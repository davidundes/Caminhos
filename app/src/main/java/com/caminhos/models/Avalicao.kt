package com.caminhos.models

import kotlinx.serialization.Serializable

/** Leitura de tb_avaliacao. */
@Serializable
data class Avaliacao(
    val id_avaliacao: Int? = null,
    val id_usuario: String? = null,
    val id_local: Int,
    val nota: Float? = null,
    val comentario: String? = null,
    val data: String? = null
)

/** Insert: nota e data NÃO são enviadas, o banco define as duas. */
@Serializable
data class NovaAvaliacao(
    val id_usuario: String,
    val id_local: Int,
    val comentario: String? = null
)

@Serializable
data class AvaliacaoCriada(val id_avaliacao: Int)