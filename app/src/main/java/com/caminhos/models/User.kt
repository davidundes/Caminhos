package com.caminhos.models

import kotlinx.serialization.Serializable

//Modelo do usario que será usado para criação

@Serializable
data class User(
    val id_usuario: String? = null,
    val nome: String,
    val email: String? = null,
    val cpf: String? = null)
