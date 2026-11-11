package com.caminhos.database

import com.caminhos.database.SupabaseClient.client
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

/** Classificação de UM recurso dentro de uma avaliação (tabela tb_avaliacao_recurso). */
@Serializable
data class AvaliacaoRecurso(
    val id_avaliacao_recurso: Int? = null,
    val id_avaliacao: Int,
    val recurso: String,            // chave do recurso: "rampa", "piso_tatil", ...
    val classificacao: String,      // adequado | parcial | inadequado | nao_possui
    val descricao: String? = null
)

class AvaliacaoRecursoRepository {

    suspend fun createAll(itens: List<AvaliacaoRecurso>) {
        if (itens.isEmpty()) return
        client.from("tb_avaliacao_recurso").insert(itens)
    }

    suspend fun getByAvaliacoes(ids: List<Int>): List<AvaliacaoRecurso> {
        if (ids.isEmpty()) return emptyList()
        return client.from("tb_avaliacao_recurso")
            .select { filter { isIn("id_avaliacao", ids) } }
            .decodeList<AvaliacaoRecurso>()
    }
}