package com.caminhos.database

import com.caminhos.database.SupabaseClient.client
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Nenhuma tabela, coluna ou bucket novo:
 * - nome e CPF: colunas que já existem na tb_usuario;
 * - e-mail e biografia: dados da própria conta no Supabase Auth (user_metadata);
 * - foto: guardada no aparelho (veja AvatarLocal).
 */
class PerfilRepository {

    /** Biografia = campo "bio" dos metadados da conta. Só o próprio usuário logado consegue ler/alterar. */
    suspend fun getBio(): String {
        val usuario = runCatching { client.auth.retrieveUserForCurrentSession(updateSession = true) }
            .getOrNull() ?: client.auth.currentUserOrNull()
        return runCatching { usuario?.userMetadata?.get("bio")?.jsonPrimitive?.contentOrNull }
            .getOrNull().orEmpty()
    }

    suspend fun salvarBio(bio: String) {
        client.auth.updateUser { data { put("bio", bio) } }
    }

    /** Atualiza só nome e CPF na tb_usuario (as outras colunas ficam intactas). */
    suspend fun atualizarNomeCpf(idUsuario: String, nome: String, cpf: String) {
        val atualizadas = client.from("tb_usuario")
            .update({
                set("nome", nome)
                set("cpf", cpf)
            }) {
                select()
                filter { eq("id_usuario", idUsuario) }
            }
            .decodeList<JsonObject>()
        // Se a política de segurança (RLS) bloquear, o Supabase não dá erro: só devolve 0 linhas.
        require(atualizadas.isNotEmpty()) { "Sem permissão para atualizar o cadastro" }
    }

    /** O Supabase Auth envia um link de confirmação para o novo e-mail. */
    suspend fun atualizarEmail(novoEmail: String) {
        client.auth.updateUser { email = novoEmail }
    }
}