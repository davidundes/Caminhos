package com.caminhos.avaliacao

import android.app.Dialog
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caminhos.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.File

/** Limite padrão de fotos por recurso. Para mudar um recurso: RecursoDef(..., limiteFotos = 6). */
const val LIMITE_FOTOS_PADRAO = 4

/** Foto já preparada (no cache do app) e ainda NÃO enviada, com descrição opcional. */
data class FotoAnexo(val uri: Uri, val descricao: String = "")

/** A Activity implementa isto: é ela quem registra as Activity Result APIs. */
interface FotosHost {
    fun tirarFoto(chave: String)
    fun escolherDaGaleria(chave: String)
}

/** Guarda qual recurso está esperando a foto (sobrevive à rotação durante a câmera). */
class FotoPendenteViewModel : ViewModel() {
    var chave: String? = null
    var arquivoCamera: File? = null
}

/**
 * Seção "Fotos da acessibilidade" (view_fotos.xml). Reutilizada em TODOS os cartões:
 * recebe a definição do recurso e o estado (onde ficam as fotos daquele recurso).
 */
class FotosSection(
    private val raiz: View,
    private val def: RecursoDef,
    private val estado: RecursoEstado,
    private val host: FotosHost,
    private val aoMudar: () -> Unit
) {
    private val contexto = raiz.context
    private val contador: TextView = raiz.findViewById(R.id.textContadorFotos)
    private val botaoAdicionar: MaterialButton = raiz.findViewById(R.id.btnAdicionarFoto)
    private val recycler: RecyclerView = raiz.findViewById(R.id.recyclerFotos)
    private val adapter = FotoAdapter(estado.fotos, ::ampliar, ::remover)

    init {
        raiz.findViewById<TextView>(R.id.textOrientacaoFotos).text =
            "Adicione fotos para mostrar as condições d${def.nomeCurto}"

        val colunas = if (raiz.resources.configuration.screenWidthDp >= 600) 4 else 3
        recycler.layoutManager = GridLayoutManager(contexto, colunas)
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false

        botaoAdicionar.setOnClickListener { escolherOrigem() }
        atualizar()
    }

    private fun escolherOrigem() {
        if (estado.fotos.size >= def.limiteFotos) {
            Toast.makeText(
                contexto, "Limite de ${def.limiteFotos} fotos para este recurso.", Toast.LENGTH_SHORT
            ).show()
            return
        }
        MaterialAlertDialogBuilder(contexto)
            .setTitle("Adicionar foto")
            .setItems(arrayOf("Tirar foto agora", "Escolher da galeria")) { _, i ->
                if (i == 0) host.tirarFoto(def.chave) else host.escolherDaGaleria(def.chave)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Chamado pela Activity depois que a imagem foi capturada/escolhida e preparada. */
    fun mostrarPreview(uri: Uri) {
        val conteudo = LayoutInflater.from(contexto).inflate(R.layout.dialog_preview_foto, null)
        conteudo.findViewById<ImageView>(R.id.imgPreview).setImageURI(uri)
        val campo = conteudo.findViewById<TextInputEditText>(R.id.inputDescricaoFoto)

        MaterialAlertDialogBuilder(contexto)
            .setTitle("Pré-visualização")
            .setView(conteudo)
            .setCancelable(false)
            .setPositiveButton("Adicionar") { _, _ ->
                estado.fotos.add(FotoAnexo(uri, campo.text?.toString().orEmpty().trim()))
                atualizar()
                aoMudar()
            }
            .setNegativeButton("Cancelar") { _, _ -> ImagemUtil.excluir(uri) }
            .show()
    }

    private fun remover(posicao: Int) {
        val foto = estado.fotos.removeAt(posicao)
        ImagemUtil.excluir(foto.uri)
        atualizar()
        aoMudar()
    }

    private fun ampliar(foto: FotoAnexo) {
        val dialogo = Dialog(contexto, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialogo.setContentView(R.layout.dialog_foto_ampliada)
        dialogo.findViewById<ImageView>(R.id.imgAmpliada).apply {
            setImageURI(foto.uri)
            contentDescription = descricaoAcessivel(foto, estado.fotos.indexOf(foto) + 1)
        }
        dialogo.findViewById<TextView>(R.id.textLegendaAmpliada).apply {
            if (foto.descricao.isNotEmpty()) {
                text = foto.descricao
                visibility = View.VISIBLE
            }
        }
        dialogo.findViewById<View>(R.id.btnFecharAmpliada).setOnClickListener { dialogo.dismiss() }
        dialogo.show()
    }

    private fun atualizar() {
        val total = estado.fotos.size
        contador.text = "$total/${def.limiteFotos}"
        contador.contentDescription = "$total de ${def.limiteFotos} fotos adicionadas"
        recycler.visibility = if (total == 0) View.GONE else View.VISIBLE
        val cheio = total >= def.limiteFotos
        botaoAdicionar.isEnabled = !cheio
        botaoAdicionar.text = if (cheio) "Limite de fotos atingido" else "Adicionar foto"
        adapter.notifyDataSetChanged()
    }
}

private fun descricaoAcessivel(foto: FotoAnexo, numero: Int): String =
    buildString {
        append("Foto $numero")
        if (foto.descricao.isNotEmpty()) append(": ${foto.descricao}")
        append(". Toque para ampliar.")
    }

private class FotoAdapter(
    private val fotos: List<FotoAnexo>,
    private val aoAmpliar: (FotoAnexo) -> Unit,
    private val aoRemover: (Int) -> Unit
) : RecyclerView.Adapter<FotoAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imagem: ImageView = view.findViewById(R.id.imgFoto)
        val legenda: TextView = view.findViewById(R.id.textLegendaFoto)
        val remover: ImageButton = view.findViewById(R.id.btnRemoverFoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_foto, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val foto = fotos[position]
        holder.imagem.setImageBitmap(ImagemUtil.carregarMiniatura(foto.uri, 300))
        holder.imagem.contentDescription = descricaoAcessivel(foto, position + 1)
        holder.imagem.setOnClickListener { aoAmpliar(foto) }

        holder.legenda.visibility = if (foto.descricao.isEmpty()) View.GONE else View.VISIBLE
        holder.legenda.text = foto.descricao

        holder.remover.contentDescription = "Remover foto ${position + 1}"
        holder.remover.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) aoRemover(p)
        }
    }

    override fun getItemCount() = fotos.size
}