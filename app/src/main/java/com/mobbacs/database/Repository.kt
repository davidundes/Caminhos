package com.mobbacs.database

import com.mobbacs.database.SupabaseClient.client

import com.mobbacs.models.Acessibilidade
import com.mobbacs.models.Avaliacao
import com.mobbacs.models.Image
import com.mobbacs.models.Local
import com.mobbacs.models.User

import io.github.jan.supabase.postgrest.from


object Repository {
    class AcessibilidadeRepository {
        suspend fun createAcessibilidade(acessibilidade: Acessibilidade) {
            client
                .from("tb_acessibilidade")
                .insert(acessibilidade)
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
            client
                .from("tb_acessibilidade")
                .update(acessibilidade) {
                    filter {
                        eq("id_acessibilidade", acessibilidade.id_acessibilidade)
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
        suspend fun createAvaliacao(avaliacao: Avaliacao) {
            client
                .from("tb_avaliacao")
                .insert(avaliacao)
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

        suspend fun updateAvaliacao(avaliacao: Avaliacao) {
            client
                .from("tb_avaliacao")
                .update(avaliacao) {
                    filter {
                        eq("id_avaliacao", avaliacao.id_avaliacao)
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

        suspend fun getImage(id: Int): Image? {
            return client
                .from("tb_image")
                .select {
                    filter {
                        eq("id_image", id)
                    }
                }
                .decodeSingleOrNull<Image>()
        }

        suspend fun updateImage(image: Image) {
            client
                .from("tb_image")
                .update(image) {
                    filter {
                        eq("id_image", image.id_image)
                    }
                }
        }

        suspend fun deleteImage(id: Int) {
            client
                .from("tb_image")
                .delete {
                    filter {
                        eq("id_image", id)
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
            client
                .from("tb_local")
                .update(local) {
                    filter {
                        eq("id_local", local.id_local)
                    }
                }
        }

        suspend fun deleteLocal(id: Int) {
            client
                .from("tb_local")
                .delete {
                    filter {
                        eq("id_local", id)
                    }
                }
        }
    }

    class UserRepository {
        suspend fun createUser(user: User) {
            client
                .from("tb_usuario")
                .insert(user)
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