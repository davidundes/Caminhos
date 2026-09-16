package com.mobbacs.home

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.mobbacs.R
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
class HomeActivity: AppCompatActivity() {

    private fun adicionarPonto(mapa: MapView, latitude: Double, longitude: Double){
        val marcador = Marker(mapa).apply {
            position = GeoPoint(latitude, longitude)
            setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )

            title = "IFSP JCR"
            snippet = "IFSP CAMPUS DE JACAREI"

            subDescription = "Rua Antonio fogaça de almeida, 200 - jardim america"

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

        val jacarei  = GeoPoint(-23.3053,-54.9658)

        map.controller.setCenter(jacarei)
        map.controller.setZoom(13.5)

        val limitesJacarei = BoundingBox(-23.20, -45.80, -23.40, -46.10)

        map.setScrollableAreaLimitDouble(limitesJacarei)

        map.setMinZoomLevel(13.0)
        map.setMaxZoomLevel(19.0)

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        adicionarPonto(map ,-23.3172, -45.9841)

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