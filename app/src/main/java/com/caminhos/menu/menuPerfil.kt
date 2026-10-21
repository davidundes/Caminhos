package com.caminhos.menu

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.database.Repository.UserRepository
import com.caminhos.database.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class menuPerfil : AppCompatActivity() {

    private lateinit var nome: TextView
    private lateinit var email: TextView
    private lateinit var cpf: TextView
    private lateinit var qtdAvaliacoes: TextView

    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.perfil)

        nome = findViewById(R.id.perfil_nome)
        email = findViewById(R.id.perfil_email)
        cpf = findViewById(R.id.perfil_cpf)
        qtdAvaliacoes = findViewById(R.id.perfil_qtdAvalicoes)

        carregarPerfil()
    }

    private fun carregarPerfil() {
        lifecycleScope.launch {
            try {
                val authUser = SupabaseClient.client.auth.currentUserOrNull()
                if (authUser == null) {
                    Toast.makeText(this@menuPerfil, "Sessão não encontrada", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                email.text = authUser.email ?: "—"

                val usuario = userRepository.getUser(authUser.id)
                if (usuario == null) {
                    Toast.makeText(this@menuPerfil, "Usuário não encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                nome.text = usuario.nome ?: "Sem nome"
                cpf.text = formatarCpf(usuario.cpf)

                val total = userRepository.getQtdAvaliacoes(authUser.id)
                qtdAvaliacoes.text = if (total == 1) "1 avaliação" else "$total avaliações"

            } catch (e: Exception) {
                Log.e("menuPerfil", "Erro ao carregar perfil", e)
                Toast.makeText(this@menuPerfil, "Erro ao carregar perfil", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 12345678900 -> 123.456.789-00
    private fun formatarCpf(valor: String?): String {
        if (valor.isNullOrBlank()) return "—"
        val d = valor.filter { it.isDigit() }
        if (d.length != 11) return valor
        return "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}"
    }
}