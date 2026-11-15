package com.caminhos.local

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.database.Repository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Cadastro de local em duas etapas, na mesma tela:
 *  1) escolher o ponto no mapa;  2) preencher os dados (formulário em cima do mapa).
 *
 * Colunas de tb_local: nome, endereco, cep, latitude, longitude (obrigatórias);
 * telefone, horario, horario_inicial, horario_final (opcionais).
 */
class LocalActivity : AppCompatActivity() {

    private val localRepository = Repository.LocalRepository()

    private lateinit var map: MapView
    private lateinit var painelFormulario: ScrollView
    private lateinit var btnConfirmar: Button
    private lateinit var btnCadastrar: Button

    private lateinit var editNome: EditText
    private lateinit var editEndereco: EditText
    private lateinit var editCep: EditText
    private lateinit var editTelefone: EditText
    private lateinit var editHorario: EditText

    private lateinit var erroNome: TextView
    private lateinit var erroEndereco: TextView
    private lateinit var erroCep: TextView
    private lateinit var erroTelefone: TextView
    private lateinit var erroHorario: TextView

    private lateinit var textLocalizacao: TextView
    private lateinit var textHoraAbre: TextView
    private lateinit var textHoraFecha: TextView
    private lateinit var buttonLimparHorario: TextView
    private lateinit var rowHorarios: View

    private var pontoEscolhido: GeoPoint? = null
    private var abre: Pair<Int, Int>? = null
    private var fecha: Pair<Int, Int>? = null
    private var enviando = false

    // Só fica ativo na etapa do formulário: o "voltar" do sistema volta para o mapa.
    private val voltarCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!enviando) voltarParaMapa()
        }
    }

    private data class DadosLocal(
        val nome: String,
        val endereco: String,
        val cep: String,
        val telefone: String?,
        val horario: String?,
        val horaInicial: String?,
        val horaFinal: String?,
        val latitude: Double,
        val longitude: Double
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("nome", nome)
            put("endereco", endereco)
            put("cep", cep)
            put("telefone", telefone)
            put("horario", horario)
            put("horario_inicial", horaInicial)
            put("horario_final", horaFinal)
            put("latitude", latitude)
            put("longitude", longitude)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_insercao)

        buscarViews()
        onBackPressedDispatcher.addCallback(this, voltarCallback)

        // Formulário acima da barra de status/navegação e do teclado
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootInsercao)) { _, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            painelFormulario.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        configurarMapa(savedInstanceState)
        configurarFormulario()
        restaurarEstado(savedInstanceState)
    }

    // ------------------------------------------------------------- setup

    private fun buscarViews() {
        map = findViewById(R.id.map)
        painelFormulario = findViewById(R.id.painelFormulario)
        btnConfirmar = findViewById(R.id.btnConfirmar)
        btnCadastrar = findViewById(R.id.buttonCadastrarLocal)

        editNome = findViewById(R.id.editTextNomeEndereco)
        editEndereco = findViewById(R.id.editTextEndereco)
        editCep = findViewById(R.id.editTextCep)
        editTelefone = findViewById(R.id.editTextTelefone)
        editHorario = findViewById(R.id.editTextHorario)

        erroNome = findViewById(R.id.erroNome)
        erroEndereco = findViewById(R.id.erroEndereco)
        erroCep = findViewById(R.id.erroCep)
        erroTelefone = findViewById(R.id.erroTelefone)
        erroHorario = findViewById(R.id.erroHorario)

        textLocalizacao = findViewById(R.id.textLocalizacaoEscolhida)
        textHoraAbre = findViewById(R.id.textHoraAbre)
        textHoraFecha = findViewById(R.id.textHoraFecha)
        buttonLimparHorario = findViewById(R.id.buttonLimparHorario)
        rowHorarios = findViewById(R.id.rowHorarios)
    }

    private fun configurarMapa(estado: Bundle?) {
        map.setMultiTouchControls(true)

        val centro = if (estado != null && estado.containsKey(ST_MAP_LAT)) {
            GeoPoint(estado.getDouble(ST_MAP_LAT), estado.getDouble(ST_MAP_LON))
        } else {
            GeoPoint(-23.3053, -45.985227)
        }
        val zoom = if (estado != null && estado.containsKey(ST_ZOOM)) estado.getDouble(ST_ZOOM) else 17.0

        map.controller.setZoom(zoom)
        map.controller.setCenter(centro)

        btnConfirmar.setOnClickListener {
            pontoEscolhido = catchCord()
            abrirFormulario()
        }
    }

    private fun configurarFormulario() {
        findViewById<View>(R.id.buttonVoltarMapa).setOnClickListener { if (!enviando) voltarParaMapa() }
        findViewById<View>(R.id.buttonAlterarNoMapa).setOnClickListener { if (!enviando) voltarParaMapa() }

        // Máscaras
        aplicarMascara(editCep, ::formatarCep)
        aplicarMascara(editTelefone, ::formatarTelefone)

        // Some o erro assim que a pessoa volta a digitar no campo
        limparErroAoDigitar(editNome, erroNome)
        limparErroAoDigitar(editEndereco, erroEndereco)
        limparErroAoDigitar(editCep, erroCep)
        limparErroAoDigitar(editTelefone, erroTelefone)
        limparErroAoDigitar(editHorario, erroHorario)

        // Horário de funcionamento
        textHoraAbre.setOnClickListener {
            escolherHora(abre, 8, 0) { abre = it }
        }
        textHoraFecha.setOnClickListener {
            escolherHora(fecha, 18, 0) { fecha = it }
        }
        buttonLimparHorario.setOnClickListener {
            abre = null
            fecha = null
            erroHorario.visibility = View.GONE
            atualizarHorariosNaTela()
        }

        btnCadastrar.setOnClickListener { enviar() }
    }

    private fun restaurarEstado(estado: Bundle?) {
        if (estado != null) {
            if (estado.containsKey(ST_PONTO_LAT)) {
                pontoEscolhido = GeoPoint(estado.getDouble(ST_PONTO_LAT), estado.getDouble(ST_PONTO_LON))
            }
            estado.getIntArray(ST_HORARIOS)?.let { h ->
                if (h.size == 4) {
                    if (h[0] >= 0) abre = h[0] to h[1]
                    if (h[2] >= 0) fecha = h[2] to h[3]
                }
            }
            if (estado.getBoolean(ST_FORMULARIO) && pontoEscolhido != null) abrirFormulario()
        }
        atualizarHorariosNaTela()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val centro = map.mapCenter
        outState.putDouble(ST_MAP_LAT, centro.latitude)
        outState.putDouble(ST_MAP_LON, centro.longitude)
        outState.putDouble(ST_ZOOM, map.zoomLevelDouble)
        pontoEscolhido?.let {
            outState.putDouble(ST_PONTO_LAT, it.latitude)
            outState.putDouble(ST_PONTO_LON, it.longitude)
        }
        outState.putBoolean(ST_FORMULARIO, painelFormulario.visibility == View.VISIBLE)
        outState.putIntArray(
            ST_HORARIOS,
            intArrayOf(abre?.first ?: -1, abre?.second ?: -1, fecha?.first ?: -1, fecha?.second ?: -1)
        )
    }

    // ------------------------------------------------------------ etapas

    private fun catchCord(): GeoPoint {
        val geoPoint = map.projection.fromPixels(map.width / 2, map.height / 2)
        return GeoPoint(geoPoint.latitude, geoPoint.longitude)
    }

    private fun abrirFormulario() {
        val ponto = pontoEscolhido ?: return
        textLocalizacao.text = String.format(
            Locale.US, "Localização escolhida: %.5f, %.5f", ponto.latitude, ponto.longitude
        )
        painelFormulario.visibility = View.VISIBLE
        controlesDoMapa(visivel = false)
        voltarCallback.isEnabled = true
    }

    private fun voltarParaMapa() {
        painelFormulario.visibility = View.GONE
        controlesDoMapa(visivel = true)
        voltarCallback.isEnabled = false
    }

    private fun controlesDoMapa(visivel: Boolean) {
        val v = if (visivel) View.VISIBLE else View.GONE
        findViewById<View>(R.id.textInstrucaoMapa).visibility = v
        findViewById<View>(R.id.centerIndicador).visibility = v
        btnConfirmar.visibility = v
    }

    // ---------------------------------------------------------- horários

    private fun escolherHora(
        atual: Pair<Int, Int>?,
        horaPadrao: Int,
        minutoPadrao: Int,
        aoEscolher: (Pair<Int, Int>) -> Unit
    ) {
        TimePickerDialog(
            this,
            { _, hora, minuto ->
                aoEscolher(hora to minuto)
                erroHorario.visibility = View.GONE
                atualizarHorariosNaTela()
            },
            atual?.first ?: horaPadrao,
            atual?.second ?: minutoPadrao,
            true
        ).show()
    }

    private fun atualizarHorariosNaTela() {
        mostrarHora(textHoraAbre, abre)
        mostrarHora(textHoraFecha, fecha)
        buttonLimparHorario.visibility = if (abre != null || fecha != null) View.VISIBLE else View.GONE
    }

    private fun mostrarHora(campo: TextView, hora: Pair<Int, Int>?) {
        if (hora == null) {
            campo.text = "Selecionar"
            campo.setTextColor(0xFF9AA7B8.toInt())
        } else {
            campo.text = formatarHora(hora)
            campo.setTextColor(0xFF1B2A41.toInt())
        }
    }

    private fun formatarHora(hora: Pair<Int, Int>): String =
        String.format(Locale.US, "%02d:%02d", hora.first, hora.second)

    // --------------------------------------------------------- validação

    private fun validar(): DadosLocal? {
        limparErros()

        var primeiroErro: View? = null
        fun falha(campo: View, erro: TextView, mensagem: String) {
            erro.text = mensagem
            erro.visibility = View.VISIBLE
            if (primeiroErro == null) primeiroErro = campo
        }

        val nome = editNome.text.toString().trim().replace(Regex("\\s+"), " ")
        val endereco = editEndereco.text.toString().trim().replace(Regex("\\s+"), " ")
        val cepDigitos = editCep.text.toString().filter { it.isDigit() }
        val telefoneDigitos = editTelefone.text.toString().filter { it.isDigit() }
        val horarioLivre = editHorario.text.toString().trim().replace(Regex("\\s+"), " ")

        if (nome.length < 2) {
            falha(editNome, erroNome, "Informe o nome do local (mínimo de 2 caracteres).")
        } else if (nome.length > MAX_NOME) {
            falha(editNome, erroNome, "O nome pode ter no máximo $MAX_NOME caracteres.")
        }

        if (endereco.length < 5) {
            falha(editEndereco, erroEndereco, "Informe o endereço (rua, número e bairro).")
        } else if (endereco.length > MAX_ENDERECO) {
            falha(editEndereco, erroEndereco, "O endereço pode ter no máximo $MAX_ENDERECO caracteres.")
        }

        if (cepDigitos.length != 8) {
            falha(editCep, erroCep, "Informe um CEP válido, com 8 números.")
        }

        if (telefoneDigitos.isNotEmpty() && !telefoneValido(telefoneDigitos)) {
            falha(editTelefone, erroTelefone, "Telefone inválido. Use DDD + número, ex.: (12) 99999-9999.")
        }

        val a = abre
        val f = fecha
        if ((a == null) != (f == null)) {
            falha(rowHorarios, erroHorario, "Informe a hora de abertura e a de fechamento.")
        } else if (a != null && a == f) {
            falha(rowHorarios, erroHorario, "Abertura e fechamento não podem ser no mesmo horário.")
        } else if (horarioLivre.length > MAX_HORARIO) {
            falha(editHorario, erroHorario, "As observações podem ter no máximo $MAX_HORARIO caracteres.")
        }

        val ponto = pontoEscolhido
        if (ponto == null || ponto.latitude !in -90.0..90.0 || ponto.longitude !in -180.0..180.0) {
            Toast.makeText(this, "Escolha o local no mapa primeiro.", Toast.LENGTH_SHORT).show()
            voltarParaMapa()
            return null
        }

        primeiroErro?.let {
            rolarAte(it)
            return null
        }

        // Se não escreveu observações, o texto de "horario" vira o intervalo (ex.: 08:00 às 18:00),
        // para o balão do mapa e a tela de detalhes continuarem mostrando o horário.
        val horarioTexto = when {
            horarioLivre.isNotEmpty() -> horarioLivre
            a != null && f != null -> "${formatarHora(a)} às ${formatarHora(f)}"
            else -> null
        }

        return DadosLocal(
            nome = nome,
            endereco = endereco,
            cep = formatarCep(cepDigitos),
            telefone = telefoneDigitos.takeIf { it.isNotEmpty() }?.let { formatarTelefone(it) },
            horario = horarioTexto,
            horaInicial = a?.let { String.format(Locale.US, "%02d:%02d:00", it.first, it.second) },
            horaFinal = f?.let { String.format(Locale.US, "%02d:%02d:00", it.first, it.second) },
            latitude = Math.round(ponto.latitude * 1_000_000.0) / 1_000_000.0,
            longitude = Math.round(ponto.longitude * 1_000_000.0) / 1_000_000.0
        )
    }

    private fun telefoneValido(digitos: String): Boolean {
        if (digitos.length != 10 && digitos.length != 11) return false
        val ddd = digitos.take(2).toInt()
        if (ddd < 11) return false
        // Celular (11 dígitos) começa com 9 depois do DDD
        return digitos.length == 10 || digitos[2] == '9'
    }

    private fun limparErros() {
        listOf(erroNome, erroEndereco, erroCep, erroTelefone, erroHorario).forEach {
            it.visibility = View.GONE
        }
    }

    private fun rolarAte(campo: View) {
        if (campo is EditText) campo.requestFocus()
        painelFormulario.post {
            painelFormulario.smoothScrollTo(0, (campo.top - dp(96)).coerceAtLeast(0))
        }
    }

    // ------------------------------------------------------------- envio

    private fun enviar() {
        if (enviando) return
        val dados = validar() ?: return

        definirEnviando(true)
        lifecycleScope.launch {
            // Se a checagem de duplicidade falhar (ex.: sem internet), segue e deixa o envio dar o erro.
            val parecido = runCatching { procurarSimilar(dados) }.getOrNull()
            if (parecido != null) {
                confirmarDuplicado(parecido, dados)
            } else {
                salvar(dados)
            }
        }
    }

    /** Devolve a mensagem de aviso se já existir um local igual/muito próximo. */
    private suspend fun procurarSimilar(dados: DadosLocal): String? {
        val novo = GeoPoint(dados.latitude, dados.longitude)
        val nomeNovo = normalizar(dados.nome)

        for (local in localRepository.getAllLocais()) {
            val lat = local.latitude?.toDouble() ?: continue
            val lon = local.longitude?.toDouble() ?: continue
            val nomeExistente = local.nome?.toString().orEmpty()
            val distancia = novo.distanceToAsDouble(GeoPoint(lat, lon))

            if (distancia <= 100 && normalizar(nomeExistente) == nomeNovo) {
                return "Já existe um local chamado \"$nomeExistente\" a cerca de ${distancia.roundToInt()} m deste ponto."
            }
            if (distancia <= 10) {
                return "Já existe um local (\"$nomeExistente\") praticamente no mesmo ponto do mapa."
            }
        }
        return null
    }

    private fun confirmarDuplicado(mensagem: String, dados: DadosLocal) {
        AlertDialog.Builder(this)
            .setTitle("Local parecido encontrado")
            .setMessage("$mensagem\n\nDeseja cadastrar mesmo assim?")
            .setPositiveButton("Cadastrar mesmo assim") { _, _ -> salvar(dados) }
            .setNegativeButton("Cancelar") { _, _ -> definirEnviando(false) }
            .setOnCancelListener { definirEnviando(false) }
            .show()
    }

    private fun salvar(dados: DadosLocal) {
        lifecycleScope.launch {
            try {
                localRepository.createLocal(dados.toJson())
                Toast.makeText(this@LocalActivity, "Local cadastrado!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Log.e("LocalActivity", "Erro ao criar local", e)
                Toast.makeText(this@LocalActivity, mensagemDeErro(e), Toast.LENGTH_LONG).show()
                definirEnviando(false)
            }
        }
    }

    private fun mensagemDeErro(e: Exception): String {
        val texto = e.message.orEmpty().lowercase()
        return when {
            "row-level security" in texto || "42501" in texto || "permission" in texto ->
                "Você não tem permissão para cadastrar locais."
            "unable to resolve host" in texto || "timeout" in texto || "network" in texto ->
                "Sem conexão com a internet. Verifique e tente novamente."
            else -> "Erro ao salvar o local. Tente novamente."
        }
    }

    private fun definirEnviando(valor: Boolean) {
        enviando = valor
        btnCadastrar.isEnabled = !valor
        btnCadastrar.text = if (valor) "Salvando..." else "Cadastrar local"
    }

    // ----------------------------------------------------------- helpers

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .trim()

    private fun formatarCep(digitos: String): String {
        val d = digitos.take(8)
        return if (d.length > 5) "${d.take(5)}-${d.drop(5)}" else d
    }

    private fun formatarTelefone(digitos: String): String {
        val d = digitos.take(11)
        if (d.isEmpty()) return ""
        if (d.length <= 2) return "($d"
        val ddd = d.take(2)
        val resto = d.drop(2)
        return when {
            resto.length <= 4 -> "($ddd) $resto"
            d.length == 11 -> "($ddd) ${resto.take(5)}-${resto.drop(5)}"
            else -> "($ddd) ${resto.take(4)}-${resto.drop(4)}"
        }
    }

    private fun aplicarMascara(campo: EditText, formatar: (String) -> String) {
        campo.addTextChangedListener(object : TextWatcher {
            private var ajustando = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (ajustando) return
                ajustando = true
                val atual = s?.toString().orEmpty()
                val novo = formatar(atual.filter { it.isDigit() })
                if (novo != atual) {
                    campo.setText(novo)
                    campo.setSelection(novo.length)
                }
                ajustando = false
            }
        })
    }

    private fun limparErroAoDigitar(campo: EditText, erro: TextView) {
        campo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (erro.visibility == View.VISIBLE) erro.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    private companion object {
        const val MAX_NOME = 100
        const val MAX_ENDERECO = 200
        const val MAX_HORARIO = 100

        const val ST_MAP_LAT = "map_lat"
        const val ST_MAP_LON = "map_lon"
        const val ST_ZOOM = "map_zoom"
        const val ST_PONTO_LAT = "ponto_lat"
        const val ST_PONTO_LON = "ponto_lon"
        const val ST_FORMULARIO = "etapa_formulario"
        const val ST_HORARIOS = "horarios"
    }
}