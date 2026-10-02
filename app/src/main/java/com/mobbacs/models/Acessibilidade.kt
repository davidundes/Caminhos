package com.mobbacs.models

import kotlinx.serialization.Serializable

@Serializable
data class Acessibilidade(
    val id_acessibilidade: Int? = null,
    val id_avaliacao: Int,
    val rampa: Boolean,
    val elevador: Boolean,
    val banheiro_acessivel: Boolean,
    val piso_tatil: Boolean,
    val vaga_pcd: Boolean,
    val entrada_acessivel: Boolean,
    val corrimao: Boolean,
    val portas_largas: Boolean,
    val sinalizacao: Boolean,
    val iluminacao: Boolean
)