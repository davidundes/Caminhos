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

class localInfoWindow(mapView: MapView) : InfoWindow(R.layout.localcreate, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return


        val bntExcluir = mView.findViewById<Button>(R.id.buttonExcluir)
        bntExcluir.visibility = if(SessaoUsuario.isAdmin) view.visibility else View.GONE

        val titulo = mView.findViewById<TextView>(R.id.infoTitulo)
        val endereco = mView.findViewById<TextView>(R.id.infoEndereco)
        val horario = mView.findViewById<TextView>(R.id.infoHorario)
        val bntAvaliar = mView.findViewById<Button>(R.id.buttonAvaliar)


        titulo.text = marker.title
        endereco.text = marker.snippet
        horario.text = marker.subDescription

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
                if(idLocal != null) {
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