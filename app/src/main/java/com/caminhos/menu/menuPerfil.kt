package com.caminhos.menu

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.transition.TransitionManager
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.caminhos.R
import com.caminhos.avaliacao.ImagemUtil
import com.caminhos.database.PerfilRepository
import com.caminhos.database.Repository.UserRepository
import com.caminhos.database.SupabaseClient
import com.caminhos.login.LoginActivity
import com.caminhos.ui.Aba
import com.caminhos.ui.HotbarNavigator
import com.caminhos.ui.HotbarView
import com.caminhos.usuario.ApagarSessao
import com.caminhos.usuario.CarregarSessao
import com.caminhos.usuario.SessaoUsuario
import com.caminhos.util.AvatarLocal
import com.caminhos.util.CpfMaskWatcher
import com.caminhos.util.LIMITE_BIO
import com.caminhos.util.cpfValido
import com.caminhos.util.formatarCpf
import com.caminhos.util.mascararCpf
import com.caminhos.util.soDigitos
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.io.File

class menuPerfil : AppCompatActivity() {

    private val userRepository = UserRepository()
    private val perfilRepository = PerfilRepository()
    private val vm: PerfilViewModel by viewModels()

    private lateinit var rootPerfil: View
    private lateinit var scroll: NestedScrollView
    private lateinit var conteudo: LinearLayout
    private lateinit var progresso: LinearProgressIndicator
    private lateinit var hotbar: HotbarView

    private lateinit var avatarContainer: View
    private lateinit var imgFoto: ImageView
    private lateinit var btnAlterarFoto: View
    private lateinit var textNome: TextView
    private lateinit var textEmail: TextView
    private lateinit var textQtd: TextView

    private lateinit var campoNome: CampoPerfil
    private lateinit var campoEmail: CampoPerfil
    private lateinit var campoCpf: CampoPerfil

    private lateinit var textBio: TextView
    private lateinit var inputBioLayout: TextInputLayout
    private lateinit var inputBio: TextInputEditText

    private lateinit var acoesVisualizacao: View
    private lateinit var acoesEdicao: View
    private lateinit var btnSalvar: MaterialButton
    private lateinit var divisorSair: View
    private lateinit var btnSair: MaterialButton

    private lateinit var voltarDaEdicao: OnBackPressedCallback
    private var alturaTeclado = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ocultarCpf = Runnable { definirCpfVisivel(false) }

    // ---------- Activity Result APIs (sem pedir permissão de câmera/galeria) ----------

    private val capturaFoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
            val arquivo = vm.arquivoCamera
            vm.arquivoCamera = null
            if (sucesso && arquivo != null) {
                prepararFotoEscolhida(Uri.fromFile(arquivo), arquivo)
            } else {
                arquivo?.delete()
            }
        }

    private val seletorGaleria =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) prepararFotoEscolhida(uri, null)
        }

    // ------------------------------------ Ciclo de vida ------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.perfil)

        vincularViews()
        configurarInsets()
        configurarCampos()
        configurarBotoes()

        // Voltar: durante a edição cancela a edição; fora dela leva à Home.
        HotbarNavigator.configurarVoltar(this)
        voltarDaEdicao = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = cancelarEdicao()
        }
        onBackPressedDispatcher.addCallback(this, voltarDaEdicao)

        val dados = vm.dados
        if (dados != null) {
            renderizar()
            if (vm.editando) preencherCampos(dados, vm.rascunho)
            aplicarModo(animar = false)
        } else {
            aplicarModo(animar = false)
            carregar()
        }
    }

    override fun onResume() {
        super.onResume()
        hotbar.sincronizar(Aba.PERFIL)
    }

    override fun onPause() {
        definirCpfVisivel(false) // não deixa o CPF aparecendo ao sair da tela
        super.onPause()
    }

    // ------------------------------------ Configuração ------------------------------------

    private fun vincularViews() {
        rootPerfil = findViewById(R.id.rootPerfil)
        scroll = findViewById(R.id.scrollPerfil)
        conteudo = findViewById(R.id.conteudoPerfil)
        progresso = findViewById(R.id.progressPerfil)
        hotbar = findViewById(R.id.hotbar)

        avatarContainer = findViewById(R.id.avatarContainer)
        imgFoto = findViewById(R.id.perfil_foto)
        btnAlterarFoto = findViewById(R.id.btnAlterarFoto)
        textNome = findViewById(R.id.perfil_nome)
        textEmail = findViewById(R.id.perfil_email)
        textQtd = findViewById(R.id.perfil_qtdAvalicoes)

        textBio = findViewById(R.id.perfil_bio)
        inputBioLayout = findViewById(R.id.inputBioLayout)
        inputBio = findViewById(R.id.inputBio)

        acoesVisualizacao = findViewById(R.id.acoesVisualizacao)
        acoesEdicao = findViewById(R.id.acoesEdicao)
        btnSalvar = findViewById(R.id.btnSalvarPerfil)
        divisorSair = findViewById(R.id.divisorSair)
        btnSair = findViewById(R.id.btnSair)
    }

    /** Status bar no cabeçalho; teclado e hotbar no espaço inferior da rolagem. */
    private fun configurarInsets() {
        val header = findViewById<View>(R.id.perfilHeader)
        val topoBase = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(rootPerfil) { _, insets ->
            header.updatePadding(top = topoBase + insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            alturaTeclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            ajustarEspacoInferior()
            insets
        }
        hotbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> ajustarEspacoInferior() }
    }

    private fun ajustarEspacoInferior() {
        // Com o teclado aberto a hotbar fica escondida atrás dele.
        val alvo = (if (alturaTeclado > 0) alturaTeclado else hotbar.height) + dp(16)
        if (scroll.paddingBottom != alvo) scroll.updatePadding(bottom = alvo)
    }

    private fun configurarCampos() {
        campoNome = CampoPerfil(findViewById(R.id.campoNome), R.drawable.ic_hotbar_perfil, "Nome completo")
        campoEmail = CampoPerfil(findViewById(R.id.campoEmail), R.drawable.ic_perfil_email, "E-mail")
        campoCpf = CampoPerfil(findViewById(R.id.campoCpf), R.drawable.ic_perfil_cpf, "CPF")

        campoNome.input.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        campoNome.input.filters = arrayOf(InputFilter.LengthFilter(100))

        campoEmail.input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        campoEmail.layout.helperText = "Se alterar, você precisará confirmar o novo e-mail."

        campoCpf.input.keyListener = DigitsKeyListener.getInstance("0123456789.-")
        campoCpf.input.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        campoCpf.input.filters = arrayOf(InputFilter.LengthFilter(14))
        campoCpf.input.addTextChangedListener(CpfMaskWatcher())
        campoCpf.layout.helperText = "Seu CPF é protegido e não aparece para outros usuários."

        campoCpf.acao.visibility = View.VISIBLE
        campoCpf.acao.setOnClickListener { definirCpfVisivel(!vm.cpfVisivel) }

        inputBio.isSaveEnabled = false
        inputBioLayout.counterMaxLength = LIMITE_BIO
        inputBio.filters = arrayOf(InputFilter.LengthFilter(LIMITE_BIO))
        inputBio.doAfterTextChanged { inputBioLayout.error = null }

        // Guarda o que o usuário digita (sobrevive à rotação da tela).
        campoNome.input.doAfterTextChanged { if (vm.editando) vm.rascunho["nome"] = it.toString() }
        campoEmail.input.doAfterTextChanged { if (vm.editando) vm.rascunho["email"] = it.toString() }
        campoCpf.input.doAfterTextChanged { if (vm.editando) vm.rascunho["cpf"] = it.toString() }
        inputBio.doAfterTextChanged { if (vm.editando) vm.rascunho["bio"] = it.toString() }
    }

    private fun configurarBotoes() {
        findViewById<MaterialButton>(R.id.btnEditarPerfil).setOnClickListener { entrarEdicao() }
        findViewById<MaterialButton>(R.id.btnCancelarEdicao).setOnClickListener { cancelarEdicao() }
        btnSalvar.setOnClickListener { salvar() }
        btnAlterarFoto.setOnClickListener { escolherOrigemDaFoto() }
        btnSair.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Sair da conta")
                .setMessage("Tem certeza que deseja sair?")
                .setPositiveButton("Sair") { _, _ -> sair() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ------------------------------------ Carregamento ------------------------------------

    private fun carregar() {
        lifecycleScope.launch {
            progresso.visibility = View.VISIBLE
            try {
                // Sempre o usuário autenticado: ninguém vê o perfil de outra pessoa.
                val authUser = SupabaseClient.client.auth.currentUserOrNull()
                if (authUser == null) {
                    aviso("Sessão não encontrada. Entre novamente.")
                    return@launch
                }
                val usuario = userRepository.getUser(authUser.id)
                val total = userRepository.getQtdAvaliacoes(authUser.id)

                vm.dados = PerfilCompleto(
                    id = authUser.id,
                    nome = usuario?.nome.orEmpty(),
                    email = authUser.email.orEmpty(),
                    cpf = soDigitos(usuario?.cpf),
                    bio = perfilRepository.getBio(),
                    qtdAvaliacoes = total
                )
                // A foto fica guardada no aparelho (nenhuma tabela nem bucket envolvido).
                vm.fotoAtual = AvatarLocal.carregar(this@menuPerfil, authUser.id)
                renderizar()
            } catch (e: Exception) {
                Log.e("menuPerfil", "Erro ao carregar perfil", e)
                aviso("Erro ao carregar o perfil.", "Tentar de novo" to { carregar() })
            } finally {
                progresso.visibility = View.GONE
            }
        }
    }

    // ------------------------------------ Exibição ------------------------------------

    private fun renderizar() {
        val d = vm.dados ?: return
        val nome = d.nome.ifBlank { "Sem nome" }
        val email = d.email.ifBlank { "—" }

        textNome.text = nome
        textEmail.text = email
        textQtd.text = if (d.qtdAvaliacoes == 1) "1 avaliação" else "${d.qtdAvaliacoes} avaliações"

        campoNome.mostrarValor(nome)
        campoEmail.mostrarValor(email)
        atualizarCpf()

        val temBio = d.bio.isNotBlank()
        textBio.text = if (temBio) d.bio
        else "Você ainda não escreveu nada. Toque em Editar perfil para contar um pouco sobre você."
        textBio.setTextColor(if (temBio) 0xFF1B2A41.toInt() else 0xFF5B6B80.toInt())
        textBio.setTypeface(null, if (temBio) Typeface.NORMAL else Typeface.ITALIC)

        mostrarAvatar()
    }

    private fun mostrarAvatar() {
        val escolhida = vm.novaFoto?.let { ImagemUtil.carregarMiniatura(it, 512) }
        val bitmap = when {
            escolhida != null -> escolhida
            vm.removerFoto -> null
            else -> vm.fotoAtual
        }
        if (bitmap != null) {
            imgFoto.setBackgroundResource(R.drawable.bg_avatar)
            imgFoto.imageTintList = null
            imgFoto.scaleType = ImageView.ScaleType.CENTER_CROP
            imgFoto.setPadding(0, 0, 0, 0)
            imgFoto.setImageBitmap(bitmap)
            imgFoto.contentDescription = "Foto de perfil de ${vm.dados?.nome.orEmpty()}".trim()
        } else {
            imgFoto.setBackgroundResource(R.drawable.bg_avatar_padrao)
            imgFoto.setImageResource(R.drawable.ic_hotbar_perfil) // imagem padrão
            imgFoto.imageTintList = ColorStateList.valueOf(Color.WHITE)
            imgFoto.scaleType = ImageView.ScaleType.FIT_CENTER
            imgFoto.setPadding(dp(30), dp(30), dp(30), dp(30))
            imgFoto.contentDescription = "Foto de perfil padrão"
        }
    }

    // CPF: sempre mascarado; "Mostrar" revela por 8 segundos.
    private fun definirCpfVisivel(visivel: Boolean) {
        vm.cpfVisivel = visivel
        handler.removeCallbacks(ocultarCpf)
        if (visivel) handler.postDelayed(ocultarCpf, 8000)
        if (vm.dados != null) atualizarCpf()
    }

    private fun atualizarCpf() {
        val digitos = vm.dados?.cpf.orEmpty()
        val texto = if (vm.cpfVisivel) formatarCpf(digitos) else mascararCpf(digitos)
        campoCpf.mostrarValor(texto, if (vm.cpfVisivel) texto else "oculto")
        campoCpf.acao.text = if (vm.cpfVisivel) "Ocultar" else "Mostrar"
        campoCpf.acao.contentDescription = if (vm.cpfVisivel) "Ocultar CPF" else "Mostrar CPF"
    }

    // ------------------------------------ Modos ------------------------------------

    private fun entrarEdicao() {
        val d = vm.dados ?: return
        definirCpfVisivel(false)
        vm.editando = true
        vm.novaFoto = null
        vm.removerFoto = false
        vm.rascunho.clear()
        preencherCampos(d, null)
        aplicarModo(animar = true)
        campoNome.input.requestFocus()
    }

    private fun preencherCampos(d: PerfilCompleto, rascunho: Map<String, String>?) {
        campoNome.input.setText(rascunho?.get("nome") ?: d.nome)
        campoEmail.input.setText(rascunho?.get("email") ?: d.email)
        campoCpf.input.setText(rascunho?.get("cpf") ?: formatarCpf(d.cpf))
        inputBio.setText(rascunho?.get("bio") ?: d.bio)
        limparErros()
    }

    private fun cancelarEdicao() {
        // Restaura os dados anteriores: descarta a foto nova e o que foi digitado.
        vm.novaFoto?.let { ImagemUtil.excluir(it) }
        vm.novaFoto = null
        vm.removerFoto = false
        vm.editando = false
        vm.rascunho.clear()
        limparErros()
        mostrarAvatar()
        aplicarModo(animar = true)
    }

    private fun aplicarModo(animar: Boolean) {
        if (animar) TransitionManager.beginDelayedTransition(conteudo)
        val e = vm.editando

        campoNome.modoEdicao(e)
        campoEmail.modoEdicao(e)
        campoCpf.modoEdicao(e)
        textBio.visibility = if (e) View.GONE else View.VISIBLE
        inputBioLayout.visibility = if (e) View.VISIBLE else View.GONE

        btnAlterarFoto.visibility = if (e) View.VISIBLE else View.GONE
        acoesVisualizacao.visibility = if (e) View.GONE else View.VISIBLE
        acoesEdicao.visibility = if (e) View.VISIBLE else View.GONE
        divisorSair.visibility = if (e) View.GONE else View.VISIBLE
        btnSair.visibility = if (e) View.GONE else View.VISIBLE
        voltarDaEdicao.isEnabled = e
    }

    private fun limparErros() {
        campoNome.layout.error = null
        campoEmail.layout.error = null
        campoCpf.layout.error = null
        inputBioLayout.error = null
    }

    // ------------------------------------ Foto ------------------------------------

    private fun escolherOrigemDaFoto() {
        val temFoto = vm.novaFoto != null || (vm.fotoAtual != null && !vm.removerFoto)
        val opcoes = mutableListOf("Tirar foto", "Escolher da galeria")
        if (temFoto) opcoes += "Remover foto"

        MaterialAlertDialogBuilder(this)
            .setTitle("Foto de perfil")
            .setItems(opcoes.toTypedArray()) { _, i ->
                when (i) {
                    0 -> tirarFoto()
                    1 -> seletorGaleria.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    else -> {
                        vm.novaFoto?.let { ImagemUtil.excluir(it) }
                        vm.novaFoto = null
                        vm.removerFoto = true
                        mostrarAvatar()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun tirarFoto() {
        val arquivo = ImagemUtil.criarArquivoCamera(this)
        vm.arquivoCamera = arquivo
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivo)
            capturaFoto.launch(uri)
        } catch (e: Exception) {
            Log.e("menuPerfil", "Erro ao abrir a câmera", e)
            aviso("Não foi possível abrir a câmera.")
        }
    }

    /** Reduz a imagem para 512 px (e remove metadados/GPS). Só é gravada ao tocar em Salvar. */
    private fun prepararFotoEscolhida(origem: Uri, arquivoTemporario: File?) {
        lifecycleScope.launch {
            val preparada = ImagemUtil.prepararImagem(this@menuPerfil, origem, 512)
            arquivoTemporario?.delete()
            if (preparada == null) {
                aviso("Não foi possível carregar a imagem.")
                return@launch
            }
            vm.novaFoto?.let { ImagemUtil.excluir(it) }
            vm.novaFoto = preparada
            vm.removerFoto = false
            mostrarAvatar()
        }
    }

    // ------------------------------------ Salvar ------------------------------------

    private val regexNome = Regex("^[\\p{L}][\\p{L} .'’-]+$")

    private fun validar(nome: String, email: String, cpf: String, bio: String): Boolean {
        limparErros()
        var primeiro: View? = null
        fun erro(layout: TextInputLayout, campo: View, mensagem: String) {
            layout.error = mensagem
            if (primeiro == null) primeiro = campo
        }

        if (nome.length < 3 || nome.split(" ").size < 2 || !regexNome.matches(nome)) {
            erro(campoNome.layout, campoNome.input, "Informe nome e sobrenome (apenas letras).")
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            erro(campoEmail.layout, campoEmail.input, "Informe um e-mail válido.")
        }
        if (!cpfValido(cpf)) {
            erro(campoCpf.layout, campoCpf.input, "CPF inválido. Confira os 11 números.")
        }
        if (bio.length > LIMITE_BIO) {
            erro(inputBioLayout, inputBio, "Use no máximo $LIMITE_BIO caracteres.")
        }
        primeiro?.requestFocus()
        return primeiro == null
    }

    private fun salvar() {
        val atual = vm.dados ?: return
        val nome = campoNome.input.text.toString().trim().replace(Regex("\\s+"), " ")
        val email = campoEmail.input.text.toString().trim().lowercase()
        val cpf = soDigitos(campoCpf.input.text.toString())
        val bio = inputBio.text.toString().trim()

        if (!validar(nome, email, cpf, bio)) return

        btnSalvar.isEnabled = false
        btnSalvar.text = "Salvando..."
        progresso.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val id = atual.id
                val novaFoto = vm.novaFoto

                // 1) Nome e CPF: colunas que já existem na tb_usuario (só se mudaram).
                if (nome != atual.nome || cpf != atual.cpf) perfilRepository.atualizarNomeCpf(id, nome, cpf)

                // 2) Biografia: metadados da conta no Supabase Auth.
                if (bio != atual.bio) perfilRepository.salvarBio(bio)

                // 3) Foto: guardada no aparelho.
                if (novaFoto != null) {
                    AvatarLocal.salvar(this@menuPerfil, id, novaFoto)
                } else if (vm.removerFoto) {
                    AvatarLocal.remover(this@menuPerfil, id)
                }
                if (nome != atual.nome) CarregarSessao(id, nome, SessaoUsuario.isAdmin)

                // 4) E-mail: o Supabase manda um link de confirmação; só muda depois de confirmar.
                var emailPendente = false
                var erroEmail = false
                if (!email.equals(atual.email, ignoreCase = true)) {
                    try {
                        perfilRepository.atualizarEmail(email)
                        emailPendente = true
                    } catch (e: Exception) {
                        Log.e("menuPerfil", "Erro ao alterar o e-mail", e)
                        erroEmail = true
                    }
                }

                // Atualiza o estado em memória e volta ao modo de visualização.
                if (novaFoto != null) {
                    vm.fotoAtual = ImagemUtil.carregarMiniatura(novaFoto, 512)
                    ImagemUtil.excluir(novaFoto)
                } else if (vm.removerFoto) {
                    vm.fotoAtual = null
                }
                vm.dados = atual.copy(nome = nome, cpf = cpf, bio = bio)
                vm.novaFoto = null
                vm.removerFoto = false
                vm.editando = false
                vm.rascunho.clear()

                renderizar()
                aplicarModo(animar = true)
                confirmarSalvamento()
                when {
                    erroEmail -> aviso("Perfil salvo, mas não foi possível alterar o e-mail.")
                    emailPendente -> aviso("Perfil salvo. Confirme o novo e-mail pelo link enviado para $email.")
                    else -> aviso("✔ Alterações salvas!")
                }
            } catch (e: Exception) {
                Log.e("menuPerfil", "Erro ao salvar o perfil", e)
                val mensagem = e.message.orEmpty()
                if (mensagem.contains("duplicate", true) || mensagem.contains("23505")) {
                    campoCpf.layout.error = "Este CPF já está cadastrado."
                    campoCpf.input.requestFocus()
                } else {
                    aviso("Não foi possível salvar. Tente novamente.")
                }
            } finally {
                btnSalvar.isEnabled = true
                btnSalvar.text = "Salvar alterações"
                progresso.visibility = View.GONE
            }
        }
    }

    /** Pequeno "pulso" na foto como feedback visual (respeita a opção de remover animações). */
    private fun confirmarSalvamento() {
        avatarContainer.animate().scaleX(1.08f).scaleY(1.08f).setDuration(160).withEndAction {
            avatarContainer.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }.start()
    }

    // ------------------------------------ Sair ------------------------------------

    private fun sair() {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signOut()
            } catch (e: Exception) {
                Log.e("menuPerfil", "Erro ao sair", e)
            } finally {
                ApagarSessao()
                HotbarNavigator.ultimaAba = null
                startActivity(
                    Intent(this@menuPerfil, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }
    }

    // ------------------------------------ Utilitários ------------------------------------

    private fun aviso(mensagem: String, acao: Pair<String, () -> Unit>? = null) {
        val snackbar = Snackbar.make(rootPerfil, mensagem, Snackbar.LENGTH_LONG).setAnchorView(hotbar)
        if (acao != null) snackbar.setAction(acao.first) { acao.second() }
        snackbar.show()
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(ocultarCpf)
        if (isFinishing && !isChangingConfigurations) {
            vm.novaFoto?.let { ImagemUtil.excluir(it) }
        }
        super.onDestroy()
    }
}