package com.caminhos.database

import android.util.Log
import com.caminhos.database.SupabaseClient.client
import com.caminhos.models.Acessibilidade
import com.caminhos.avalicao.Avaliacao
import com.caminhos.avalicao.AvaliacaoCriada
import com.caminhos.models.Image
import com.caminhos.models.Local
import com.caminhos.avalicao.NovaAvaliacao
import com.caminhos.models.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object Repository {

    class AcessibilidadeRepository {

        /**
         * Grava as respostas de acessibilidade de uma avaliação.
         * O banco já cria uma linha vazia para cada avaliação nova (trigger trg_criar_acessibilidade),
         * então primeiro tenta ATUALIZAR essa linha; só insere se ela não existir.
         * A nota é recalculada pelo trigger de tb_acessibilidade (INSERT e UPDATE).
         */
        suspend fun createAcessibilidade(linha: JsonObject) {
            val idAvaliacao = requireNotNull(linha["id_avaliacao"]?.jsonPrimitive?.int) {
                "id_avaliacao é obrigatório"
            }

            val atualizadas = client
                .from("tb_acessibilidade")
                .update(linha) {
                    select()
                    filter { eq("id_avaliacao", idAvaliacao) }
                }
                .decodeList<AvaliacaoCriada>()

            if (atualizadas.isEmpty()) {
                client
                    .from("tb_acessibilidade")
                    .insert(linha)
            }
        }

        suspend fun createAcessibilidade(acessibilidade: Acessibilidade) {
            client
                .from("tb_acessibilidade")
                .insert(acessibilidade)
        }

        /** Linha bruta de tb_acessibilidade de uma avaliação (para preencher a tela de edição). */
        suspend fun getAcessibilidadeBruta(idAvaliacao: Int): JsonObject? {
            return client
                .from("tb_acessibilidade")
                .select {
                    filter { eq("id_avaliacao", idAvaliacao) }
                }
                .decodeSingleOrNull<JsonObject>()
        }

        suspend fun getAcessibilidadesByAvaliacoes(ids: List<Int>): List<Acessibilidade> {
            if (ids.isEmpty()) return emptyList()

            return client
                .from("tb_acessibilidade")
                .select {
                    filter {
                        isIn("id_avaliacao", ids)
                    }
                }
                .decodeList<Acessibilidade>()
        }

        suspend fun getAvaliacoesByLocal(idLocal: Int): List<Avaliacao> {
            return client
                .from("tb_avaliacao")
                .select {
                    filter { eq("id_local", idLocal) }
                }
                .decodeList<Avaliacao>()
        }

        suspend fun getAvaliacoesByUsuario(idUsuario: String): List<Avaliacao> {
            return client
                .from("tb_avaliacao")
                .select {
                    filter { eq("id_usuario", idUsuario) }
                    order("data", Order.DESCENDING)
                }
                .decodeList<Avaliacao>()
        }

        suspend fun getAcessibilidade(id: Int): Acessibilidade? {
            return client
                .from("tb_acessibilidade")
                .select {
                    filter {
                        eq("id_acessibilidade", id)
                    }
                }
                .decodeSingleOrNull<Acessibilidade>()
        }

        suspend fun updateAcessibilidade(acessibilidade: Acessibilidade) {
            val idAcessibilidade = requireNotNull(acessibilidade.id_acessibilidade) {
                "O id_acessibilidade não pode ser nulo"
            }

            client
                .from("tb_acessibilidade")
                .update(acessibilidade) {
                    filter {
                        eq("id_acessibilidade", idAcessibilidade)
                    }
                }
        }

        suspend fun deleteAcessibilidade(id: Int) {
            client
                .from("tb_acessibilidade")
                .delete {
                    filter {
                        eq("id_acessibilidade", id)
                    }
                }
        }
    }

    class AvaliacaoRepository {

        // Não envia nota nem data: o banco define as duas.
        // Devolve o id_avaliacao gerado, necessário para vincular a acessibilidade e as fotos.
        suspend fun createAvaliacao(nova: NovaAvaliacao): AvaliacaoCriada {
            return client
                .from("tb_avaliacao")
                .insert(nova) {
                    select()
                }
                .decodeSingle<AvaliacaoCriada>()
        }

        suspend fun getAvaliacao(id: Int): Avaliacao? {
            return client
                .from("tb_avaliacao")
                .select {
                    filter {
                        eq("id_avaliacao", id)
                    }
                }
                .decodeSingleOrNull<Avaliacao>()
        }

        // Atualiza só o comentário: nota e data são controladas pelo banco
        // e não podem ser sobrescritas por um objeto Avaliacao com campos nulos.
        suspend fun updateAvaliacao(avaliacao: Avaliacao) {
            val idAvaliacao = requireNotNull(avaliacao.id_avaliacao) {
                "O id_avaliacao não pode ser nulo"
            }

            client
                .from("tb_avaliacao")
                .update(
                    buildJsonObject { put("comentario", avaliacao.comentario) }
                ) {
                    filter {
                        eq("id_avaliacao", idAvaliacao)
                    }
                }
        }

        suspend fun deleteAvaliacao(id: Int) {
            client
                .from("tb_avaliacao")
                .delete {
                    filter {
                        eq("id_avaliacao", id)
                    }
                }
        }
    }

    class ImageRepository {
        suspend fun createImage(image: Image) {
            client
                .from("tb_image")
                .insert(image)
        }

        // A chave primária de tb_image é id_foto (não id_image).
        suspend fun getImage(id: Int): Image? {
            return client
                .from("tb_image")
                .select {
                    filter {
                        eq("id_foto", id)
                    }
                }
                .decodeSingleOrNull<Image>()
        }

        /** Quantas fotos já existem na avaliação (evita reaproveitar o mesmo índice ao editar). */
        suspend fun contarFotosDaAvaliacao(idAvaliacao: Int): Int {
            return client
                .from("tb_image")
                .select {
                    count(Count.EXACT)
                    filter { eq("id_avaliacao", idAvaliacao) }
                }
                .countOrNull()?.toInt() ?: 0
        }

        suspend fun updateImage(image: Image) {
            client
                .from("tb_image")
                .update(image) {
                    filter {
                        eq("id_foto", image.id_image)
                    }
                }
        }

        suspend fun deleteImage(id: Int) {
            client
                .from("tb_image")
                .delete {
                    filter {
                        eq("id_foto", id)
                    }
                }
        }
    }

    class LocalRepository {
        suspend fun createLocal(local: Local) {
            client
                .from("tb_local")
                .insert(local)
        }

        // Usado pelo cadastro de local: envia só as colunas preenchidas, incluindo
        // horario_inicial e horario_final (time), sem depender do model Local.
        suspend fun createLocal(linha: JsonObject) {
            client
                .from("tb_local")
                .insert(linha)
        }

        suspend fun getLocal(id: Int): Local? {
            return client
                .from("tb_local")
                .select {
                    filter {
                        eq("id_local", id)
                    }
                }
                .decodeSingleOrNull<Local>()
        }

        suspend fun getAllLocais(): List<Local> {
            return client
                .from("tb_local")
                .select()
                .decodeList<Local>()
        }

        suspend fun updateLocal(local: Local) {
            val idLocal = requireNotNull(local.id_local) {
                "O id_local não pode ser nulo"
            }

            client
                .from("tb_local")
                .update(local) {
                    filter {
                        eq("id_local", idLocal)
                    }
                }
        }

        suspend fun deleteLocal(id: Int): Boolean {
            return try {
                val apagados = client.from("tb_local").delete {
                    select()
                    filter {
                        eq("id_local", id)
                    }
                }.decodeList<Local>()

                Log.d("LocalRepository", "linhas apagadas: ${apagados.size}")
                apagados.isNotEmpty()
            } catch (e: Exception) {
                Log.e("LocalRepository", "Erro ao excluir local", e)
                false
            }
        }
    }

    class UserRepository {
        suspend fun createUser(user: User) {
            client
                .from("tb_usuario")
                .insert(user)
        }

        suspend fun getQtdAvaliacoes(idUsuario: String): Int {
            return client
                .from("tb_avaliacao")
                .select {
                    count(Count.EXACT)
                    filter { eq("id_usuario", idUsuario) }
                }
                .countOrNull()?.toInt() ?: 0
        }

        suspend fun getUser(id: String): User? {
            return client
                .from("tb_usuario")
                .select {
                    filter {
                        eq("id_usuario", id)
                    }
                }
                .decodeSingleOrNull<User>()
        }

        suspend fun userIsAdmin(id: String): Boolean {
            val user = client
                .from("tb_usuario")
                .select {
                    filter {
                        eq("id_usuario", id)
                    }
                }
                .decodeSingleOrNull<User>()

            return user?.is_admin == true
        }

        suspend fun updateUser(user: User) {
            val idUsuario = requireNotNull(user.id_usuario) {
                "O id_usuario não pode ser nulo"
            }

            client
                .from("tb_usuario")
                .update(user) {
                    filter {
                        eq(
                            column = "id_usuario",
                            value = idUsuario
                        )
                    }
                }
        }

        suspend fun deleteUser(id: String) {
            client
                .from("tb_usuario")
                .delete {
                    filter {
                        eq("id_usuario", id)
                    }
                }
        }
    }
}