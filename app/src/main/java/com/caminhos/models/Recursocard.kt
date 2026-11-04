package com.caminhos.avaliacao

import android.graphics.Color
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import com.caminhos.R
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout


class RecursoCard(
    private val raiz: View,
    private val def: RecursoDef,
    private val estado: RecursoEstado,
    private val aoMudar: () -> Unit
) {
    private val verde = Color.parseColor("#2E7D32")
    private val cinza = Color.parseColor("#5B6B80")
    private val borda = Color.parseColor("#E3E9F1")

    private val card = raiz as MaterialCardView
    private val header: View = raiz.findViewById(R.id.headerRecurso)
    private val seta: TextView = raiz.findViewById(R.id.textSeta)
    private val status: TextView = raiz.findViewById(R.id.textStatusRecurso)
    private val conteudo: View = raiz.findViewById(R.id.conteudoRecurso)
    private val toggle: MaterialButtonToggleGroup = raiz.findViewById(R.id.toggleExiste)
    private val secaoSim: View = raiz.findViewById(R.id.secaoSim)
    private val secaoNao: View = raiz.findViewById(R.id.secaoNao)
    private val secaoNaoSei: View = raiz.findViewById(R.id.secaoNaoSei)
    private val rating: RatingBar = raiz.findViewById(R.id.ratingRecurso)
    private val legenda: TextView = raiz.findViewById(R.id.textLegendaRecurso)
    private val descLayout: TextInputLayout = raiz.findViewById(R.id.inputDescricaoLayout)
    private val desc: TextInputEditText = raiz.findViewById(R.id.inputDescricao)
    private val obs: TextInputEditText = raiz.findViewById(R.id.inputObservacoes)
    private val checkFazFalta: MaterialCheckBox = raiz.findViewById(R.id.checkFazFalta)
    private val alternativa: TextInputEditText = raiz.findViewById(R.id.inputAlternativa)

    init {
        // Textos fixos do recurso
        raiz.findViewById<TextView>(R.id.textIcone).text = def.emoji
        raiz.findViewById<TextView>(R.id.textTituloRecurso).text = def.titulo
        raiz.findViewById<TextView>(R.id.textPerguntaNota).text = "Como você avalia ${def.nomeCurto}?"
        rating.contentDescription = "Nota para ${def.nomeCurto}, de 1 a 5"
        descLayout.hint = "Descreva ${def.nomeCurto}"
        descLayout.helperText = def.dica

        // Restaura o estado salvo (antes de registrar os listeners)
        when (estado.resposta) {
            Resposta.SIM -> toggle.check(R.id.btnSim)
            Resposta.NAO -> toggle.check(R.id.btnNao)
            Resposta.NAO_SEI -> toggle.check(R.id.btnNaoSei)
            null -> Unit
        }
        rating.rating = estado.nota.toFloat()
        desc.setText(estado.descricao)
        obs.setText(estado.observacoes)
        checkFazFalta.isChecked = estado.fazFalta
        alternativa.setText(estado.alternativa)
        atualizarLegenda()
        aplicarExpansao(animar = false)
        atualizarVisual()

        // Listeners
        header.setOnClickListener {
            estado.expandido = !estado.expandido
            aplicarExpansao(animar = true)
        }

        toggle.addOnButtonCheckedListener { _, id, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            estado.resposta = when (id) {
                R.id.btnSim -> Resposta.SIM
                R.id.btnNao -> Resposta.NAO
                else -> Resposta.NAO_SEI
            }
            TransitionManager.beginDelayedTransition(card)
            atualizarVisual()
            aoMudar()
        }

        rating.setOnRatingBarChangeListener { _, nota, doUsuario ->
            if (!doUsuario) return@setOnRatingBarChangeListener
            estado.nota = nota.toInt()
            atualizarLegenda()
            atualizarVisual()
            aoMudar()
        }

        desc.doAfterTextChanged { estado.descricao = it?.toString().orEmpty() }
        obs.doAfterTextChanged { estado.observacoes = it?.toString().orEmpty() }
        alternativa.doAfterTextChanged { estado.alternativa = it?.toString().orEmpty() }
        checkFazFalta.setOnCheckedChangeListener { _, marcado -> estado.fazFalta = marcado }
    }

    private fun atualizarLegenda() {
        legenda.text = if (estado.nota > 0) {
            "${estado.nota} · ${rotuloNota(estado.nota)}"
        } else {
            "1 = muito ruim · 5 = excelente"
        }
    }

    private fun aplicarExpansao(animar: Boolean) {
        if (animar) TransitionManager.beginDelayedTransition(card)
        conteudo.visibility = if (estado.expandido) View.VISIBLE else View.GONE
        seta.animate().rotation(if (estado.expandido) 180f else 0f).setDuration(200).start()
        ViewCompat.setStateDescription(header, if (estado.expandido) "Expandido" else "Recolhido")
    }

    private fun atualizarVisual() {
        secaoSim.visibility = if (estado.resposta == Resposta.SIM) View.VISIBLE else View.GONE
        secaoNao.visibility = if (estado.resposta == Resposta.NAO) View.VISIBLE else View.GONE
        secaoNaoSei.visibility = if (estado.resposta == Resposta.NAO_SEI) View.VISIBLE else View.GONE

        val textoStatus = when {
            estado.resposta == Resposta.SIM && estado.nota > 0 -> "Avaliado · ${estado.nota}/5"
            estado.resposta == Resposta.SIM -> "Falta dar a nota"
            estado.resposta == Resposta.NAO -> "Não existe no local"
            estado.resposta == Resposta.NAO_SEI -> "Sem avaliação"
            else -> "Toque para avaliar"
        }
        val avaliado = estado.avaliado
        status.text = if (avaliado) "✓ $textoStatus" else textoStatus
        status.setTextColor(if (avaliado) verde else cinza)
        header.contentDescription = "${def.titulo}. $textoStatus"

        card.strokeColor = if (avaliado) verde else borda
        card.strokeWidth = px(if (avaliado) 2 else 1)
    }

    private fun px(dp: Int) = (dp * raiz.resources.displayMetrics.density).toInt()
}