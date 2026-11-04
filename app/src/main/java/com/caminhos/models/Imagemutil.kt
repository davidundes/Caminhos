package com.caminhos.avaliacao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImagemUtil {

    private const val PASTA_CAMERA = "camera"
    private const val PASTA_FOTOS = "fotos"
    private const val QUALIDADE_JPEG = 85

    /** Arquivo temporário (cache do app) onde a câmera grava a foto. */
    fun criarArquivoCamera(context: Context): File {
        val pasta = File(context.cacheDir, PASTA_CAMERA).apply { mkdirs() }
        return File(pasta, "captura_${System.currentTimeMillis()}.jpg")
    }

    /**
     * Reduz a imagem (evita fotos de 12 MP+ na memória e no upload), corrige a rotação e
     * salva como JPEG no cache do app. Ao recomprimir, os metadados EXIF (como a
     * localização GPS) NÃO são copiados, o que protege a privacidade do usuário.
     */
    suspend fun prepararImagem(context: Context, origem: Uri, ladoMax: Int = 1280): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver

                val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(origem)?.use { BitmapFactory.decodeStream(it, null, limites) }

                var amostra = 1
                while (limites.outWidth / (amostra * 2) >= ladoMax ||
                    limites.outHeight / (amostra * 2) >= ladoMax
                ) amostra *= 2

                val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
                val bitmap = resolver.openInputStream(origem)
                    ?.use { BitmapFactory.decodeStream(it, null, opcoes) }
                    ?: return@withContext null

                val graus = resolver.openInputStream(origem)?.use { lerRotacao(it) } ?: 0
                val final = if (graus != 0) {
                    val matriz = Matrix().apply { postRotate(graus.toFloat()) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
                } else bitmap

                val pasta = File(context.cacheDir, PASTA_FOTOS).apply { mkdirs() }
                val destino = File(pasta, "foto_${System.currentTimeMillis()}.jpg")
                FileOutputStream(destino).use { final.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, it) }

                Uri.fromFile(destino)
            } catch (e: Exception) {
                Log.e("ImagemUtil", "Erro ao preparar imagem", e)
                null
            }
        }

    /** Miniatura leve para a grade (decodifica já reduzida). */
    fun carregarMiniatura(uri: Uri, ladoAlvo: Int): Bitmap? {
        val caminho = uri.path ?: return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(caminho, limites)
        var amostra = 1
        while (limites.outWidth / (amostra * 2) >= ladoAlvo &&
            limites.outHeight / (amostra * 2) >= ladoAlvo
        ) amostra *= 2
        return BitmapFactory.decodeFile(caminho, BitmapFactory.Options().apply { inSampleSize = amostra })
    }

    fun excluir(uri: Uri) {
        uri.path?.let { File(it).delete() }
    }

    private fun lerRotacao(stream: InputStream): Int =
        when (ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}