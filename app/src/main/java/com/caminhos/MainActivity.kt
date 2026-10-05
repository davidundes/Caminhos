package com.caminhos

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.caminhos.home.HomeActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = Intent(this, HomeActivity()::class.java)
        startActivity(intent)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        finish()
    }

}