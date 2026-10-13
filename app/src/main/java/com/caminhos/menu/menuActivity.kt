package com.caminhos.menu

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.caminhos.R
import com.caminhos.usuario.SessaoUsuario
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.caminhos.database.SupabaseClient
import com.caminhos.login.LoginActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import com.caminhos.usuario.ApagarSessao
class menuActivity: AppCompatActivity() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.menu)

        val adm = SessaoUsuario.isAdmin

        val bnt_perfil = findViewById<Button>(R.id.bnt_perfil)
        val bnt_avaliacoes = findViewById<Button>(R.id.bnt_minAvaliacoes)
        val bnt_adm = findViewById<Button>(R.id.bnt_adm)
        bnt_adm.visibility = if(adm) View.VISIBLE else View.GONE
        val bnt_deslogar = findViewById<Button>(R.id.bnt_deslogar)
        Log.d("MENU", "isAdmin=${SessaoUsuario.isAdmin}")


        bnt_perfil.setOnClickListener {
            startActivity(Intent(this, menuPerfil::class.java))
        }

        bnt_avaliacoes.setOnClickListener {  }

        bnt_adm.setOnClickListener {
            startActivity(Intent(this, menuAdm::class.java))
        }

        bnt_deslogar.setOnClickListener {
            lifecycleScope.launch {
                try {
                    SupabaseClient.client.auth.signOut()
                } catch (e: Exception) {
                } finally {
                    ApagarSessao()
                    startActivity(
                        Intent(this@menuActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
            }
        }


    }
}