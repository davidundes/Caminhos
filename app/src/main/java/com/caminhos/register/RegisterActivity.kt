package com.caminhos.register

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.models.User
import com.caminhos.database.SupabaseClient
import io.github.jan.supabase.auth.auth
import com.caminhos.database.Repository
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.providers.builtin.Email


class RegisterActivity: AppCompatActivity() {
    suspend fun cadastrar(email: String, senha: String) {
        SupabaseClient.client.auth.signUpWith(Email) {
            this.email = email
            this.password = senha
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.register_conta)

        val userRepository = Repository.UserRepository()

        val nome = findViewById<EditText>(R.id.editNome)
        val email = findViewById<EditText>(R.id.editEmail)
        val senha = findViewById<EditText>(R.id.editSenha)
        val cpf = findViewById<EditText>(R.id.editCPF)
        val bntRegister = findViewById<Button>(R.id.buttonRegister)
        val bntLinkL = findViewById<Button>(R.id.buttonLinkLogin)

        bntLinkL.setOnClickListener {
            finish()
        }

        bntRegister.setOnClickListener {
            val nomeUsuario = nome.text.toString()
            val emailUsuario = email.text.toString()
            val senhaUsuario = senha.text.toString()
            val cpfUsuario = cpf.text.toString()

            if(nomeUsuario.isEmpty() || emailUsuario.isEmpty() || senhaUsuario.isEmpty() || cpfUsuario.isEmpty()){
                Toast.makeText(this, "Digite um valor válido!", Toast.LENGTH_SHORT).show()
            } else{
                lifecycleScope.launch() {
                    try {
                        cadastrar(emailUsuario, senhaUsuario)
                        val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id
                        val usuario = User(
                            cpf = cpfUsuario,
                            email = emailUsuario,
                            nome = nomeUsuario,
                            id_usuario = idUsuario
                        )
                        userRepository.createUser(usuario)
                        Toast.makeText(
                            this@RegisterActivity,
                            "Usuário cadastrado com sucesso!",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception){
                        Toast.makeText(
                            this@RegisterActivity,
                            "Usuário cadastrado com fracasso!",
                            Toast.LENGTH_LONG
                        ).show()
                         Log.e("SUPABASE_TESTE", "${e.message}")
                    }
                }
            }
        }
    }
}