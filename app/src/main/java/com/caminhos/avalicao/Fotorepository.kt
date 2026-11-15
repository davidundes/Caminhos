package com.caminhos.database

import com.caminhos.database.SupabaseClient.client
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File


/** Uma foto de um recurso (ex.: rampa) dentro de uma avaliação. */
@Serializable
data class FotoRecurso(
    val id_foto: Int? = null,
    val id_avaliacao: Int,
    val recurso: String,      // mesma chave do RecursoDef ("rampa", "piso_tatil", ...)
    val url: String,
    val descricao: String? = null
)

class FotoRepository {
    private val bucket get() = client.storage.from("fotos-acessibilidade")

    suspend fun enviarFoto(
        idAvaliacao: Int,
        idUsuario: String,
        recurso: String,
        indice: Int,
        arquivo: File,
        descricao: String
    ) {
        val bytes = withContext(Dispatchers.IO) { arquivo.readBytes() }
        val caminho = "$idUsuario/$idAvaliacao/${recurso}_$indice.jpg"

        bucket.upload(caminho, bytes)
        val url = bucket.publicUrl(caminho)

        client.from("tb_foto_recurso").insert(
            FotoRecurso(
                id_avaliacao = idAvaliacao,
                recurso = recurso,
                url = url,
                descricao = descricao.ifBlank { null }
            )
        )
    }

    suspend fun getFotosByAvaliacoes(ids: List<Int>): List<FotoRecurso> {
        if (ids.isEmpty()) return emptyList()
        return client.from("tb_foto_recurso")
            .select { filter { isIn("id_avaliacao", ids) } }
            .decodeList<FotoRecurso>()
    }
}