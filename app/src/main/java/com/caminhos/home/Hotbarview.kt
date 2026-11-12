package com.caminhos.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.caminhos.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Barra de navegação inferior (Avaliações · Home · Perfil), reutilizável nas três páginas.
 *
 * Uso no XML:  <com.caminhos.ui.HotbarView android:id="@+id/hotbar" ... />
 * Uso na Activity (onResume):  hotbar.sincronizar(Aba.HOME)
 */
class HotbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private class Tab(
        val aba: Aba,
        val raiz: View,
        val icone: ImageView,
        val rotulo: TextView,
        val caixa: View,      // caixa do ícone (recebe escala e elevação)
        val pill: View?,      // fundo gradiente suave (Avaliações/Perfil)
        val anel: View?       // anel amarelo (Home)
    )

    private val azul = Color.parseColor("#1769AA")
    private val cinza = Color.parseColor("#5B6B80")
    private val cores = ArgbEvaluator()
    private val negrito = BooleanArray(Aba.values().size)

    private val tabsContainer: View
    private val indicador: View
    private val tabs: List<Tab>

    private var animador: ValueAnimator? = null
    private var abaAtual: Aba? = null

    /** Se definido, substitui a navegação padrão (HotbarNavigator.abrir). */
    var aoSelecionar: ((Aba) -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false
        LayoutInflater.from(context).inflate(R.layout.view_hotbar, this, true)

        tabsContainer = findViewById(R.id.hotbarTabs)
        indicador = findViewById(R.id.hotbarIndicador)
        tabs = listOf(
            tab(Aba.AVALIACOES, R.id.tabAvaliacoes, R.id.iconAvaliacoes, R.id.labelAvaliacoes,
                R.id.caixaAvaliacoes, R.id.pillAvaliacoes, null),
            tab(Aba.HOME, R.id.tabHome, R.id.iconHome, R.id.labelHome,
                R.id.homeCirculo, null, R.id.homeAnel),
            tab(Aba.PERFIL, R.id.tabPerfil, R.id.iconPerfil, R.id.labelPerfil,
                R.id.caixaPerfil, R.id.pillPerfil, null)
        )

        tabs.forEach { t ->
            t.raiz.contentDescription = t.aba.rotulo
            t.raiz.setOnClickListener { aoTocar(t) }
            visual(t, 0f, 0f) // começa inativa; sincronizar() define a aba certa
        }
        indicador.alpha = 0f

        // Respeita a barra de navegação / gestos do Android (safe area).
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val embaixo = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, embaixo)
            insets
        }
    }

    private fun tab(
        aba: Aba, raizId: Int, iconeId: Int, rotuloId: Int, caixaId: Int, pillId: Int?, anelId: Int?
    ) = Tab(
        aba, findViewById(raizId), findViewById(iconeId), findViewById(rotuloId),
        findViewById(caixaId), pillId?.let { findViewById(it) }, anelId?.let { findViewById(it) }
    )

    // ------------------------------------ API pública ------------------------------------

    /**
     * Marca a aba da página atual. Anima SOMENTE se a página anterior era de outra aba
     * (não anima em rotação de tela, retorno de outra tela nem na primeira abertura).
     */
    fun sincronizar(aba: Aba) {
        val origem = HotbarNavigator.ultimaAba
        HotbarNavigator.ultimaAba = aba
        abaAtual = aba

        ancorar {
            animador?.cancel()
            indicador.alpha = 1f
            atualizarAcessibilidade(aba)
            if (origem != null && origem != aba && animacoesAtivas()) {
                aplicar(origem, origem, 1f)
                animar(origem, aba)
            } else {
                aplicar(aba, aba, 1f)
            }
        }
    }

    /**
     * Soma ao padding inferior do conteúdo a altura da barra, para ela nunca cobrir a página.
     * Chame uma vez no onCreate.
     */
    fun reservarEspacoEm(conteudo: View) {
        val base = conteudo.paddingBottom
        val ajustar = {
            val alvo = base + height
            if (conteudo.paddingBottom != alvo) {
                conteudo.setPadding(
                    conteudo.paddingLeft, conteudo.paddingTop, conteudo.paddingRight, alvo
                )
            }
        }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> ajustar() }
        post { ajustar() }
    }

    // ------------------------------------ Navegação ------------------------------------

    private fun aoTocar(tab: Tab) {
        if (tab.aba == abaAtual) return // já está nesta página: nada a fazer
        tab.raiz.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        val personalizado = aoSelecionar
        if (personalizado != null) {
            personalizado(tab.aba)
        } else {
            context.encontrarActivity()?.let { HotbarNavigator.abrir(it, tab.aba) }
        }
    }

    // ------------------------------------ Animação ------------------------------------

    private fun animar(de: Aba, para: Aba) {
        animador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = AnimationUtils.loadInterpolator(
                context, android.R.interpolator.fast_out_slow_in
            )
            addUpdateListener { aplicar(de, para, it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = aplicar(para, para, 1f)
            })
            start()
        }
    }

    /** t = 0: tudo como em "de". t = 1: tudo como em "para". */
    private fun aplicar(de: Aba, para: Aba, t: Float) {
        val pulso = if (de != para) sin(PI * t).toFloat() else 0f

        tabs.forEach { tab ->
            val p = when (tab.aba) {
                para -> t
                de -> 1f - t
                else -> 0f
            }
            visual(tab, p, if (tab.aba == para) pulso else 0f)
        }

        val x = centro(de) + (centro(para) - centro(de)) * t
        indicador.translationX = x - indicador.width / 2f
        indicador.scaleX = 1f + 0.35f * pulso // "estica" levemente durante o deslize
    }

    /** p = 0 (inativa) .. 1 (ativa). pulso = pequeno "pop" na aba de destino. */
    private fun visual(tab: Tab, p: Float, pulso: Float) {
        val cor = cores.evaluate(p, cinza, azul) as Int

        if (tab.aba == Aba.HOME) {
            val escala = 1f + 0.10f * p + 0.06f * pulso
            tab.caixa.scaleX = escala
            tab.caixa.scaleY = escala
            tab.caixa.translationY = -dp(4f) * p
            tab.caixa.elevation = dp(4f + 6f * p)
            tab.anel?.alpha = p
        } else {
            val escala = 1f + 0.12f * p + 0.08f * pulso
            tab.caixa.scaleX = escala
            tab.caixa.scaleY = escala
            tab.caixa.translationY = -dp(3f) * p
            tab.icone.imageTintList = ColorStateList.valueOf(cor)
            tab.pill?.alpha = p
        }

        tab.rotulo.setTextColor(cor)
        val ativo = p > 0.5f
        if (negrito[tab.aba.indice] != ativo) {
            negrito[tab.aba.indice] = ativo
            tab.rotulo.setTypeface(null, if (ativo) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun centro(aba: Aba): Float = tabsContainer.width * (aba.indice + 0.5f) / tabs.size

    private fun atualizarAcessibilidade(selecionada: Aba) {
        tabs.forEach {
            val ativa = it.aba == selecionada
            it.raiz.isSelected = ativa
            ViewCompat.setStateDescription(it.raiz, if (ativa) "Página atual" else null)
        }
    }

    /** Respeita "remover animações" do Android (escala de animação = 0). */
    private fun animacoesAtivas(): Boolean =
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f

    /** Executa o bloco quando a barra já tiver largura (precisamos dela para posicionar o indicador). */
    private fun ancorar(bloco: () -> Unit) {
        if (tabsContainer.width > 0) bloco() else doOnLayout { bloco() }
    }

    private fun dp(valor: Float) = valor * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        animador?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }
}

private fun Context.encontrarActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}