package com.mobbacs.models
import kotlinx.serialization.Serializable

@Serializable
data class Local(
    val id_local: Int,
    val nome: String,
    val endereco: String,
    val cep: String,
    val latitude: Double,
    val longitude: Double,
    val telofone: String? = null,
    val horario: String
)