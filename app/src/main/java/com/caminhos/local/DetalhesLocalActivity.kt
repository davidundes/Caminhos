package com.caminhos.local

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.avalicao.Avaliacao
import com.caminhos.avaliacao.AvaliacaoActivity
import com.caminhos.database.Repository
import com.caminhos.models.Local
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tela com os detalhes de um estabelecimento (tb_local) e as avaliações de cada usuário.
 * Recebe o extra "id_local" (Int), enviado pela localInfoWindow.
 */
class DetalhesLocalActivity : AppCompatActivity() {

    private val localRepository = Repository.LocalRepository()
    private val acessibilidadeRepository = Repository.AcessibilidadeRepository()
    private val userRepository = Repository.UserRepository()

    private var idLocal = -1
    private var carregando = false

    private val progress by lazy { findViewById<ProgressBar>(R.id.progressDetalhes) }
    private val containerAvaliacoes by lazy { findViewById<LinearLayout>(R.id.containerAvaliacoes) }
    private val textSemAvaliacoes by lazy { findViewById<TextView>(R.id.textSemAvaliacoes) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detalhes_local)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootDetalhes)) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        idLocal = intent.getIntExtra("id_local", -1)
        if (idLocal == -1) {
            Toast.makeText(this, "Local inválido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<TextView>(R.id.buttonVoltar).setOnClickListener { finish() }

        findViewById<Button>(R.id.buttonAvaliarLocal).setOnClickListener {
            val intent = Intent(this, AvaliacaoActivity::class.java)
            intent.putExtra("id_local", idLocal)
            startActivity(intent)
        }
    }

    // Recarrega ao voltar da AvaliacaoActivity, para a nova avaliação/nota aparecer.
    override fun onResume() {
        super.onResume()
        carregar()
    }

    private fun carregar() {
        if (carregando) return
        carregando = true
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val local = localRepository.getLocal(idLocal)
                if (local == null) {
                    Toast.makeText(this@DetalhesLocalActivity, "Local não encontrado", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                val avaliacoes = acessibilidadeRepository
                    .getAvaliacoesByLocal(idLocal)
                    .sortedByDescending { it.data?.toString() ?: "" } // mais recentes primeiro

                // Nomes dos usuários e critérios de acessibilidade são buscados em paralelo.
                val (nomes, criterios) = coroutineScope {
                    val n = async { buscarNomes(avaliacoes) }
                    val c = async { buscarCriterios(avaliacoes) }
                    n.await() to c.await()
                }

                mostrarLocal(local)
                mostrarAvaliacoes(avaliacoes, nomes, criterios)
            } catch (e: Exception) {
                Log.e("DetalhesLocal", "Erro ao carregar detalhes", e)
                Toast.makeText(this@DetalhesLocalActivity, "Erro ao carregar os detalhes", Toast.LENGTH_SHORT).show()
            } finally {
                carregando = false
                progress.visibility = View.GONE
            }
        }
    }

    // ---------------------------------------------------------------- dados

    /** id_usuario -> nome. Se algo falhar, a avaliação aparece como "Usuário". */
    private suspend fun buscarNomes(avaliacoes: List<Avaliacao>): Map<String, String> = coroutineScope {
        avaliacoes
            .mapNotNull { it.id_usuario }
            .distinct()
            .map { id ->
                // ATENÇÃO: ajuste "nome" se o campo de User tiver outro nome.
                async { id to runCatching { userRepository.getUser(id)?.nome }.getOrNull() }
            }
            .awaitAll()
            .mapNotNull { (id, nome) -> if (nome.isNullOrBlank()) null else id to nome }
            .toMap()
    }

    /** id_avaliacao -> lista de (critério, situação), lida da linha bruta de tb_acessibilidade. */
    private suspend fun buscarCriterios(
        avaliacoes: List<Avaliacao>
    ): Map<Int, List<Pair<String, String>>> = coroutineScope {
        avaliacoes
            .mapNotNull { it.id_avaliacao }
            .map { id ->
                async { id to runCatching { acessibilidadeRepository.getAcessibilidadeBruta(id) }.getOrNull() }
            }
            .awaitAll()
            .associate { (id, linha) -> id to extrairCriterios(linha) }
    }

    /**
     * Só entram colunas cujo valor é uma das situações aceitas pelo banco
     * (validar_criterios_acessibilidade). Assim ids, nota e colunas nulas são ignorados
     * sem precisar listar os nomes das colunas aqui.
     */
    private fun extrairCriterios(linha: JsonObject?): List<Pair<String, String>> {
        if (linha == null) return emptyList()
        return linha.entries.mapNotNull { (chave, valor) ->
            val texto = (valor as? JsonPrimitive)?.contentOrNull
            if (texto != null && texto in SITUACOES) formatarChave(chave) to texto else null
        }
    }

    private fun formatarChave(chave: String): String =
        chave.removePrefix("id_").replace('_', ' ').replaceFirstChar { it.uppercase() }

    // ------------------------------------------------------------------ UI

    private fun mostrarLocal(local: Local) {
        findViewById<TextView>(R.id.textNomeLocal).text = local.nome

        val enderecoCompleto = listOfNotNull(
            local.endereco?.takeIf { it.isNotBlank() },
            local.cep?.takeIf { it.isNotBlank() }?.let { "CEP $it" }
        ).joinToString(" - ")
        preencherLinha(R.id.rowEndereco, R.id.textEnderecoLocal, enderecoCompleto)

        preencherLinha(R.id.rowTelefone, R.id.textTelefoneLocal, local.telefone)
        val telefone = local.telefone
        if (!telefone.isNullOrBlank()) {
            findViewById<TextView>(R.id.textTelefoneLocal).setOnClickListener {
                val numero = telefone.filter { it.isDigit() || it == '+' }
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")))
            }
        }

        preencherLinha(R.id.rowHorario, R.id.textHorarioLocal, local.horario)
    }

    private fun preencherLinha(idLinha: Int, idTexto: Int, valor: String?) {
        val linha = findViewById<View>(idLinha)
        if (valor.isNullOrBlank()) {
            linha.visibility = View.GONE
        } else {
            linha.visibility = View.VISIBLE
            findViewById<TextView>(idTexto).text = valor
        }
    }

    private fun mostrarAvaliacoes(
        avaliacoes: List<Avaliacao>,
        nomes: Map<String, String>,
        criterios: Map<Int, List<Pair<String, String>>>
    ) {
        // Resumo (média das notas)
        val notas = avaliacoes.mapNotNull { it.nota?.toDouble() }
        val textMedia = findViewById<TextView>(R.id.textMediaNota)
        val textEstrelas = findViewById<TextView>(R.id.textEstrelasMedia)
        val textQtd = findViewById<TextView>(R.id.textQtdAvaliacoes)

        if (notas.isEmpty()) {
            textMedia.text = "–"
            textEstrelas.text = estrelas(0.0)
            textEstrelas.contentDescription = "Sem avaliações"
        } else {
            val media = notas.average()
            textMedia.text = formatarNota(media)
            textEstrelas.text = estrelas(media)
            textEstrelas.contentDescription = "Nota média ${formatarNota(media)} de 5"
        }
        textQtd.text = when (avaliacoes.size) {
            0 -> "Nenhuma avaliação"
            1 -> "1 avaliação"
            else -> "${avaliacoes.size} avaliações"
        }

        mostrarResumoAcessibilidade(criterios)

        // Lista
        containerAvaliacoes.removeAllViews()
        textSemAvaliacoes.visibility = if (avaliacoes.isEmpty()) View.VISIBLE else View.GONE

        for (avaliacao in avaliacoes) {
            containerAvaliacoes.addView(
                criarItemAvaliacao(avaliacao, nomes, criterios)
            )
        }
    }

    /**
     * Para cada critério, mostra a situação mais votada entre todas as avaliações do local.
     * - "Não se aplica" não entra na votação (a menos que seja a única resposta).
     * - Em caso de empate, vale a situação mais crítica (Inadequado > Não possui >
     *   Parcialmente adequado > Adequado), para não esconder um problema de acessibilidade.
     */
    private fun mostrarResumoAcessibilidade(criterios: Map<Int, List<Pair<String, String>>>) {
        val bloco = findViewById<View>(R.id.blocoAcessibilidadeResumo)
        val caixa = findViewById<LinearLayout>(R.id.containerAcessibilidadeResumo)
        caixa.removeAllViews()

        // critério -> (situação -> nº de votos)
        val votos = LinkedHashMap<String, MutableMap<String, Int>>()
        criterios.values.forEach { lista ->
            lista.forEach { (criterio, situacao) ->
                val contagem = votos.getOrPut(criterio) { mutableMapOf() }
                contagem[situacao] = (contagem[situacao] ?: 0) + 1
            }
        }

        if (votos.isEmpty()) {
            bloco.visibility = View.GONE
            return
        }
        bloco.visibility = View.VISIBLE

        for ((criterio, contagem) in votos) {
            val validos = contagem.filterKeys { it != "Não se aplica" }.ifEmpty { contagem }
            val maiorVoto = validos.values.maxOrNull() ?: continue
            val vencedora = validos
                .filter { it.value == maiorVoto }
                .keys
                .minByOrNull { PRIORIDADE_EMPATE.indexOf(it) } ?: continue
            val total = contagem.values.sum()

            caixa.addView(criarLinhaCriterio(criterio, vencedora, "$maiorVoto/$total"))
        }
    }

    private fun criarItemAvaliacao(
        avaliacao: Avaliacao,
        nomes: Map<String, String>,
        criterios: Map<Int, List<Pair<String, String>>>
    ): View {
        val item = layoutInflater.inflate(R.layout.item_avaliacao_local, containerAvaliacoes, false)

        item.findViewById<TextView>(R.id.textUsuarioAvaliacao).text =
            nomes[avaliacao.id_usuario] ?: "Usuário"

        item.findViewById<TextView>(R.id.textDataAvaliacao).text =
            formatarData(avaliacao.data?.toString())

        val nota = avaliacao.nota?.toDouble()
        val textEstrelas = item.findViewById<TextView>(R.id.textEstrelasAvaliacao)
        val textNota = item.findViewById<TextView>(R.id.textNotaAvaliacao)
        if (nota != null) {
            textEstrelas.text = estrelas(nota)
            textNota.text = "Nota ${formatarNota(nota)}"
            textEstrelas.contentDescription = "Nota ${formatarNota(nota)} de 5"
        } else {
            textEstrelas.visibility = View.GONE
            textNota.visibility = View.GONE
        }

        val textComentario = item.findViewById<TextView>(R.id.textComentarioAvaliacao)
        if (avaliacao.comentario.isNullOrBlank()) {
            textComentario.visibility = View.GONE
        } else {
            textComentario.text = avaliacao.comentario
        }

        // Critérios de acessibilidade (expansível)
        val lista = criterios[avaliacao.id_avaliacao] ?: emptyList()
        val botao = item.findViewById<TextView>(R.id.buttonToggleAcessibilidade)
        val caixa = item.findViewById<LinearLayout>(R.id.containerAcessibilidade)

        if (lista.isEmpty()) {
            botao.visibility = View.GONE
        } else {
            lista.forEach { (criterio, situacao) -> caixa.addView(criarLinhaCriterio(criterio, situacao)) }

            val textoFechado = "Ver acessibilidade (${lista.size})"
            botao.text = textoFechado
            botao.setOnClickListener {
                val aberto = caixa.visibility == View.VISIBLE
                caixa.visibility = if (aberto) View.GONE else View.VISIBLE
                botao.text = if (aberto) textoFechado else "Ocultar acessibilidade"
            }
        }

        return item
    }

    private fun criarLinhaCriterio(criterio: String, situacao: String, votos: String? = null): View {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }

        val nome = TextView(this).apply {
            text = criterio
            setTextColor(Color.parseColor("#1B2A41"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val (fundo, texto) = coresDaSituacao(situacao)
        val chip = TextView(this).apply {
            text = if (votos != null) "$situacao · $votos" else situacao
            setTextColor(Color.parseColor(texto))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor(fundo))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }

        linha.addView(nome)
        linha.addView(chip)
        return linha
    }

    // -------------------------------------------------------------- helpers

    /** Par (cor de fundo, cor do texto). */
    private fun coresDaSituacao(situacao: String): Pair<String, String> = when (situacao) {
        "Adequado" -> "#E3F6EA" to "#1E7B45"
        "Parcialmente adequado" -> "#FFF3D6" to "#8A5A00"
        "Inadequado" -> "#FDE6E6" to "#B3261E"
        else -> "#EEF1F5" to "#6B7A90" // Não possui / Não se aplica
    }

    /** Assume nota de 0 a 5. Se a escala da sua função de nota for outra, ajuste aqui. */
    private fun estrelas(nota: Double): String {
        val cheias = nota.roundToInt().coerceIn(0, 5)
        return "★".repeat(cheias) + "☆".repeat(5 - cheias)
    }

    private fun formatarNota(nota: Double): String =
        String.format(Locale("pt", "BR"), "%.1f", nota)

    /** "2026-11-14T16:10:01+00:00" -> "14/11/2026". Se não reconhecer, devolve o texto original. */
    private fun formatarData(data: String?): String {
        if (data.isNullOrBlank()) return ""
        val partes = data.take(10).split("-")
        return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else data
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    private companion object {
        // Ordem usada para desempate no resumo: a primeira da lista vence.
        val PRIORIDADE_EMPATE = listOf(
            "Inadequado",
            "Não possui",
            "Parcialmente adequado",
            "Adequado",
            "Não se aplica"
        )

        val SITUACOES = setOf(
            "Adequado",
            "Parcialmente adequado",
            "Inadequado",
            "Não possui",
            "Não se aplica"
        )
    }
}