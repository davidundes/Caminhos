package com.caminhos.models

import kotlinx.serialization.Serializable

/**
 * Cada recurso guarda um destes textos (ou null se não foi avaliado):
 * "Adequado", "Parcialmente adequado", "Inadequado" ou "Não possui".
 */
@Serializable
data class Acessibilidade(
    val id_acessibilidade: Int? = null,
    val id_avaliacao: Int,

    val rampa: String? = null,
    val elevador: String? = null,
    val banheiro_acessivel: String? = null,
    val piso_tatil: String? = null,
    val vaga_pcd: String? = null,
    val entrada_acessivel: String? = null,
    val corrimao: String? = null,
    val portas_largas: String? = null,
    val sinalizacao: String? = null,
    val iluminacao: String? = null,
    val banheiro_adaptado: String? = null,
    val atendimento_prioritario: String? = null,
    val interprete_libras: String? = null,
    val cardapio_acessivel: String? = null,
    val informacao_em_braille: String? = null,
    val piso_antiderrapante: String? = null,
    val mapa_tatil: String? = null,
    val sinalizacao_visual: String? = null,
    val sinalizacao_tatil: String? = null,
    val sinalizacao_sonora: String? = null,
    val comunicacao_acessivel: String? = null,
    val mobiliario_adaptado: String? = null,
    val balcao_acessivel: String? = null,
    val acesso_cao_guia: String? = null,

    val comentario_rampa: String? = null,
    val comentario_elevador: String? = null,
    val comentario_banheiro_acessivel: String? = null,
    val comentario_piso_tatil: String? = null,
    val comentario_vaga_pcd: String? = null,
    val comentario_entrada_acessivel: String? = null,
    val comentario_corrimao: String? = null,
    val comentario_portas_largas: String? = null,
    val comentario_sinalizacao: String? = null,
    val comentario_iluminacao: String? = null,
    val comentario_banheiro_adaptado: String? = null,
    val comentario_atendimento_prioritario: String? = null,
    val comentario_interprete_libras: String? = null,
    val comentario_cardapio_acessivel: String? = null,
    val comentario_informacao_em_braille: String? = null,
    val comentario_piso_antiderrapante: String? = null,
    val comentario_mapa_tatil: String? = null,
    val comentario_sinalizacao_visual: String? = null,
    val comentario_sinalizacao_tatil: String? = null,
    val comentario_sinalizacao_sonora: String? = null,
    val comentario_comunicacao_acessivel: String? = null,
    val comentario_mobiliario_adaptado: String? = null,
    val comentario_balcao_acessivel: String? = null,
    val comentario_acesso_cao_guia: String? = null,
)