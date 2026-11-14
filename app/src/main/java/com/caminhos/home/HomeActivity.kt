package com.caminhos.home

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.database.Repository
import com.caminhos.database.Repository.UserRepository
import com.caminhos.database.SupabaseClient
import com.caminhos.local.localInfoWindow
import com.caminhos.login.LoginActivity
import com.caminhos.menu.menuActivity
import com.caminhos.models.Local
import com.caminhos.ui.Aba
import com.caminhos.ui.HotbarView
import com.caminhos.usuario.CarregarSessao
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()
    private val localRepository = Repository.LocalRepository()
    private lateinit var map: MapView

    private companion object {
        const val ZOOM_MINIMO_POI = 17.0
    }

    /**
     * Cada recurso agora é texto: "Adequado", "Parcialmente adequado", "Inadequado",
     * "Não possui" ou null (não avaliado). O recurso "existe" no local quando é
     * adequado ou parcialmente adequado.
     */
    private fun String?.ofereceRecurso(): Boolean =
        this == "Adequado" || this == "Parcialmente adequado"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        val session = SupabaseClient.client.auth.currentSessionOrNull()
        if (session == null) {
            val intent = Intent(this, LoginActivity()::class.java)
            finish()
            startActivity(intent)
        }

        setContentView(R.layout.home)

        val bntMenu = findViewById<ImageButton>(R.id.btnMenu)
        val userRepository = UserRepository()
        val idUsuarioLogado = SupabaseClient.client.auth.currentUserOrNull()?.id

        lifecycleScope.launch {
            val id = idUsuarioLogado ?: return@launch
            val usuario = userRepository.getUser(id) ?: return@launch
            val nome = usuario.nome ?: return@launch

            CarregarSessao(
                id,
                nome,
                userRepository.userIsAdmin(id)
            )
        }

        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.map)

        map.setMultiTouchControls(true)

        val jacarei = GeoPoint(-23.3053, -45.9658)
        map.controller.setCenter(jacarei)
        map.controller.setZoom(13.5)

        val limitesJacarei = BoundingBox(-23.20, -45.80, -23.40, -46.10)
        map.setScrollableAreaLimitDouble(limitesJacarei)

        map.setMinZoomLevel(13.0)
        map.setMaxZoomLevel(19.0)

        bntMenu.setOnClickListener {
            startActivity(Intent(this, menuActivity::class.java))
        }
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                atualizarVisibilidadePOIs()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                atualizarVisibilidadePOIs()
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        findViewById<HotbarView>(R.id.hotbar).sincronizar(Aba.HOME)
        carregarLocaisDoBanco()
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

    private fun corPorMedia(media: Double?): Int = when {
        media == null -> Color.parseColor("#9AA7B8") // sem avaliações: cinza
        media <= 2.0 -> Color.parseColor("#E53935")  // 2 para baixo: vermelho
        media < 3.5 -> Color.parseColor("#FBC02D")   // entre 2 e 3,5: amarelo
        else -> Color.parseColor("#43A047")          // 3,5 para cima: verde
    }

    private fun carregarLocaisDoBanco() {
        Log.d("HomeActivity", "carregarLocaisDoBanco: iniciando busca no Supabase")
        lifecycleScope.launch {
            try {
                val locais: List<Local> = localRepository.getAllLocais()
                Log.d(
                    "HomeActivity",
                    "carregarLocaisDoBanco: ${locais.size} local(is) retornado(s)"
                )

                map.overlays.removeAll { it is Marker }

                locais.forEach { local ->
                    val id = local.id_local ?: return@forEach

                    Log.d(
                        "HomeActivity",
                        "Adicionando marcador '${local.nome}' em (${local.latitude}, ${local.longitude})"
                    )

                    // nota agora é Float? (calculada pela trigger): ignora avaliações sem nota.
                    val media: Double? = try {
                        acessibilidadeRepository.getAvaliacoesByLocal(id)
                            .mapNotNull { it.nota }
                            .takeIf { it.isNotEmpty() }
                            ?.average()
                    } catch (e: Exception) {
                        null
                    }

                    adicionarPonto(
                        map,
                        id,
                        local.latitude,
                        local.longitude,
                        local.nome,
                        local.endereco,
                        local.horario,
                        media
                    )
                }
                map.invalidate()
            } catch (e: Exception) {
                Log.e("HomeActivity", "Erro ao buscar locais do Supabase", e)
            }
        }
    }

    private fun adicionarPonto(
        mapa: MapView,
        idLocal: Int,
        latitude: Double,
        longitude: Double,
        titulo: String,
        subtitulo: String,
        descricao: String,
        media: Double?
    ) {
        val marcador = Marker(mapa).apply {
            position = GeoPoint(latitude, longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_marker)
                ?.mutate()
                ?.apply { setTint(corPorMedia(media)) }

            title = titulo
            snippet = subtitulo
            subDescription = descricao
            relatedObject = idLocal

            infoWindow = localInfoWindow(mapa)

            setPanToView(false)

            setOnMarkerClickListener { ponto, _ ->
                if (ponto.isInfoWindowShown) {
                    ponto.closeInfoWindow()
                } else {
                    lifecycleScope.launch {
                        try {
                            val texto = montarTextoAvaliacoes(idLocal)
                            ponto.subDescription = "🕒 $descricao\n\n$texto"
                        } catch (e: Exception) {
                            Log.e("HomeActivity", "Erro ao buscar avaliações", e)
                        }
                        ponto.showInfoWindow()
                    }
                }
                true
            }

            isEnabled = mapa.zoomLevelDouble >= ZOOM_MINIMO_POI
        }
        mapa.overlays.add(marcador)
    }

    private fun atualizarVisibilidadePOIs() {
        val visivel = map.zoomLevelDouble >= ZOOM_MINIMO_POI
        map.overlays.filterIsInstance<Marker>().forEach { it.isEnabled = visivel }
        map.invalidate()
    }

    private suspend fun montarTextoAvaliacoes(idLocal: Int): String {
        val avaliacoes = acessibilidadeRepository.getAvaliacoesByLocal(idLocal)
        if (avaliacoes.isEmpty()) return "☆ Sem avaliações ainda"

        val media: Double? = avaliacoes
            .mapNotNull { it.nota }
            .takeIf { it.isNotEmpty() }
            ?.average()

        val ids = avaliacoes.mapNotNull { it.id_avaliacao }
        val acessibilidades = acessibilidadeRepository.getAcessibilidadesByAvaliacoes(ids)

        val itens = listOf(
            "Rampa" to acessibilidades.count { it.rampa.ofereceRecurso() },
            "Elevador" to acessibilidades.count { it.elevador.ofereceRecurso() },
            "Banheiro acessível" to acessibilidades.count { it.banheiro_acessivel.ofereceRecurso() },
            "Piso tátil" to acessibilidades.count { it.piso_tatil.ofereceRecurso() },
            "Vaga PCD" to acessibilidades.count { it.vaga_pcd.ofereceRecurso() },
            "Entrada acessível" to acessibilidades.count { it.entrada_acessivel.ofereceRecurso() },
            "Corrimão" to acessibilidades.count { it.corrimao.ofereceRecurso() },
            "Portas largas" to acessibilidades.count { it.portas_largas.ofereceRecurso() },
            "Sinalização" to acessibilidades.count { it.sinalizacao.ofereceRecurso() },
            "Iluminação" to acessibilidades.count { it.iluminacao.ofereceRecurso() }
        ).filter { it.second > 0 }
            .sortedByDescending { it.second }

        val total = avaliacoes.size
        val palavraAvaliacao = if (total == 1) "avaliação" else "avaliações"

        return buildString {
            if (media != null) {
                append("★ %.1f · %d %s".format(Locale("pt", "BR"), media, total, palavraAvaliacao))
            } else {
                append("☆ %d %s (sem nota ainda)".format(Locale("pt", "BR"), total, palavraAvaliacao))
            }
            if (itens.isNotEmpty()) {
                append("\n\nAcessibilidade")
                itens.forEach { (nome, qtd) ->
                    val palavraUsuario = if (qtd == 1) "usuário" else "usuários"
                    append("\n✔ $nome · $qtd $palavraUsuario")
                }
            }
        }
    }
}