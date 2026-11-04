package com.caminhos.avaliacao

import androidx.lifecycle.ViewModel

enum class Resposta { SIM, NAO, NAO_SEI }

/** Descrição de um recurso. Para criar um novo cartão, basta adicionar um item em RECURSOS. */
data class RecursoDef(
    val chave: String,
    val titulo: String,
    val emoji: String,
    val nomeCurto: String,
    val dica: String,
    val limiteFotos: Int = LIMITE_FOTOS_PADRAO   // <- NOVO (limite configurável)
)

class RecursoEstado(
    var resposta: Resposta? = null,
    var nota: Int = 0,
    var descricao: String = "",
    var observacoes: String = "",
    var fazFalta: Boolean = false,
    var alternativa: String = "",
    var expandido: Boolean = false,
    val fotos: MutableList<FotoAnexo> = mutableListOf()   // <- NOVO
) {
    /** Conta como "avaliado": existe com nota, ou foi informado que não existe. */
    val avaliado: Boolean
        get() = (resposta == Resposta.SIM && nota > 0) || resposta == Resposta.NAO
}

class AvaliacaoViewModel : ViewModel() {
    var notaGeral: Int = 0
    val estados: Map<String, RecursoEstado> = RECURSOS.associate { it.chave to RecursoEstado() }
}

fun rotuloNota(nota: Int): String = when (nota) {
    1 -> "Muito ruim"
    2 -> "Ruim"
    3 -> "Regular"
    4 -> "Bom"
    5 -> "Excelente"
    else -> ""
}

val RECURSOS = listOf(
    RecursoDef(
        "rampa", "Rampa de acesso", "♿", "a rampa",
        "Inclinação, largura, corrimão, superfície, obstáculos e facilidade de uso"
    ),
    RecursoDef(
        "elevador", "Elevador", "🛗", "o elevador",
        "Tamanho, espaço para cadeira de rodas, botões (braille/sonoros) e funcionamento"
    ),
    RecursoDef(
        "banheiro_acessivel", "Banheiro acessível", "🚻", "o banheiro acessível",
        "Espaço para manobrar, barras de apoio, largura da porta e altura de pia e vaso"
    ),
    RecursoDef(
        "piso_tatil", "Piso tátil", "🦯", "o piso tátil",
        "Continuidade, conservação, obstáculos no caminho e se leva aos lugares certos"
    ),
    RecursoDef(
        "vaga_pcd", "Vaga para pessoa com deficiência", "🅿️", "a vaga PCD",
        "Quantidade, sinalização, largura, distância da entrada e se costuma estar livre"
    ),
    RecursoDef(
        "entrada_acessivel", "Entrada acessível", "🚪", "a entrada",
        "Degraus, largura, tipo de abertura da porta, piso e necessidade de ajuda"
    ),
    RecursoDef(
        "corrimao", "Corrimão", "🤝", "o corrimão",
        "Altura, firmeza, dois lados, material e se acompanha toda a rampa ou escada"
    ),
    RecursoDef(
        "portas_largas", "Portas largas", "↔️", "as portas",
        "Largura, peso, soleiras e espaço para passar com cadeira de rodas"
    ),
    RecursoDef(
        "sinalizacao", "Sinalização acessível", "🪧", "a sinalização",
        "Placas visíveis, braille, contraste, símbolos e altura de leitura"
    ),
    RecursoDef(
        "iluminacao", "Iluminação adequada", "💡", "a iluminação",
        "Se é suficiente, uniforme, se há pontos escuros e se é boa nos acessos"
    )
)