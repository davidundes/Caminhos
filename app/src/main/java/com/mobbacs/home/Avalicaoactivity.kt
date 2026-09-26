package com.mobbacs.avaliacao

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RatingBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mobbacs.R
import com.mobbacs.database.Repository
import com.mobbacs.database.SupabaseClient
import com.mobbacs.models.Acessibilidade
import com.mobbacs.models.Avaliacao
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AvaliacaoActivity : AppCompatActivity() {

    private val avaliacaoRepository = Repository.AvaliacaoRepository()
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_avaliacao)

        val idLocal = intent.getIntExtra("id_local", -1)
        if (idLocal == -1) {
            Toast.makeText(this, "Local inválido!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val ratingNota = findViewById<RatingBar>(R.id.ratingNota)

        val checkRampa = findViewById<CheckBox>(R.id.checkRampa)
        val checkElevador = findViewById<CheckBox>(R.id.checkElevador)
        val checkBanheiro = findViewById<CheckBox>(R.id.checkBanheiro)
        val checkPisoTatil = findViewById<CheckBox>(R.id.checkPisoTatil)
        val checkVagaPcd = findViewById<CheckBox>(R.id.checkVagaPcd)
        val checkEntrada = findViewById<CheckBox>(R.id.checkEntrada)
        val checkCorrimao = findViewById<CheckBox>(R.id.checkCorrimao)
        val checkPortas = findViewById<CheckBox>(R.id.checkPortas)
        val checkSinalizacao = findViewById<CheckBox>(R.id.checkSinalizacao)
        val checkIluminacao = findViewById<CheckBox>(R.id.checkIluminacao)

        val bntEnviar = findViewById<Button>(R.id.buttonEnviarAvaliacao)

        bntEnviar.setOnClickListener {
            val nota = ratingNota.rating

            if (nota == 0f) {
                Toast.makeText(this, "Dê uma nota antes de enviar!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id
            if (idUsuario == null) {
                Toast.makeText(this, "Você precisa estar logado para avaliar!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dataAtual = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            lifecycleScope.launch {
                try {
                    val avaliacao = Avaliacao(
                        id_usuario = idUsuario,
                        id_local = idLocal,
                        nota = nota,
                        data = dataAtual
                    )

                    // Insere a avaliação e recupera o id_avaliacao gerado
                    // pelo banco, necessário para vincular a Acessibilidade.
                    val avaliacaoCriada = avaliacaoRepository.createAvaliacao(avaliacao)
                    val idAvaliacao = requireNotNull(avaliacaoCriada.id_avaliacao) {
                        "id_avaliacao não retornado pelo banco"
                    }

                    val acessibilidade = Acessibilidade(
                        id_avaliaco = idAvaliacao,
                        rampa = checkRampa.isChecked,
                        elevador = checkElevador.isChecked,
                        banheiro_acessivel = checkBanheiro.isChecked,
                        piso_tatil = checkPisoTatil.isChecked,
                        vaga_pcd = checkVagaPcd.isChecked,
                        entrada_acessivel = checkEntrada.isChecked,
                        corrimao = checkCorrimao.isChecked,
                        portas_largas = checkPortas.isChecked,
                        sinalizacao = checkSinalizacao.isChecked,
                        iluminacao = checkIluminacao.isChecked
                    )

                    acessibilidadeRepository.createAcessibilidade(acessibilidade)

                    Toast.makeText(
                        this@AvaliacaoActivity,
                        "Avaliação enviada com sucesso!",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(
                        this@AvaliacaoActivity,
                        "Erro ao enviar avaliação!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}