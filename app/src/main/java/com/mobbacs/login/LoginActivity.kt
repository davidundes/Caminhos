package com.mobbacs.login

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mobbacs.R
import com.mobbacs.database.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.providers.builtin.Email

class LoginActivity: AppCompatActivity() {

    suspend fun login(email: String, senha: String){
        SupabaseClient.client.auth.signInWith(Email){
            this.email = email
            this.password = senha
        }
    }


    public override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.login_conta)

        val email = findViewById<EditText>(R.id.loginEmail)
        val senha = findViewById<EditText>(R.id.loginSenha)
        val bntLogin = findViewById<Button>(R.id.buttonLogin)

        bntLogin.setOnClickListener {
            val emailUsuario = email.text.toString()
            val senhaUsuario = senha.text.toString()

            lifecycleScope.launch() {
                try{
                    login(emailUsuario, senhaUsuario)

                    Toast.makeText(this@LoginActivity ,"Logou", Toast.LENGTH_LONG).show()
                }catch (e: Exception){
                    Toast.makeText(this@LoginActivity, "Não logou", Toast.LENGTH_LONG).show()
                    Log.e("SUPABASE_TESTE", "${e.message}")
                }
            }

        }

    }


}