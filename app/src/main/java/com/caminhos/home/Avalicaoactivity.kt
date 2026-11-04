package com.caminhos.avaliacao

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.database.FotoRepository
import com.caminhos.database.Repository
import com.caminhos.database.SupabaseClient
import com.caminhos.models.Acessibilidade
import com.caminhos.models.Avaliacao
import com.caminhos.util.aplicarInsetsDoSistema
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AvaliacaoActivity : AppCompatActivity(), FotosHost {

    private val avaliacaoRepository = Repository.AvaliacaoRepository()
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()
    private val fotoRepository = FotoRepository()

    private val viewModel: AvaliacaoViewModel by viewModels()
    private val pendente: FotoPendenteViewModel by viewModels()
    private val cartoes = mutableMapOf<String, RecursoCard>()
    private var idLocal: Int = -1

    private lateinit var progresso: LinearProgressIndicator
    private lateinit var textProgresso: TextView

    // total = recursos + nota geral
    private val totalItens = RECURSOS.size + 1

    // ---------- Activity Result APIs (não exigem permissão de câmera/galeria) ----------

    private val capturaFoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
            val chave = pendente.chave
            val arquivo = pendente.arquivoCamera
            if (sucesso && chave != null && arquivo != null) {
                processarFoto(chave, Uri.fromFile(arquivo))
            } else {
                arquivo?.delete()
                pendente.arquivoCamera = null
            }
        }

    private val seletorGaleria =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val chave = pendente.chave
            if (uri != null && chave != null) processarFoto(chave, uri)
        }

    override fun tirarFoto(chave: String) {
        val arquivo = ImagemUtil.criarArquivoCamera(this)
        pendente.chave = chave
        pendente.arquivoCamera = arquivo
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivo)
            capturaFoto.launch(uri)
        } catch (e: Exception) {
            Log.e("AvaliacaoActivity", "Erro ao abrir a câmera", e)
            Toast.makeText(this, "Não foi possível abrir a câmera.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun escolherDaGaleria(chave: String) {
        pendente.chave = chave
        seletorGaleria.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** Reduz/corrige a imagem e abre a pré-visualização no cartão do recurso certo. */
    private fun processarFoto(chave: String, origem: Uri) {
        lifecycleScope.launch {
            val preparada = ImagemUtil.prepararImagem(this@AvaliacaoActivity, origem)
            pendente.arquivoCamera?.delete()
            pendente.arquivoCamera = null

            if (preparada == null) {
                Toast.makeText(
                    this@AvaliacaoActivity, "Não foi possível carregar a imagem.", Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            cartoes[chave]?.fotos?.mostrarPreview(preparada)
        }
    }

    // ---------------------------------- Ciclo de vida ----------------------------------

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

        findViewById<View>(R.id.rootAvaliacao).aplicarInsetsDoSistema()

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

    override fun onDestroy() {
        super.onDestroy()
        // Saiu da tela sem enviar: apaga as fotos temporárias (não em rotação de tela).
        if (isFinishing && !isChangingConfigurations) {
            viewModel.estados.values.flatMap { it.fotos }.forEach { ImagemUtil.excluir(it.uri) }
        }
    }

    // ------------------------------------- Interface -------------------------------------

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
            cartoes[def.chave] = RecursoCard(
                view, def, viewModel.estados.getValue(def.chave), this
            ) { atualizarProgresso() }
            container.addView(view)
        }
    }

    private fun atualizarProgresso() {
        val feitos = viewModel.estados.values.count { it.avaliado } +
                if (viewModel.notaGeral > 0) 1 else 0
        progresso.setProgressCompat(feitos, true)
        textProgresso.text = "$feitos de $totalItens itens preenchidos"
    }

    // --------------------------------------- Envio ---------------------------------------

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

                // As fotos só saem do celular aqui, quando o usuário envia a avaliação.
                val falhas = enviarFotos(idAvaliacao, idUsuario)

                Toast.makeText(
                    this@AvaliacaoActivity,
                    if (falhas == 0) "Avaliação enviada com sucesso!"
                    else "Avaliação enviada, mas $falhas foto(s) não puderam ser enviadas.",
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

    /** Envia as fotos de cada recurso, ligadas pelo id da avaliação e pela chave do recurso. */
    private suspend fun enviarFotos(idAvaliacao: Int, idUsuario: String): Int {
        var falhas = 0
        viewModel.estados.forEach { (chave, estado) ->
            // Fotos só valem para recursos marcados como "Sim" (a seção fica nesse bloco).
            if (estado.resposta != Resposta.SIM) return@forEach

            estado.fotos.forEachIndexed { indice, foto ->
                try {
                    val arquivo = File(requireNotNull(foto.uri.path))
                    fotoRepository.enviarFoto(
                        idAvaliacao, idUsuario, chave, indice, arquivo, foto.descricao
                    )
                } catch (e: Exception) {
                    falhas++
                    Log.e("AvaliacaoActivity", "Erro ao enviar foto de $chave", e)
                }
            }
        }
        return falhas
    }
}