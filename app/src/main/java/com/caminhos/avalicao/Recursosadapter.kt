package com.caminhos.avaliacao

import android.content.res.ColorStateList
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.caminhos.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Itens da lista: cabeçalho com progresso, títulos de categoria e um cartão por recurso. */
sealed class ItemLista {
    object Topo : ItemLista()
    data class Categoria(val titulo: String) : ItemLista()
    data class Recurso(val def: RecursoDef) : ItemLista()
}

class RecursosAdapter(
    private val estados: Map<String, RecursoEstado>,
    private val host: FotosHost,
    private val nomeLocal: String,
    private val aoMudar: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TIPO_TOPO = 0
        const val TIPO_CATEGORIA = 1
        const val TIPO_RECURSO = 2
        const val PAYLOAD_PROGRESSO = "progresso"
    }

    private val itens: List<ItemLista> = buildList {
        add(ItemLista.Topo)
        CategoriaRecurso.values().forEach { categoria ->
            val doGrupo = RECURSOS.filter { it.categoria == categoria }
            if (doGrupo.isNotEmpty()) {
                add(ItemLista.Categoria(categoria.titulo))
                doGrupo.forEach { add(ItemLista.Recurso(it)) }
            }
        }
    }

    private fun avaliados() = estados.values.count { it.avaliado }

    /** Atualiza só o cabeçalho (contadores e barra de progresso). */
    fun atualizarTopo() = notifyItemChanged(0, PAYLOAD_PROGRESSO)

    /** Redesenha o cartão de um recurso (ex.: depois de adicionar uma foto). */
    fun notificarRecurso(chave: String) {
        val i = itens.indexOfFirst { it is ItemLista.Recurso && it.def.chave == chave }
        if (i >= 0) notifyItemChanged(i)
    }

    override fun getItemCount() = itens.size

    override fun getItemViewType(position: Int) = when (itens[position]) {
        is ItemLista.Topo -> TIPO_TOPO
        is ItemLista.Categoria -> TIPO_CATEGORIA
        is ItemLista.Recurso -> TIPO_RECURSO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TIPO_TOPO -> TopoHolder(inflater.inflate(R.layout.item_topo, parent, false))
            TIPO_CATEGORIA -> CategoriaHolder(inflater.inflate(R.layout.item_categoria, parent, false))
            else -> RecursoHolder(inflater.inflate(R.layout.item_recurso, parent, false), host, aoMudar)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = itens[position]) {
            is ItemLista.Topo -> (holder as TopoHolder).vincular(nomeLocal, avaliados(), RECURSOS.size)
            is ItemLista.Categoria -> (holder as CategoriaHolder).vincular(item.titulo)
            is ItemLista.Recurso ->
                (holder as RecursoHolder).vincular(item.def, estados.getValue(item.def.chave))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_PROGRESSO) && holder is TopoHolder) {
            holder.atualizarProgresso(avaliados(), RECURSOS.size)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // ------------------------------------------------------------------------------------

    class TopoHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val local: TextView = view.findViewById(R.id.textNomeLocal)
        private val avaliados: TextView = view.findViewById(R.id.textProgressoAvaliados)
        private val pendentes: TextView = view.findViewById(R.id.textProgressoPendentes)
        private val barra: LinearProgressIndicator = view.findViewById(R.id.progressAvaliacao)

        init {
            ViewCompat.setAccessibilityHeading(view.findViewById(R.id.textTituloAvaliacao), true)
        }

        fun vincular(nomeLocal: String, feitos: Int, total: Int) {
            local.text = "📍 $nomeLocal"
            barra.max = total
            atualizarProgresso(feitos, total)
        }

        fun atualizarProgresso(feitos: Int, total: Int) {
            avaliados.text = "✔ $feitos avaliados"
            pendentes.text = "${total - feitos} pendentes"
            barra.setProgressCompat(feitos, true)
        }
    }

    class CategoriaHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titulo: TextView = view.findViewById(R.id.textCategoria)

        init {
            ViewCompat.setAccessibilityHeading(titulo, true)
        }

        fun vincular(texto: String) {
            titulo.text = texto
        }
    }

    private class Linha(
        val classificacao: Classificacao,
        val cartao: MaterialCardView,
        val indicador: TextView
    )

    class RecursoHolder(
        view: View,
        host: FotosHost,
        private val aoMudar: () -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val cinza = 0xFF5B6B80.toInt()
        private val borda = 0xFFE3E9F1.toInt()
        private val branco = 0xFFFFFFFF.toInt()

        private val card = view as MaterialCardView
        private val header: View = view.findViewById(R.id.headerRecurso)
        private val icone: TextView = view.findViewById(R.id.textIcone)
        private val titulo: TextView = view.findViewById(R.id.textTituloRecurso)
        private val status: TextView = view.findViewById(R.id.textStatusRecurso)
        private val seta: TextView = view.findViewById(R.id.textSeta)
        private val conteudo: View = view.findViewById(R.id.conteudoRecurso)
        private val textoDescricao: TextView = view.findViewById(R.id.textDescricaoRecurso)
        private val secaoComplemento: View = view.findViewById(R.id.secaoComplemento)
        private val descLayout: TextInputLayout = view.findViewById(R.id.inputDescricaoLayout)
        private val desc: TextInputEditText = view.findViewById(R.id.inputDescricao)
        private val fotos = FotosSection(view, host, aoMudar)

        private val linhas: List<Linha>

        private var def: RecursoDef? = null
        private var estado: RecursoEstado? = null
        private var vinculando = false

        init {
            linhas = listOf(
                R.id.opAdequado to Classificacao.ADEQUADO,
                R.id.opParcial to Classificacao.PARCIAL,
                R.id.opInadequado to Classificacao.INADEQUADO,
                R.id.opNaoPossui to Classificacao.NAO_POSSUI
            ).map { (id, c) ->
                val linha = view.findViewById<MaterialCardView>(id)
                linha.findViewById<TextView>(R.id.textSimbolo).apply {
                    text = c.simbolo
                    setTextColor(c.cor)
                    backgroundTintList = ColorStateList.valueOf(c.fundo)
                }
                linha.findViewById<TextView>(R.id.textOpcaoTitulo).text = c.rotulo
                linha.findViewById<TextView>(R.id.textOpcaoExplicacao).text = c.explicacao
                linha.contentDescription = "${c.rotulo}. ${c.explicacao}"
                linha.setOnClickListener { selecionar(c) }
                Linha(c, linha, linha.findViewById(R.id.textIndicador))
            }

            header.setOnClickListener {
                val e = estado ?: return@setOnClickListener
                e.expandido = !e.expandido
                animarMudanca()
                aplicarExpansao(animar = true)
            }

            desc.doAfterTextChanged {
                if (!vinculando) estado?.descricao = it?.toString().orEmpty()
            }
        }

        fun vincular(def: RecursoDef, estado: RecursoEstado) {
            this.def = def
            this.estado = estado

            icone.text = def.emoji
            titulo.text = def.titulo
            textoDescricao.text = def.descricao

            vinculando = true
            desc.setText(estado.descricao)
            vinculando = false

            fotos.vincular(def, estado)
            aplicarExpansao(animar = false)
            atualizarVisual()
        }

        private fun selecionar(c: Classificacao) {
            val e = estado ?: return
            // Tocar de novo na opção já marcada desfaz a escolha (recurso fica sem avaliação).
            e.classificacao = if (e.classificacao == c) null else c
            animarMudanca()
            atualizarVisual()
            aoMudar()
        }

        private fun animarMudanca() {
            (itemView.parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
        }

        private fun aplicarExpansao(animar: Boolean) {
            val aberto = estado?.expandido == true
            conteudo.visibility = if (aberto) View.VISIBLE else View.GONE
            val giro = if (aberto) 180f else 0f
            if (animar) seta.animate().rotation(giro).setDuration(200).start() else seta.rotation = giro
            ViewCompat.setStateDescription(header, if (aberto) "Expandido" else "Recolhido")
        }

        private fun atualizarVisual() {
            val d = def ?: return
            val c = estado?.classificacao

            linhas.forEach { linha ->
                val ativa = linha.classificacao == c
                linha.cartao.strokeColor = if (ativa) linha.classificacao.cor else borda
                linha.cartao.strokeWidth = px(if (ativa) 2 else 1)
                linha.cartao.setCardBackgroundColor(if (ativa) linha.classificacao.fundo else branco)
                linha.indicador.text = if (ativa) "●" else "○"
                linha.indicador.setTextColor(if (ativa) linha.classificacao.cor else cinza)
                linha.cartao.isSelected = ativa
                ViewCompat.setStateDescription(
                    linha.cartao, if (ativa) "Selecionado" else "Não selecionado"
                )
            }

            // Indicador de conclusão: texto + símbolo + cor + borda
            status.text = if (c != null) "${c.simbolo} ${c.rotulo}" else "Toque para avaliar"
            status.setTextColor(c?.cor ?: cinza)
            header.contentDescription = "${d.titulo}. ${c?.rotulo ?: "Não avaliado"}"
            card.strokeColor = c?.cor ?: borda
            card.strokeWidth = px(if (c != null) 2 else 1)

            secaoComplemento.visibility = if (c != null) View.VISIBLE else View.GONE
            if (c == Classificacao.NAO_POSSUI) {
                descLayout.hint = "Observações (opcional)"
                descLayout.helperText = "Ex.: Existe alguma alternativa acessível?"
            } else {
                descLayout.hint = "Descrição das condições"
                descLayout.helperText = "Ex.: ${d.exemplo}"
            }
        }

        private fun px(dp: Int) = (dp * itemView.resources.displayMetrics.density).toInt()
    }
}