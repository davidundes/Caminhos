package com.caminhos.local

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.caminhos.R
import com.caminhos.avaliacao.AvaliacaoActivity
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import com.caminhos.usuario.SessaoUsuario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.caminhos.database.Repository.LocalRepository

// Classe que cuida das informações do local

class localInfoWindow(mapView: MapView) : InfoWindow(R.layout.localcreate, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return

        val bntExcluir = mView.findViewById<Button>(R.id.buttonExcluir)
        // Verifica se o usuario é admin para mostrar a opção
        bntExcluir.visibility = if (SessaoUsuario.isAdmin) View.VISIBLE else View.GONE

        val titulo = mView.findViewById<TextView>(R.id.infoTitulo)
        val endereco = mView.findViewById<TextView>(R.id.infoEndereco)
        val horario = mView.findViewById<TextView>(R.id.infoHorario)
        val bntAvaliar = mView.findViewById<Button>(R.id.buttonAvaliar)
        val bntDetalhes = mView.findViewById<Button>(R.id.buttonDetalhes)

        titulo.text = marker.title
        endereco.text = marker.snippet
        horario.text = marker.subDescription

        // Botão, título e endereço abrem a tela de detalhes (info completa + avaliações dos usuários)
        val abrirDetalhes = View.OnClickListener {
            val idLocal = marker.relatedObject as? Int ?: return@OnClickListener
            val context = mView.context
            context.startActivity(
                Intent(context, DetalhesLocalActivity::class.java).putExtra("id_local", idLocal)
            )
            close()
        }
        bntDetalhes.setOnClickListener(abrirDetalhes)
        titulo.setOnClickListener(abrirDetalhes)
        endereco.setOnClickListener(abrirDetalhes)

        bntAvaliar.setOnClickListener {
            val idLocal = marker.relatedObject as? Int
            if (idLocal != null) {
                val context = mView.context
                val intent = Intent(context, AvaliacaoActivity::class.java)
                intent.putExtra("id_local", idLocal)
                context.startActivity(intent)
                close()
            }
        }

        bntExcluir.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val idLocal = marker.relatedObject as? Int
                if (idLocal != null) {
                    val ok = LocalRepository().deleteLocal(idLocal)
                    if (ok) {
                        close()
                        mapView.overlays.remove(marker)
                        mapView.invalidate()
                    }
                }
            }
        }
    }

    override fun onClose() {

    }
}