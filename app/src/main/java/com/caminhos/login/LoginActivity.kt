package com.caminhos.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.database.SupabaseClient
import com.caminhos.register.RegisterActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.providers.builtin.Email
import com.caminhos.home.HomeActivity

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
        val bntLinkR = findViewById<Button>(R.id.buttonLinkRegister)

        bntLinkR.setOnClickListener {
            val intent = Intent(this, RegisterActivity()::class.java)
            startActivity(intent)
        }

        bntLogin.setOnClickListener {
            val emailUsuario = email.text.toString()
            val senhaUsuario = senha.text.toString()

            lifecycleScope.launch() {
                try {
                    login(emailUsuario, senhaUsuario)
                    Toast.makeText(
                        this@LoginActivity, "Login efetuado com sucesso!",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this@LoginActivity, HomeActivity()::class.java))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Senha ou/e Email inválidos!",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("SUPABASE_TESTE", "${e.message}")
                }
            }
        }
    }
}