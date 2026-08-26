package com.mobbacs

import com.mobbacs.login.LoginActivity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val intent = Intent(this, LoginActivity()::class.java)
        startActivity(intent)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
}