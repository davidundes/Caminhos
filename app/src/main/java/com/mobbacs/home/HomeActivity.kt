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

class HomeActivity: AppCompatActivity() {

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