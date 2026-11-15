package com.caminhos.menu

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import java.io.File

/** Dados do usuário já carregados (CPF sempre só com dígitos). */
data class PerfilCompleto(
    val id: String,
    val nome: String,
    val email: String,
    val cpf: String,
    val bio: String,
    val qtdAvaliacoes: Int
)

/** Guarda o estado da tela: sobrevive à rotação sem recarregar nem perder a edição. */
class PerfilViewModel : ViewModel() {
    var dados: PerfilCompleto? = null
    var fotoAtual: Bitmap? = null      // foto salva no aparelho

    var editando = false
    var novaFoto: Uri? = null          // foto escolhida, ainda não enviada
    var removerFoto = false
    var arquivoCamera: File? = null
    var cpfVisivel = false
    val rascunho = mutableMapOf<String, String>()   // texto digitado durante a edição
}