package com.caminhos.models
import kotlinx.serialization.Serializable

@Serializable
data class Avaliacao(
    val id_avaliacao: Int? = null,
    val id_usuario: String,
    val id_local: Int,
    val nota: Float,
    val data: String
)