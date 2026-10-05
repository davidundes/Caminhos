package com.mobbacs.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mobbacs.R
import com.mobbacs.database.Repository
import com.mobbacs.local.LocalActivity
import com.mobbacs.models.Local
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.mobbacs.database.SupabaseClient
import com.mobbacs.login.LoginActivity
import com.mobbacs.models.Avaliacao
import io.github.jan.supabase.auth.auth


class HomeActivity : AppCompatActivity() {

    private val avaliacaoRepository = Repository.AvaliacaoRepository()
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()
    private val localRepository = Repository.LocalRepository()
    private lateinit var map: MapView

    private companion object {
        const val ZOOM_MINIMO_POI = 17.0
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        val session = SupabaseClient.client.auth.currentSessionOrNull()
        println(session)
        if (session == null) {
            val intent = Intent(this, LoginActivity()::class.java)
            startActivity(intent)
        }

        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.home)

        val bntLocal = findViewById<Button>(R.id.btnCriar)
        map = findViewById(R.id.map)

        map.setMultiTouchControls(true)

        val jacarei = GeoPoint(-23.3053, -45.9658)
        map.controller.setCenter(jacarei)
        map.controller.setZoom(13.5)

        val limitesJacarei = BoundingBox(-23.20, -45.80, -23.40, -46.10)
        map.setScrollableAreaLimitDouble(limitesJacarei)

        map.setMinZoomLevel(13.0)
        map.setMaxZoomLevel(19.0)


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

        bntLocal.setOnClickListener {
            startActivity(Intent(this, LocalActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        carregarLocaisDoBanco()
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
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

                // Limpa os marcadores antigos para não duplicar
                map.overlays.removeAll { it is Marker }

                locais.forEach { local ->
                    val id = local.id_local ?: return@forEach

                    Log.d(
                        "HomeActivity",
                        "Adicionando marcador '${local.nome}' em (${local.latitude}, ${local.longitude})"
                    )
                    adicionarPonto(
                        map,
                        id,
                        local.latitude,
                        local.longitude,
                        local.nome,
                        local.endereco,
                        local.horario
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
    ) {
        val marcador = Marker(mapa).apply {
            position = GeoPoint(latitude, longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

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
                            ponto.subDescription = "$descricao\n$texto"
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
        if (avaliacoes.isEmpty()) return "Sem avaliações ainda"

        val media = avaliacoes.map { it.nota }.average()
        val ids = avaliacoes.mapNotNull { it.id_avaliacao }
        val acessibilidades = acessibilidadeRepository.getAcessibilidadesByAvaliacoes(ids)

        // só entram os itens que pelo menos 1 usuário marcou como true
        val itens = listOf(
            "Rampa" to acessibilidades.count { it.rampa == true },
            "Elevador" to acessibilidades.count { it.elevador == true },
            "Banheiro acessível" to acessibilidades.count { it.banheiro_acessivel == true },
            "Piso tátil" to acessibilidades.count { it.piso_tatil == true },
            "Vaga PCD" to acessibilidades.count { it.vaga_pcd == true },
            "Entrada acessível" to acessibilidades.count { it.entrada_acessivel == true },
            "Corrimão" to acessibilidades.count { it.corrimao == true },
            "Portas largas" to acessibilidades.count { it.portas_largas == true },
            "Sinalização" to acessibilidades.count { it.sinalizacao == true },
            "Iluminação" to acessibilidades.count { it.iluminacao == true }
        ).filter { it.second > 0 }

        return buildString {
            append("★ %.1f (%d avaliações)".format(media, avaliacoes.size))
            if (itens.isNotEmpty()) {
                append("\n\nAcessibilidade:")
                itens.forEach { (nome, qtd) ->
                    append("\n $nome — $qtd ${if (qtd == 1) "usuário" else "usuários"}")
                }
            }
        }
    }
}