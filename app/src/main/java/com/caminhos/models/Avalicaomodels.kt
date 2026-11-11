package com.caminhos.avaliacao

import androidx.lifecycle.ViewModel

/** As quatro classificações de cada recurso. Sempre aparecem com texto + símbolo + cor. */
enum class Classificacao(
    val chave: String,        // valor gravado no banco (coluna "classificacao")
    val rotulo: String,
    val explicacao: String,   // texto explicativo mostrado e lido pelo leitor de tela
    val simbolo: String,
    val cor: Int,             // cor do texto/borda (contraste AA sobre branco)
    val fundo: Int,           // cor suave de fundo quando selecionada
    val pontos: Int           // usada só para calcular a nota do mapa (veja notaDerivada)
) {
    ADEQUADO(
        "adequado", "Adequado", "Existe e está em boas condições de uso.",
        "✔", 0xFF2E7D32.toInt(), 0xFFE8F5E9.toInt(), 5
    ),
    PARCIAL(
        "parcial", "Parcialmente adequado", "Existe, mas tem limitações que dificultam o uso.",
        "!", 0xFF9A5B00.toInt(), 0xFFFFF3E0.toInt(), 3
    ),
    INADEQUADO(
        "inadequado", "Inadequado", "Existe, mas as condições não são acessíveis.",
        "✕", 0xFFC62828.toInt(), 0xFFFDECEA.toInt(), 1
    ),
    NAO_POSSUI(
        "nao_possui", "Não possui", "O local não oferece este recurso.",
        "—", 0xFF455A64.toInt(), 0xFFECEFF1.toInt(), 1
    )
}

enum class CategoriaRecurso(val titulo: String) {
    ACESSO("Acesso e circulação"),
    BANHEIROS("Banheiros e atendimento"),
    ORIENTACAO("Orientação e comunicação"),
    SERVICOS("Serviços e alimentação")
}

/** Descrição de um recurso. Para criar um novo cartão, basta adicionar um item em RECURSOS. */
data class RecursoDef(
    val chave: String,               // nome do campo (ex.: "rampa", "piso_tatil")
    val titulo: String,
    val emoji: String,
    val categoria: CategoriaRecurso,
    val descricao: String,           // o que está sendo avaliado
    val exemplo: String,             // exemplo exibido no campo de descrição
    val limiteFotos: Int = LIMITE_FOTOS_PADRAO
)

/** Estado de um cartão. Fica no ViewModel: sobrevive à rotação e à rolagem da lista. */
class RecursoEstado(
    var classificacao: Classificacao? = null,
    var descricao: String = "",
    var expandido: Boolean = false,
    val fotos: MutableList<FotoAnexo> = mutableListOf()
) {
    val avaliado: Boolean get() = classificacao != null
}

class AvaliacaoViewModel : ViewModel() {
    val estados: Map<String, RecursoEstado> = RECURSOS.associate { it.chave to RecursoEstado() }
}

/**
 * A avaliação geral por estrelas foi removida, mas a tabela tb_avaliacao ainda exige "nota"
 * e o mapa usa essa nota para colorir os pinos. Por isso a nota é calculada automaticamente:
 * média dos pontos de cada classificação (Adequado 5, Parcial 3, Inadequado 1, Não possui 1).
 * Para mudar os pesos, edite "pontos" no enum Classificacao.
 */
fun Map<String, RecursoEstado>.notaDerivada(): Float? {
    val pontos = values.mapNotNull { it.classificacao?.pontos }
    if (pontos.isEmpty()) return null
    return (Math.round(pontos.average() * 10) / 10.0).toFloat()
}

private fun r(
    chave: String, titulo: String, emoji: String, categoria: CategoriaRecurso,
    descricao: String, exemplo: String
) = RecursoDef(chave, titulo, emoji, categoria, descricao, exemplo)

/**
 * ATENÇÃO: confira se cada "chave" é exatamente o nome do campo no seu banco.
 * As 10 primeiras chaves de cada tabela existente já eram usadas no app; as demais foram inferidas.
 */
val RECURSOS = listOf(
    // ---- Acesso e circulação ----
    r("rampa", "Rampa de acesso", "♿", CategoriaRecurso.ACESSO,
        "Rampa que permite entrar ou mudar de nível sem usar degraus.",
        "A rampa é muito inclinada."),
    r("elevador", "Elevador", "🛗", CategoriaRecurso.ACESSO,
        "Elevador com espaço para cadeira de rodas e botões de fácil alcance.",
        "O elevador é estreito para cadeira de rodas."),
    r("entrada_acessivel", "Entrada acessível", "🚪", CategoriaRecurso.ACESSO,
        "Entrada principal sem degraus, com passagem livre e porta fácil de abrir.",
        "Há um degrau na entrada."),
    r("corrimao", "Corrimão", "🤝", CategoriaRecurso.ACESSO,
        "Barras de apoio em rampas e escadas, com altura e firmeza adequadas.",
        "Existe corrimão apenas de um lado."),
    r("portas_largas", "Portas largas", "↔️", CategoriaRecurso.ACESSO,
        "Portas com largura suficiente para a passagem de uma cadeira de rodas.",
        "As portas internas são estreitas."),
    r("piso_antiderrapante", "Piso antiderrapante", "👟", CategoriaRecurso.ACESSO,
        "Piso firme e que não escorrega, mesmo quando está molhado.",
        "O piso fica escorregadio quando chove."),
    r("mobiliario_adaptado", "Mobiliário adaptado", "🪑", CategoriaRecurso.ACESSO,
        "Mesas, cadeiras e bancadas com altura e espaço adequados para todas as pessoas.",
        "As mesas não têm espaço para cadeira de rodas."),
    r("acesso_guia", "Acesso com cão-guia", "🐕", CategoriaRecurso.ACESSO,
        "Permissão e boas condições para entrar acompanhado de cão-guia.",
        "A equipe permitiu a entrada com o cão-guia."),

    // ---- Banheiros e atendimento ----
    r("banheiro_acessivel", "Banheiro acessível", "🚻", CategoriaRecurso.BANHEIROS,
        "Banheiro com espaço para manobra, porta larga e barras de apoio.",
        "Não há barras de apoio."),
    r("banheiro_adaptado", "Banheiro adaptado", "🚽", CategoriaRecurso.BANHEIROS,
        "Vaso, pia e acessórios em altura adequada para pessoas com mobilidade reduzida.",
        "A pia é alta demais."),
    r("atendimento_prioritario", "Atendimento prioritário", "🙋", CategoriaRecurso.BANHEIROS,
        "Fila ou atendimento preferencial para quem precisa.",
        "O atendimento prioritário é bem sinalizado e rápido."),
    r("balcao_acessivel", "Balcão acessível", "🛎️", CategoriaRecurso.BANHEIROS,
        "Balcão ou caixa em altura que permite o atendimento de quem usa cadeira de rodas.",
        "O balcão é alto demais."),

    // ---- Orientação e comunicação ----
    r("piso_tatil", "Piso tátil", "🦯", CategoriaRecurso.ORIENTACAO,
        "Faixas no chão que orientam pessoas com deficiência visual.",
        "O piso tátil está interrompido."),
    r("mapa_tatil", "Mapa tátil", "🗺️", CategoriaRecurso.ORIENTACAO,
        "Mapa em relevo que mostra a planta do local.",
        "O mapa tátil fica na entrada e é fácil de encontrar."),
    r("sinalizacao", "Sinalização acessível", "🪧", CategoriaRecurso.ORIENTACAO,
        "Placas claras e fáceis de entender em todo o local.",
        "Faltam placas indicando o banheiro."),
    r("sinalizacao_visual", "Sinalização visual", "👁️", CategoriaRecurso.ORIENTACAO,
        "Placas com bom contraste, letras grandes e símbolos.",
        "As letras são pequenas e de baixo contraste."),
    r("sinalizacao_tatil", "Sinalização tátil", "✋", CategoriaRecurso.ORIENTACAO,
        "Placas com relevo ou Braille que podem ser lidas pelo toque.",
        "As placas das salas têm Braille."),
    r("sinalizacao_sonora", "Sinalização sonora", "🔊", CategoriaRecurso.ORIENTACAO,
        "Avisos por som, como o anúncio de andares no elevador.",
        "O elevador anuncia os andares em voz alta."),
    r("interprete_libras", "Intérprete de Libras", "🤟", CategoriaRecurso.ORIENTACAO,
        "Profissional ou recurso para atendimento em Língua Brasileira de Sinais.",
        "Não havia ninguém que soubesse Libras."),
    r("comunicacao_acessivel", "Comunicação acessível", "💬", CategoriaRecurso.ORIENTACAO,
        "Equipe e materiais que se comunicam de forma clara e adaptada a cada pessoa.",
        "A equipe explicou tudo com paciência."),
    r("informacoes_braille", "Informações em Braille", "📖", CategoriaRecurso.ORIENTACAO,
        "Cardápios, placas ou folhetos com texto em Braille.",
        "Só o cardápio tem versão em Braille."),

    // ---- Serviços e alimentação ----
    r("vaga_pcd", "Vaga PCD", "🅿️", CategoriaRecurso.SERVICOS,
        "Vaga reservada, larga e próxima da entrada.",
        "A vaga é estreita e fica longe da entrada."),
    r("cardapio_acessivel", "Cardápio acessível", "🍽️", CategoriaRecurso.SERVICOS,
        "Cardápio em Braille, com fonte ampliada, digital ou com leitura por QR Code.",
        "O cardápio digital funciona com leitor de tela."),
    r("iluminacao", "Iluminação adequada", "💡", CategoriaRecurso.SERVICOS,
        "Iluminação suficiente e uniforme, sem pontos escuros nos acessos.",
        "O corredor de entrada é escuro.")
)