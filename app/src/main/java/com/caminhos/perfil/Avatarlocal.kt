package com.caminhos.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foto de perfil guardada na memória interna do app (privada, só este app lê).
 * Um arquivo por usuário: quem entra no mesmo aparelho só vê a própria foto.
 * Persiste ao fechar o app e ao sair da conta; é apagada se os dados do app forem limpos.
 */
object AvatarLocal {

    private fun arquivo(context: Context, idUsuario: String): File {
        val pasta = File(context.filesDir, "perfil").apply { mkdirs() }
        val nomeSeguro = idUsuario.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(pasta, "avatar_$nomeSeguro.jpg")
    }

    suspend fun carregar(context: Context, idUsuario: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val f = arquivo(context, idUsuario)
            if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        }

    /** Copia a imagem já preparada (512 px, sem GPS) para o armazenamento do app. */
    suspend fun salvar(context: Context, idUsuario: String, origem: Uri) =
        withContext(Dispatchers.IO) {
            File(requireNotNull(origem.path)).copyTo(arquivo(context, idUsuario), overwrite = true)
        }

    suspend fun remover(context: Context, idUsuario: String) =
        withContext(Dispatchers.IO) { arquivo(context, idUsuario).delete() }
}