package com.caminhos.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import com.caminhos.R
import com.caminhos.home.HomeActivity
import com.caminhos.menu.menuAvalicao
import com.caminhos.menu.menuPerfil

/** As três páginas principais, na ordem em que aparecem na barra (Home sempre no centro). */
enum class Aba(val indice: Int, val rotulo: String) {
    AVALIACOES(0, "Avaliações"),
    HOME(1, "Home"),
    PERFIL(2, "Perfil")
}

object HotbarNavigator {

    /** Aba mostrada na última página principal. A barra da próxima página anima a partir dela. */
    @Volatile
    var ultimaAba: Aba? = null

    /**
     * Abre uma página principal sem criar cópias:
     * - Home: volta para a Home que já existe e fecha o que estiver acima dela (mapa preservado).
     * - Avaliações e Perfil: traz para a frente a instância existente, se houver.
     */
    fun abrir(origem: Activity, destino: Aba) {
        val classe = when (destino) {
            Aba.HOME -> HomeActivity::class.java
            Aba.AVALIACOES -> menuAvalicao.MinhasAvaliacoesActivity::class.java
            Aba.PERFIL -> menuPerfil::class.java
        }
        val intent = Intent(origem, classe).apply {
            flags = if (destino == Aba.HOME) {
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } else {
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        }
        origem.startActivity(intent)
        aplicarTransicao(origem)
    }

    /** Voltar (botão/gesto) em Avaliações e Perfil leva à Home, como em apps com barra inferior. */
    fun configurarVoltar(activity: ComponentActivity) {
        activity.onBackPressedDispatcher.addCallback(activity) {
            abrir(activity, Aba.HOME)
        }
    }

    private fun aplicarTransicao(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN, R.layout.hotbar_fade_in, R.layout.hotbar_hold
            )
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.layout.hotbar_fade_in, R.layout.hotbar_hold)
        }
    }
}