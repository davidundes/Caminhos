package com.caminhos.usuario

import com.caminhos.usuario.SessaoUsuario


fun ApagarSessao(){
    SessaoUsuario.nome = null
    SessaoUsuario.isAdmin = false
}