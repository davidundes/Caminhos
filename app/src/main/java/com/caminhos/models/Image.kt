package com.caminhos.models

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val id_image: Int,
    val id_foto: Int,
    val id_local: Int,
    val id_avalicao: Int,
    val id_usuario: Int,
    val url: String,
    val descricao: String,
    val caminho: String
)