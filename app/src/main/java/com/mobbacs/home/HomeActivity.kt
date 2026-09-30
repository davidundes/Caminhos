package com.mobbacs.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
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

class HomeActivity : AppCompatActivity() {

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
                Log.d("HomeActivity", "carregarLocaisDoBanco: ${locais.size} local(is) retornado(s)")

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
        descricao: String
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
                    ponto.showInfoWindow()
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
}