package com.caminhos.menu

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.widget.doAfterTextChanged
import com.caminhos.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Controla um <include layout="@layout/item_campo_perfil"/>: leitura x edição. */
class CampoPerfil(raiz: View, @DrawableRes icone: Int, val rotulo: String) {

    private val leitura: View = raiz.findViewById(R.id.campoLeitura)
    private val valor: TextView = raiz.findViewById(R.id.valorCampo)
    val acao: MaterialButton = raiz.findViewById(R.id.acaoCampo)
    val layout: TextInputLayout = raiz.findViewById(R.id.inputLayoutCampo)
    val input: TextInputEditText = raiz.findViewById(R.id.inputCampo)

    init {
        raiz.findViewById<ImageView>(R.id.iconeCampo).setImageResource(icone)
        raiz.findViewById<TextView>(R.id.rotuloCampo).text = rotulo
        layout.hint = rotulo
        input.doAfterTextChanged { layout.error = null } // limpa o erro ao digitar
        // Os 3 campos compartilham o mesmo id: o estado é guardado no PerfilViewModel (rascunho).
        input.isSaveEnabled = false
    }

    fun mostrarValor(texto: String, descricao: String = texto) {
        valor.text = texto
        valor.contentDescription = "$rotulo: $descricao"
    }

    fun modoEdicao(edicao: Boolean) {
        leitura.visibility = if (edicao) View.GONE else View.VISIBLE
        layout.visibility = if (edicao) View.VISIBLE else View.GONE
    }
}