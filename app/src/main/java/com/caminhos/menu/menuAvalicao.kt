package com.caminhos.menu

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.caminhos.R
import com.caminhos.database.Repository
import com.caminhos.database.SupabaseClient
import com.caminhos.models.Acessibilidade
import com.caminhos.ui.Aba
import com.caminhos.ui.HotbarNavigator
import com.caminhos.ui.HotbarView
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Cada recurso é texto: "Adequado", "Parcialmente adequado", "Inadequado",
 * "Não possui" ou null (não avaliado). O recurso "existe" no local quando
 * é adequado ou parcialmente adequado.
 */
private fun String?.ofereceRecurso(): Boolean =
    this == "Adequado" || this == "Parcialmente adequado"

class menuAvalicao {

    data class AvaliacaoItem(
        val local: String,
        val nota: Float?,   // null enquanto a trigger do banco não calculou a nota
        val data: String,
        val itens: List<String>
    )

    class MinhasAvaliacoesActivity : AppCompatActivity() {

        private val acessibilidadeRepository = Repository.AcessibilidadeRepository()
        private val localRepository = Repository.LocalRepository()
        private val adapter = AvaliacaoAdapter()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            setContentView(R.layout.minha_avaliacao)

            findViewById<RecyclerView>(R.id.listaAvaliacoes).apply {
                layoutManager = LinearLayoutManager(this@MinhasAvaliacoesActivity)
                adapter = this@MinhasAvaliacoesActivity.adapter
            }

            carregarAvaliacoes()

            HotbarNavigator.configurarVoltar(this)
            findViewById<HotbarView>(R.id.hotbar).reservarEspacoEm(findViewById(R.id.conteudoPagina))
        }

        private fun carregarAvaliacoes() {
            val textVazio = findViewById<TextView>(R.id.textVazio)

            lifecycleScope.launch {
                try {
                    val idUsuario = SupabaseClient.client.auth.currentUserOrNull()?.id
                        ?: return@launch

                    val avaliacoes = acessibilidadeRepository.getAvaliacoesByUsuario(idUsuario)
                    if (avaliacoes.isEmpty()) {
                        textVazio.visibility = View.VISIBLE
                        return@launch
                    }

                    val ids = avaliacoes.mapNotNull { it.id_avaliacao }
                    val acessPorAvaliacao = acessibilidadeRepository
                        .getAcessibilidadesByAvaliacoes(ids)
                        .associateBy { it.id_avaliacao }

                    val locais = localRepository.getAllLocais().associateBy { it.id_local }

                    val itens = avaliacoes.map { av ->
                        AvaliacaoItem(
                            local = locais[av.id_local]?.nome ?: "Local removido",
                            nota = av.nota,
                            data = formatarData(av.data),
                            itens = itensMarcados(acessPorAvaliacao[av.id_avaliacao])
                        )
                    }
                    adapter.submit(itens)

                } catch (e: Exception) {
                    Log.e("MinhasAvaliacoes", "Erro ao carregar avaliações", e)
                    Toast.makeText(
                        this@MinhasAvaliacoesActivity,
                        "Erro ao carregar avaliações",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        override fun onResume() {
            super.onResume()
            findViewById<HotbarView>(R.id.hotbar).sincronizar(Aba.AVALIACOES)
        }

        /** O banco devolve "2026-11-14T15:30:00.123": mostra só a data como dd/MM/yyyy. */
        private fun formatarData(data: String?): String {
            if (data.isNullOrBlank()) return ""
            val dia = data.take(10).split("-")
            return if (dia.size == 3) "${dia[2]}/${dia[1]}/${dia[0]}" else data
        }

        private fun itensMarcados(a: Acessibilidade?): List<String> {
            if (a == null) return emptyList()
            return listOf(
                "Rampa" to a.rampa,
                "Elevador" to a.elevador,
                "Banheiro acessível" to a.banheiro_acessivel,
                "Piso tátil" to a.piso_tatil,
                "Vaga PCD" to a.vaga_pcd,
                "Entrada acessível" to a.entrada_acessivel,
                "Corrimão" to a.corrimao,
                "Portas largas" to a.portas_largas,
                "Sinalização" to a.sinalizacao,
                "Iluminação" to a.iluminacao
            ).filter { it.second.ofereceRecurso() }.map { it.first }
        }
    }

    class AvaliacaoAdapter : RecyclerView.Adapter<AvaliacaoAdapter.VH>() {

        private var lista: List<AvaliacaoItem> = emptyList()

        fun submit(novos: List<AvaliacaoItem>) {
            lista = novos
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val local: TextView = view.findViewById(R.id.itemLocal)
            val nota: TextView = view.findViewById(R.id.itemNota)
            val data: TextView = view.findViewById(R.id.itemData)
            val checks: TextView = view.findViewById(R.id.itemChecks)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_avaliacao, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = lista[position]
            holder.local.text = item.local
            holder.nota.text = item.nota
                ?.let { "★ %.1f".format(Locale("pt", "BR"), it) }
                ?: "Sem nota"
            holder.data.text = item.data
            holder.data.visibility = if (item.data.isBlank()) View.GONE else View.VISIBLE
            holder.checks.text =
                if (item.itens.isEmpty()) "Nenhum item de acessibilidade marcado"
                else item.itens.joinToString("\n") { "✔ $it" }
        }

        override fun getItemCount() = lista.size
    }
}