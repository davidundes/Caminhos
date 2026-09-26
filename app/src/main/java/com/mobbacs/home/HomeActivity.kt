package com.mobbacs.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mobbacs.R
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import com.mobbacs.database.Repository
import com.mobbacs.models.Local

class HomeActivity: AppCompatActivity() {
    private val localRepository = Repository.LocalRepository()

    private fun adicionarPonto(
        mapa: MapView,
        idLocal: Int,
        latitude: Double,
        longitude: Double,
        titulo: String,
        subtitulo: String,
        descricao: String)
    {
        val marcador = Marker(mapa).apply {
            position = GeoPoint(latitude, longitude)
            setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )

            title = titulo
            snippet = subtitulo
            subDescription = descricao

            // guarda o id do local no marcador para recuperar
            // na hora de abrir a tela de avaliação
            relatedObject = idLocal

            infoWindow = localInfoWindow(mapa)

            setPanToView(false)

            setOnMarkerClickListener { ponto, mapView ->
                if (ponto.isInfoWindowShown) {
                    ponto.closeInfoWindow()
                }else {
                    ponto.showInfoWindow()
                }
                true
            }
        }
        mapa.overlays.add(marcador)
        mapa.invalidate()

        mapa.addMapListener(object: MapListener{
            override fun onScroll(event: ScrollEvent?): Boolean {
                atualizarVisibilidadePOI(mapa, marcador)
                return true
            }
            override fun onZoom(event: ZoomEvent?): Boolean {
                atualizarVisibilidadePOI(mapa, marcador)
                return true
            }
        })
        atualizarVisibilidadePOI(mapa, marcador)
    }

    private fun atualizarVisibilidadePOI(
        mapa: MapView,
        marcador: Marker
    ) {
        marcador.isEnabled = mapa.zoomLevel >= 17.0
        mapa.invalidate()
    }
    private fun carregarLocaisDoBanco() {
        Log.d("HomeActivity", "carregarLocaisDoBanco: iniciando busca no Supabase")
        lifecycleScope.launch {
            try {
                val locais: List<Local> = localRepository.getAllLocais()
                Log.d("HomeActivity", "carregarLocaisDoBanco: ${locais.size} local(is) retornado(s)")

                locais.forEach { local ->
                    Log.d(
                        "HomeActivity",
                        "Adicionando marcador '${local.nome}' em (${local.latitude}, ${local.longitude})"
                    )
                    adicionarPonto(
                        map,
                        local.id_local,
                        local.latitude,
                        local.longitude,
                        local.nome,
                        local.endereco,
                        local.horario
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Erro ao buscar locais do Supabase", e)
            }
        }
    }

    private lateinit var map: MapView
    @SuppressLint("MissingInflatedId")
    public override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        setContentView(R.layout.home)

        map = findViewById(R.id.map)

        val jacarei  = GeoPoint(-23.3053,-45.9658)

        map.controller.setCenter(jacarei)
        map.controller.setZoom(13.5)

        val limitesJacarei = BoundingBox(-23.20, -45.80, -23.40, -46.10)

        map.setScrollableAreaLimitDouble(limitesJacarei)

        map.setMinZoomLevel(13.0)
        map.setMaxZoomLevel(19.0)

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        carregarLocaisDoBanco()

    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

}