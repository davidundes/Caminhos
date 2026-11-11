package com.caminhos.avaliacao

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.caminhos.R
import com.caminhos.database.AvaliacaoRecurso
import com.caminhos.database.AvaliacaoRecursoRepository
import com.caminhos.database.FotoRepository
import com.caminhos.database.Repository
import com.caminhos.database.SupabaseClient
import com.caminhos.models.Acessibilidade
import com.caminhos.models.Avaliacao
import com.caminhos.util.aplicarInsetsDoSistema
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AvaliacaoActivity : AppCompatActivity(), FotosHost {

    private val avaliacaoRepository = Repository.AvaliacaoRepository()
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()
    private val recursoRepository = AvaliacaoRecursoRepository()
    private val fotoRepository = FotoRepository()

    private val viewModel: AvaliacaoViewModel by viewModels()
    private val pendente: FotoPendenteViewModel by viewModels()
    private lateinit var adapter: RecursosAdapter
    private var idLocal: Int = -1

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

    /** Reduz/corrige a imagem e abre a pré-visualização. Cada foto fica ligada à chave do recurso. */
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
            mostrarPreview(chave, preparada)
        }
    }

    private fun mostrarPreview(chave: String, uri: Uri) {
        val conteudo = LayoutInflater.from(this).inflate(R.layout.dialog_preview_foto, null)
        conteudo.findViewById<ImageView>(R.id.imgPreview).setImageURI(uri)
        val campo = conteudo.findViewById<TextInputEditText>(R.id.inputDescricaoFoto)

        MaterialAlertDialogBuilder(this)
            .setTitle("Pré-visualização")
            .setView(conteudo)
            .setCancelable(false)
            .setPositiveButton("Adicionar") { _, _ ->
                val estado = viewModel.estados[chave]
                val limite = RECURSOS.first { it.chave == chave }.limiteFotos
                if (estado == null || estado.fotos.size >= limite) {
                    ImagemUtil.excluir(uri)
                    Toast.makeText(this, "Limite de $limite fotos para este recurso.", Toast.LENGTH_SHORT).show()
                } else {
                    estado.fotos.add(FotoAnexo(uri, campo.text?.toString().orEmpty().trim()))
                    adapter.notificarRecurso(chave)
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> ImagemUtil.excluir(uri) }
            .show()
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

        adapter = RecursosAdapter(
            estados = viewModel.estados,
            host = this,
            nomeLocal = intent.getStringExtra("nome_local") ?: "Local selecionado",
            aoMudar = { adapter.atualizarTopo() }
        )

        findViewById<RecyclerView>(R.id.listaRecursos).apply {
            layoutManager = LinearLayoutManager(this@AvaliacaoActivity)
            adapter = this@AvaliacaoActivity.adapter
            // evita o "piscar" do cartão ao ser redesenhado
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        findViewById<MaterialButton>(R.id.buttonEnviarAvaliacao).setOnClickListener { botao ->
            // A nota do local agora é calculada a partir das classificações.
            val nota = viewModel.estados.notaDerivada()
            if (nota == null) {
                Snackbar.make(botao, "Avalie pelo menos um recurso antes de enviar.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            enviarAvaliacao(botao as MaterialButton, nota)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Saiu da tela sem enviar: apaga as fotos temporárias (não em rotação de tela).
        if (isFinishing && !isChangingConfigurations) {
            viewModel.estados.values.flatMap { it.fotos }.forEach { ImagemUtil.excluir(it.uri) }
        }
    }

    // --------------------------------------- Envio ---------------------------------------

    private fun enviarAvaliacao(botao: MaterialButton, nota: Float) {
        val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (idUsuario == null) {
            Toast.makeText(this, "Você precisa estar logado para avaliar!", Toast.LENGTH_SHORT).show()
            return
        }

        val dataAtual = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Para a tabela antiga (booleans): "existe" = Adequado, Parcial ou Inadequado.
        fun existe(chave: String): Boolean {
            val c = viewModel.estados[chave]?.classificacao
            return c != null && c != Classificacao.NAO_POSSUI
        }

        botao.isEnabled = false // evita envio duplicado

        lifecycleScope.launch {
            try {
                val avaliacaoCriada = avaliacaoRepository.createAvaliacao(
                    Avaliacao(id_usuario = idUsuario, id_local = idLocal, nota = nota, data = dataAtual)
                )
                val idAvaliacao = requireNotNull(avaliacaoCriada.id_avaliacao) {
                    "id_avaliacao não retornado pelo banco"
                }

                // 1) Tabela existente: mantém o mapa e as contagens funcionando.
                acessibilidadeRepository.createAcessibilidade(
                    Acessibilidade(
                        id_avaliacao = idAvaliacao,
                        rampa = existe("rampa"),
                        elevador = existe("elevador"),
                        banheiro_acessivel = existe("banheiro_acessivel"),
                        piso_tatil = existe("piso_tatil"),
                        vaga_pcd = existe("vaga_pcd"),
                        entrada_acessivel = existe("entrada_acessivel"),
                        corrimao = existe("corrimao"),
                        portas_largas = existe("portas_largas"),
                        sinalizacao = existe("sinalizacao"),
                        iluminacao = existe("iluminacao")
                    )
                )

                // 2) Nova tabela: classificação + descrição de cada recurso avaliado.
                val itens = viewModel.estados.mapNotNull { (chave, estado) ->
                    val c = estado.classificacao ?: return@mapNotNull null
                    AvaliacaoRecurso(
                        id_avaliacao = idAvaliacao,
                        recurso = chave,
                        classificacao = c.chave,
                        descricao = estado.descricao.trim().ifBlank { null }
                    )
                }
                recursoRepository.createAll(itens)

                // 3) Fotos: só saem do celular aqui, quando o usuário envia a avaliação.
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
                Toast.makeText(this@AvaliacaoActivity, "Erro ao enviar avaliação!", Toast.LENGTH_LONG).show()
                Log.e("Erro ao avaliar ", "${e.message}")
            }
        }
    }

    /** Envia as fotos de cada recurso avaliado, ligadas pelo id da avaliação e pela chave do recurso. */
    private suspend fun enviarFotos(idAvaliacao: Int, idUsuario: String): Int {
        var falhas = 0
        viewModel.estados.forEach { (chave, estado) ->
            if (!estado.avaliado) return@forEach // recurso sem avaliação: fotos ignoradas

            estado.fotos.forEachIndexed { indice, foto ->
                try {
                    val arquivo = File(requireNotNull(foto.uri.path))
                    fotoRepository.enviarFoto(idAvaliacao, idUsuario, chave, indice, arquivo, foto.descricao)
                } catch (e: Exception) {
                    falhas++
                    Log.e("AvaliacaoActivity", "Erro ao enviar foto de $chave", e)
                }
            }
        }
        return falhas
    }
}