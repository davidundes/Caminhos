package com.caminhos.util

import android.text.Editable
import android.text.TextWatcher

const val LIMITE_BIO = 300   // limite de caracteres da biografia (configurável)

fun soDigitos(valor: String?): String = valor.orEmpty().filter { it.isDigit() }

/** 12345678900 -> 123.456.789-00 (também formata valores parciais enquanto o usuário digita). */
fun formatarCpf(digitos: String): String = buildString {
    digitos.forEachIndexed { i, c ->
        if (i == 3 || i == 6) append('.')
        if (i == 9) append('-')
        append(c)
    }
}

/** Mostra só os 2 últimos dígitos: •••.•••.•••-00 (para não expor o CPF sem necessidade). */
fun mascararCpf(digitos: String): String =
    if (digitos.length == 11) "•••.•••.•••-${digitos.takeLast(2)}" else "—"

/** Valida tamanho e dígitos verificadores. */
fun cpfValido(digitos: String): Boolean {
    if (digitos.length != 11 || digitos.all { it == digitos[0] }) return false

    fun digito(base: String, pesoInicial: Int): Int {
        val soma = base.mapIndexed { i, c -> (c - '0') * (pesoInicial - i) }.sum()
        val resto = (soma * 10) % 11
        return if (resto == 10) 0 else resto
    }
    return digito(digitos.substring(0, 9), 10) == digitos[9] - '0' &&
            digito(digitos.substring(0, 10), 11) == digitos[10] - '0'
}

/** Aplica a máscara 000.000.000-00 enquanto o usuário digita. */
class CpfMaskWatcher : TextWatcher {
    private var aplicando = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable?) {
        if (aplicando || s == null) return
        aplicando = true
        val formatado = formatarCpf(soDigitos(s.toString()).take(11))
        if (formatado != s.toString()) s.replace(0, s.length, formatado)
        aplicando = false
    }
}