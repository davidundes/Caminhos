package com.caminhos.menu

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.local.LocalActivity
import com.caminhos.usuario.SessaoUsuario
import kotlinx.coroutines.launch

class menuAdm: AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.adm)

        val bntLocal = findViewById<Button>(R.id.adm_CriarLocal)
        bntLocal.setOnClickListener {
            startActivity(Intent(this, LocalActivity::class.java))
        }
    }
}