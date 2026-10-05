package com.caminhos.models
import kotlinx.serialization.Serializable

@Serializable
data class Local(
    val id_local: Int? = null,
    val nome: String,
    val endereco: String,
    val cep: String,
    val latitude: Double,
    val longitude: Double,
    val telefone: String? = null,
    val horario: String
)