package com.caminhos.register

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
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

        // CPF: máscara 000.000.000-00 enquanto digita (aceita no máximo 14 caracteres)
        cpf.filters = arrayOf(InputFilter.LengthFilter(14))
        aplicarMascaraCpf(cpf)

        bntLinkL.setOnClickListener {
            finish()
        }

        bntRegister.setOnClickListener {
            val nomeUsuario = nome.text.toString().trim()
            val emailUsuario = email.text.toString().trim()
            val senhaUsuario = senha.text.toString()
            val cpfDigitos = cpf.text.toString().filter { it.isDigit() }

            if (nomeUsuario.isEmpty() || emailUsuario.isEmpty() || senhaUsuario.isEmpty() || cpfDigitos.isEmpty()) {
                Toast.makeText(this, "Digite um valor válido!", Toast.LENGTH_SHORT).show()
            } else if (!cpfValido(cpfDigitos)) {
                // Valida ANTES de criar a conta, para não deixar usuário no Auth com CPF inválido
                cpf.error = "CPF inválido"
                cpf.requestFocus()
            } else {
                bntRegister.isEnabled = false
                lifecycleScope.launch {
                    try {
                        cadastrar(emailUsuario, senhaUsuario)
                        val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id
                        val usuario = User(
                            cpf = cpfDigitos, // só os 11 números, sem pontos e traço
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
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Não foi possível cadastrar o usuário.",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e("SUPABASE_TESTE", "${e.message}")
                        bntRegister.isEnabled = true
                    }
                }
            }
        }
    }

    /**
     * Regra do CPF: 11 dígitos, não pode ser todos iguais (111.111.111-11 etc.)
     * e os dois últimos dígitos precisam bater com o cálculo dos dígitos verificadores.
     */
    private fun cpfValido(cpf: String): Boolean {
        if (cpf.length != 11 || !cpf.all { it.isDigit() }) return false
        if (cpf.all { it == cpf[0] }) return false

        fun digitoVerificador(base: String): Int {
            val pesoInicial = base.length + 1 // 10 para os 9 primeiros, 11 para os 10 primeiros
            val soma = base.mapIndexed { i, c -> (c - '0') * (pesoInicial - i) }.sum()
            val resto = (soma * 10) % 11
            return if (resto == 10) 0 else resto
        }

        return digitoVerificador(cpf.take(9)) == (cpf[9] - '0') &&
                digitoVerificador(cpf.take(10)) == (cpf[10] - '0')
    }

    private fun formatarCpf(digitos: String): String {
        val d = digitos.take(11)
        return when {
            d.length <= 3 -> d
            d.length <= 6 -> "${d.take(3)}.${d.drop(3)}"
            d.length <= 9 -> "${d.take(3)}.${d.substring(3, 6)}.${d.drop(6)}"
            else -> "${d.take(3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.drop(9)}"
        }
    }

    private fun aplicarMascaraCpf(campo: EditText) {
        campo.addTextChangedListener(object : TextWatcher {
            private var ajustando = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (ajustando) return
                ajustando = true
                val atual = s?.toString().orEmpty()
                val novo = formatarCpf(atual.filter { it.isDigit() })
                if (novo != atual) {
                    campo.setText(novo)
                    campo.setSelection(novo.length)
                }
                ajustando = false
            }
        })
    }
}