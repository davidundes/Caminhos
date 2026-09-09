package com.mobbacs.home

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.mobbacs.R
import org.osmdroid.config.Configuration
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