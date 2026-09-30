package com.mobbacs.local

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mobbacs.R
import com.mobbacs.database.Repository
import com.mobbacs.models.Local
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class LocalActivity : AppCompatActivity() {

    private val localRepository = Repository.LocalRepository()
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_insercao)

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)
        map.controller.setCenter(GeoPoint(-23.3053, -45.985227))

        val btnConfirmar = findViewById<Button>(R.id.btnConfirmar)

        btnConfirmar.setOnClickListener {
            val coordenadas = catchCord()

            setContentView(R.layout.activity_local)

            val nome = findViewById<EditText>(R.id.editTextNomeEndereco)
            val endereco = findViewById<EditText>(R.id.editTextEndereco)
            val cep = findViewById<EditText>(R.id.editTextCep)
            val telefone = findViewById<EditText>(R.id.editTextTelefone)
            val horario = findViewById<EditText>(R.id.editTextHorario)
            val btnEnviar = findViewById<Button>(R.id.button)

            btnEnviar.setOnClickListener {
                val local = Local(
                    nome = nome.text.toString(),
                    endereco = endereco.text.toString(),
                    cep = cep.text.toString(),
                    telefone = telefone.text.toString(),
                    horario = horario.text.toString(),
                    latitude = coordenadas.first,
                    longitude = coordenadas.second
                )

                lifecycleScope.launch {
                    try {
                        localRepository.createLocal(local)
                        Toast.makeText(this@LocalActivity, "Local cadastrado!", Toast.LENGTH_SHORT).show()
                        finish()
                    } catch (e: Exception) {
                        Log.e("LocalActivity", "Erro ao criar local", e)
                        Toast.makeText(this@LocalActivity, "Erro ao salvar o local", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun catchCord(): Pair<Double, Double> {
        val geoPoint = map.projection.fromPixels(map.width / 2, map.height / 2)
        return Pair(geoPoint.latitude, geoPoint.longitude)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}