package com.mobbacs.home

import android.content.Intent
import android.widget.Button
import android.widget.TextView
import com.mobbacs.R
import com.mobbacs.avaliacao.AvaliacaoActivity
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

class localInfoWindow(mapView: MapView) : InfoWindow(R.layout.localinfowindow, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return

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
            }
        }
    }

    override fun onClose() {
        // nada a limpar por enquanto
    }
}