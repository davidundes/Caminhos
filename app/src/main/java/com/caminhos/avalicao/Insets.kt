package com.caminhos.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Afasta o conteúdo da barra de status, da barra de navegação, do notch e do teclado.
 * Necessário porque o app usa enableEdgeToEdge() e, no Android 15 (targetSdk 35),
 * toda tela desenha por baixo das barras do sistema. Mantém o padding que a view já tinha.
 *
 * Uso (depois do setContentView):
 *   findViewById<View>(android.R.id.content).aplicarInsetsDoSistema()
 */
fun View.aplicarInsetsDoSistema() {
    val inicial = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val barras = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(
            inicial[0] + barras.left,
            inicial[1] + barras.top,
            inicial[2] + barras.right,
            inicial[3] + maxOf(barras.bottom, teclado.bottom)
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}