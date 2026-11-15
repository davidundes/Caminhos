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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.caminhos.R
import com.caminhos.database.FotoRepository
import com.caminhos.database.Repository
import com.caminhos.database.SupabaseClient
import com.caminhos.avalicao.NovaAvaliacao
import com.caminhos.util.aplicarInsetsDoSistema
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** Guarda se a tela está editando uma avaliação existente (sobrevive à rotação de tela). */
class EdicaoAvaliacaoViewModel : ViewModel() {
    var carregado: Boolean = false
    var idAvaliacao: Int? = null
}

class AvaliacaoActivity : AppCompatActivity(), FotosHost {

    private companion object {
        const val TAG = "AvaliacaoActivity"
        const val NAO_POSSUI = "Não possui"

        /** Todas as colunas de recurso de tb_acessibilidade, com o nome exato do banco. */
        val COLUNAS = listOf(
            "rampa", "elevador", "banheiro_acessivel", "piso_tatil", "vaga_pcd",
            "entrada_acessivel", "corrimao", "portas_largas", "sinalizacao", "iluminacao",
            "banheiro_adaptado", "atendimento_prioritario", "interprete_libras",
            "cardapio_acessivel", "informacao_em_braille", "piso_antiderrapante",
            "mapa_tatil", "sinalizacao_visual", "sinalizacao_tatil", "sinalizacao_sonora",
            "comunicacao_acessivel", "mobiliario_adaptado", "balcao_acessivel", "acesso_cao_guia"
        )

        /** Coluna do banco -> chave usada hoje em RECURSOS, quando os nomes divergem. */
        val CHAVE_ALTERNATIVA = mapOf(
            "acesso_cao_guia" to "acesso_guia",
            "informacao_em_braille" to "informacoes_braille"
        )
    }

    private val avaliacaoRepository = Repository.AvaliacaoRepository()
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()
    private val imageRepository = Repository.ImageRepository()
    private val fotoRepository = FotoRepository()

    private val viewModel: AvaliacaoViewModel by viewModels()
    private val pendente: FotoPendenteViewModel by viewModels()
    private val edicao: EdicaoAvaliacaoViewModel by viewModels()

    private lateinit var adapter: RecursosAdapter
    private var idLocal: Int = -1

    // ------------------------------- Fotos (Activity Result APIs) -------------------------------

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
            Log.e(TAG, "Erro ao abrir a câmera", e)
            toast("Não foi possível abrir a câmera.")
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
                toast("Não foi possível carregar a imagem.")
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
                val limite = RECURSOS.firstOrNull { it.chave == chave }?.limiteFotos ?: 0
                if (estado == null || estado.fotos.size >= limite) {
                    ImagemUtil.excluir(uri)
                    toast("Limite de $limite fotos para este recurso.")
                } else {
                    estado.fotos.add(FotoAnexo(uri, campo.text?.toString().orEmpty().trim()))
                    adapter.notificarRecurso(chave)
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> ImagemUtil.excluir(uri) }
            .show()
    }

    // ----------------------------------- Ciclo de vida -----------------------------------

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        idLocal = intent.getIntExtra("id_local", -1)
        if (idLocal == -1) {
            toast("Local inválido!")
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

        val botaoEnviar = findViewById<MaterialButton>(R.id.buttonEnviarAvaliacao)
        botaoEnviar.setOnClickListener { botao ->
            // A nota NÃO é calculada aqui: a trigger do banco faz isso ao gravar tb_acessibilidade.
            val editando = edicao.idAvaliacao != null
            if (!editando && viewModel.estados.values.none { it.avaliado }) {
                Snackbar.make(botao, "Avalie pelo menos um recurso antes de enviar.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            enviarAvaliacao(botao as MaterialButton)
        }

        if (edicao.idAvaliacao != null) {
            // Rotação de tela: o modo de edição já foi carregado antes.
            botaoEnviar.text = TEXTO_BOTAO_EDICAO
        } else if (!edicao.carregado) {
            carregarAvaliacaoExistente(botaoEnviar)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Saiu da tela sem enviar: apaga as fotos temporárias (não em rotação de tela).
        if (isFinishing && !isChangingConfigurations) {
            viewModel.estados.values.flatMap { it.fotos }.forEach { ImagemUtil.excluir(it.uri) }
        }
    }

    // ------------------------------------ Modo de edição ------------------------------------

    private val TEXTO_BOTAO_EDICAO = "Salvar alterações"

    /** Se o usuário já avaliou este local, carrega essa avaliação e passa a EDITAR em vez de criar. */
    private fun carregarAvaliacaoExistente(botao: MaterialButton) {
        val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return

        botao.isEnabled = false // só libera depois de saber se é criação ou edição

        lifecycleScope.launch {
            try {
                // A lista vem ordenada da mais recente para a mais antiga.
                val anterior = acessibilidadeRepository
                    .getAvaliacoesByUsuario(idUsuario)
                    .firstOrNull { it.id_local == idLocal }

                val idAnterior = anterior?.id_avaliacao
                if (idAnterior != null) {
                    acessibilidadeRepository.getAcessibilidadeBruta(idAnterior)
                        ?.let { preencherEstados(it) }

                    edicao.idAvaliacao = idAnterior
                    botao.text = TEXTO_BOTAO_EDICAO
                    Snackbar.make(
                        findViewById(R.id.rootAvaliacao),
                        "Você já avaliou este local. Editando sua avaliação.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                edicao.carregado = true
                botao.isEnabled = true
            } catch (e: Exception) {
                // Sem saber se já existe avaliação, não deixa criar uma duplicada.
                Log.e(TAG, "Erro ao carregar avaliação existente", e)
                toast("Não foi possível carregar sua avaliação. Tente novamente.")
                finish()
            }
        }
    }

    /** Estado da tela (RECURSOS) correspondente a uma coluna do banco. */
    private fun estadoDaColuna(coluna: String) =
        viewModel.estados[coluna] ?: CHAVE_ALTERNATIVA[coluna]?.let { viewModel.estados[it] }

    /** Copia respostas e comentários salvos no banco para os cartões da tela. */
    private fun preencherEstados(linha: JsonObject) {
        COLUNAS.forEach { coluna ->
            val estado = estadoDaColuna(coluna) ?: return@forEach
            estado.classificacao = classificacaoDoBanco(linha[coluna]?.jsonPrimitive?.contentOrNull)
            estado.descricao = linha["comentario_$coluna"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        adapter.notifyDataSetChanged()
        adapter.atualizarTopo()
    }

    /** Texto do banco -> constante do enum (compara pelo mesmo texto que seria enviado ao banco). */
    private fun classificacaoDoBanco(valor: String?): Classificacao? {
        if (valor == null) return null
        return Classificacao.values().firstOrNull { valorParaBanco(it.chave) == valor }
    }

    // ---------------------------------------- Envio ----------------------------------------

    private fun enviarAvaliacao(botao: MaterialButton) {
        val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (idUsuario == null) {
            toast("Você precisa estar logado para avaliar!")
            return
        }

        botao.isEnabled = false // evita envio duplicado

        lifecycleScope.launch {
            val idExistente = edicao.idAvaliacao
            var idCriadoAgora: Int? = null
            try {
                // 1) Criação: tb_avaliacao sem nota e sem data (o banco define as duas).
                //    Edição: reaproveita a avaliação que já existe.
                val idAvaliacao = idExistente
                    ?: inserirAvaliacao(idUsuario).also { idCriadoAgora = it }

                // 2) tb_acessibilidade: o trigger do banco recalcula a nota a cada gravação.
                salvarAcessibilidade(idAvaliacao)

                // 3) Fotos: só saem do celular aqui.
                val falhas = enviarFotos(idAvaliacao, idUsuario)

                val sucesso = if (idExistente != null) "Avaliação atualizada com sucesso!"
                else "Avaliação enviada com sucesso!"
                val parcial = if (idExistente != null)
                    "Avaliação atualizada, mas $falhas foto(s) não puderam ser enviadas."
                else "Avaliação enviada, mas $falhas foto(s) não puderam ser enviadas."

                Toast.makeText(
                    this@AvaliacaoActivity,
                    if (falhas == 0) sucesso else parcial,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao enviar avaliação", e)
                // Só desfaz o que foi criado agora: nunca apaga uma avaliação que já existia.
                idCriadoAgora?.let { desfazerAvaliacao(it) }
                botao.isEnabled = true
                Toast.makeText(this@AvaliacaoActivity, "Erro ao enviar avaliação!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun inserirAvaliacao(idUsuario: String): Int =
        avaliacaoRepository
            .createAvaliacao(NovaAvaliacao(id_usuario = idUsuario, id_local = idLocal))
            .id_avaliacao

    /**
     * Converte o que vier do enum para o texto exato aceito por validar_criterios_acessibilidade:
     * 'Adequado', 'Parcialmente adequado', 'Inadequado', 'Não possui' ou 'Não se aplica'.
     */
    private fun valorParaBanco(chave: String?): String {
        val normalizada = chave?.trim()?.lowercase()?.replace('_', ' ')
        return when (normalizada) {
            "adequado" -> "Adequado"
            "parcialmente adequado", "parcialmente", "parcial" -> "Parcialmente adequado"
            "inadequado" -> "Inadequado"
            "não possui", "nao possui" -> NAO_POSSUI
            "não se aplica", "nao se aplica" -> "Não se aplica"
            else -> {
                if (chave != null) Log.w(TAG, "Classificação desconhecida: '$chave'")
                NAO_POSSUI
            }
        }
    }

    /**
     * Grava as 24 colunas de recurso. O que o usuário não avaliou vai como "Não possui".
     * O comentário vai como null quando está vazio, o que também apaga um comentário antigo ao editar.
     */
    private suspend fun salvarAcessibilidade(idAvaliacao: Int) {
        val linha = buildJsonObject {
            put("id_avaliacao", idAvaliacao)
            COLUNAS.forEach { coluna ->
                val estado = estadoDaColuna(coluna)

                put(coluna, valorParaBanco(estado?.classificacao?.chave))

                val descricao = estado?.descricao?.trim().orEmpty()
                put("comentario_$coluna", descricao.ifEmpty { null })
            }
        }
        Log.d(TAG, "Linha acessibilidade: $linha")
        // Atualiza a linha criada pelo trigger do banco (ou insere, se não existir).
        acessibilidadeRepository.createAcessibilidade(linha)
    }

    /** Evita avaliação sem acessibilidade se algo falhar logo após criá-la. */
    private suspend fun desfazerAvaliacao(idAvaliacao: Int) {
        try {
            avaliacaoRepository.deleteAvaliacao(idAvaliacao)
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível desfazer a avaliação $idAvaliacao", e)
        }
    }

    /** Envia as fotos novas de cada recurso avaliado, ligadas pelo id da avaliação e pela chave do recurso. */
    private suspend fun enviarFotos(idAvaliacao: Int, idUsuario: String): Int {
        // Ao editar, já podem existir fotos: continua a numeração para não repetir o mesmo índice.
        val deslocamento = try {
            imageRepository.contarFotosDaAvaliacao(idAvaliacao)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao contar fotos existentes", e)
            0
        }

        var falhas = 0
        viewModel.estados.forEach { (chave, estado) ->
            if (!estado.avaliado) return@forEach // recurso sem avaliação: fotos ignoradas

            estado.fotos.forEachIndexed { indice, foto ->
                try {
                    val arquivo = File(requireNotNull(foto.uri.path))
                    fotoRepository.enviarFoto(
                        idAvaliacao, idUsuario, chave, indice + deslocamento, arquivo, foto.descricao
                    )
                } catch (e: Exception) {
                    falhas++
                    Log.e(TAG, "Erro ao enviar foto de $chave", e)
                }
            }
        }
        return falhas
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}