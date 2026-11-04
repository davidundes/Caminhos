package com.caminhos.avaliacao

import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.database.Repository
import com.caminhos.database.SupabaseClient
import com.caminhos.models.Acessibilidade
import com.caminhos.models.Avaliacao
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AvaliacaoActivity : AppCompatActivity() {

    private val avaliacaoRepository = Repository.AvaliacaoRepository()
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()

    private val viewModel: AvaliacaoViewModel by viewModels()
    private val cards = mutableListOf<RecursoCard>()
    private var idLocal: Int = -1

    private lateinit var progresso: LinearProgressIndicator
    private lateinit var textProgresso: TextView

    // total = recursos + nota geral
    private val totalItens = RECURSOS.size + 1

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        idLocal = intent.getIntExtra("id_local", -1)
        if (idLocal == -1) {
            Toast.makeText(this, "Local inválido!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_avaliacao)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootAvaliacao)) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        findViewById<TextView>(R.id.textNomeLocal).text =
            "📍 " + (intent.getStringExtra("nome_local") ?: "Local selecionado")

        ViewCompat.setAccessibilityHeading(findViewById(R.id.textTituloAvaliacao), true)

        progresso = findViewById(R.id.progressAvaliacao)
        textProgresso = findViewById(R.id.textProgresso)
        progresso.max = totalItens

        configurarNotaGeral()
        criarCartoes()
        atualizarProgresso()

        findViewById<MaterialButton>(R.id.buttonEnviarAvaliacao).setOnClickListener { botao ->
            // A nota geral continua obrigatória (a tabela de avaliação exige a nota).
            if (viewModel.notaGeral == 0) {
                Snackbar.make(botao, "Dê uma nota geral antes de enviar!", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            enviarAvaliacao(botao as MaterialButton)
        }
    }

    private fun configurarNotaGeral() {
        val rating = findViewById<RatingBar>(R.id.ratingNota)
        val legenda = findViewById<TextView>(R.id.textLegendaNota)

        fun atualizarLegenda() {
            legenda.text = if (viewModel.notaGeral > 0) {
                "${viewModel.notaGeral} · ${rotuloNota(viewModel.notaGeral)}"
            } else {
                "Toque nas estrelas para avaliar"
            }
        }

        rating.rating = viewModel.notaGeral.toFloat()
        atualizarLegenda()

        rating.setOnRatingBarChangeListener { _, nota, doUsuario ->
            if (!doUsuario) return@setOnRatingBarChangeListener
            viewModel.notaGeral = nota.toInt()
            atualizarLegenda()
            atualizarProgresso()
        }
    }

    private fun criarCartoes() {
        val container = findViewById<LinearLayout>(R.id.containerRecursos)
        RECURSOS.forEach { def ->
            val view = layoutInflater.inflate(R.layout.item_recurso, container, false)
            // Os cartões compartilham os mesmos IDs internos; o estado é restaurado pelo ViewModel.
            view.isSaveFromParentEnabled = false
            cards += RecursoCard(view, def, viewModel.estados.getValue(def.chave)) {
                atualizarProgresso()
            }
            container.addView(view)
        }
    }

    private fun atualizarProgresso() {
        val feitos = viewModel.estados.values.count { it.avaliado } +
                if (viewModel.notaGeral > 0) 1 else 0
        progresso.setProgressCompat(feitos, true)
        textProgresso.text = "$feitos de $totalItens itens preenchidos"
    }

    private fun enviarAvaliacao(botao: MaterialButton) {
        val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (idUsuario == null) {
            Toast.makeText(this, "Você precisa estar logado para avaliar!", Toast.LENGTH_SHORT).show()
            return
        }

        val dataAtual = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // SIM = true. "Não" e "Não sei" = false (mesmo comportamento dos antigos checkboxes).
        val existe = viewModel.estados.mapValues { it.value.resposta == Resposta.SIM }

        botao.isEnabled = false // evita envio duplicado

        lifecycleScope.launch {
            try {
                val avaliacao = Avaliacao(
                    id_usuario = idUsuario,
                    id_local = idLocal,
                    nota = viewModel.notaGeral.toFloat(),
                    data = dataAtual
                )

                val avaliacaoCriada = avaliacaoRepository.createAvaliacao(avaliacao)
                val idAvaliacao = requireNotNull(avaliacaoCriada.id_avaliacao) {
                    "id_avaliacao não retornado pelo banco"
                }

                val acessibilidade = Acessibilidade(
                    id_avaliacao = idAvaliacao,
                    rampa = existe["rampa"] == true,
                    elevador = existe["elevador"] == true,
                    banheiro_acessivel = existe["banheiro_acessivel"] == true,
                    piso_tatil = existe["piso_tatil"] == true,
                    vaga_pcd = existe["vaga_pcd"] == true,
                    entrada_acessivel = existe["entrada_acessivel"] == true,
                    corrimao = existe["corrimao"] == true,
                    portas_largas = existe["portas_largas"] == true,
                    sinalizacao = existe["sinalizacao"] == true,
                    iluminacao = existe["iluminacao"] == true
                )

                acessibilidadeRepository.createAcessibilidade(acessibilidade)

                Toast.makeText(
                    this@AvaliacaoActivity,
                    "Avaliação enviada com sucesso!",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (e: Exception) {
                botao.isEnabled = true
                Toast.makeText(
                    this@AvaliacaoActivity,
                    "Erro ao enviar avaliação!",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("Erro ao avaliar ", "${e.message}")
            }
        }
    }
}