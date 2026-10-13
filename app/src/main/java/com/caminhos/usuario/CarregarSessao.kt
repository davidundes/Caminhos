package com.caminhos.usuario

import com.caminhos.usuario.SessaoUsuario

fun CarregarSessao(id: String,nome: String?,adm: Boolean ){
    SessaoUsuario.nome = nome
    SessaoUsuario.isAdmin = adm
}
