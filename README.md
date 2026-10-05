# MobBacs

Aplicativo Android para **mapear e avaliar a acessibilidade de locais** em Jacareí (SP). Os usuários visualizam locais em um mapa, cadastram novos pontos e avaliam o quão acessível cada um é, com nota por estrelas e uma lista de itens de acessibilidade.

## Funcionalidades

- **Autenticação**: cadastro e login por e-mail e senha (Supabase Auth). Os dados complementares do usuário (nome, CPF) ficam na tabela `tb_usuario`.
- **Mapa de locais**: mapa OpenStreetMap (osmdroid) centralizado em Jacareí, com área de rolagem limitada à cidade e zoom entre 13 e 19. Os marcadores (POIs) só aparecem a partir do zoom 17.
- **Detalhes do local**: ao tocar em um marcador, um balão mostra nome, endereço, horário, a nota média e a contagem de usuários que marcaram cada item de acessibilidade.
- **Avaliação de acessibilidade**: nota geral (RatingBar) mais 10 itens marcáveis:
  - Rampa
  - Elevador
  - Banheiro acessível
  - Piso tátil
  - Vaga PCD
  - Entrada acessível
  - Corrimão
  - Portas largas
  - Sinalização
  - Iluminação
- **Cadastro de local**: o usuário posiciona o mapa de modo que o pin fixo no centro fique sobre o local, confirma, e preenche nome, endereço, CEP, telefone e horário.

## Tecnologias

| Item | Tecnologia |
|---|---|
| Linguagem | Kotlin |
| Plataforma | Android (AppCompat, Activities) |
| Backend | [Supabase](https://supabase.com) (Auth + Postgrest) via `supabase-kt` |
| Serialização | kotlinx.serialization |
| Mapas | [osmdroid](https://github.com/osmdroid/osmdroid) |
| Assincronia | Kotlin Coroutines (`lifecycleScope`) |

## Estrutura do projeto

```
com.mobbacs
├── MainActivity.kt            # Ponto de entrada; redireciona para a Home
├── database/
│   ├── SupaDB.kt              # SupabaseClient (Auth + Postgrest)
│   └── Repository.kt          # Repositórios de acesso às tabelas
├── models/
│   ├── User.kt
│   ├── Local.kt
│   ├── Avaliacao.kt
│   ├── Acessibilidade.kt
│   └── Image.kt
├── home/
│   ├── HomeActivity.kt        # Mapa, marcadores e resumo de avaliações
│   └── localInfoWindow.kt     # Balão do marcador com botão "Avaliar"
├── local/
│   └── LocalActivity.kt       # Cadastro de novos locais
├── avaliacao/
│   └── AvaliacaoActivity.kt   # Tela de avaliação
├── login/
│   └── LoginActivity.kt
└── register/
    └── RegisterActivity.kt
```

## Banco de dados

O app espera as seguintes tabelas no Supabase (Postgres):

| Tabela | Chave primária | Descrição |
|---|---|---|
| `tb_usuario` | `id_usuario` (UUID do Auth) | Nome, e-mail e CPF |
| `tb_local` | `id_local` | Nome, endereço, CEP, latitude, longitude, telefone, horário |
| `tb_avaliacao` | `id_avaliacao` | `id_usuario`, `id_local`, `nota`, `data` |
| `tb_acessibilidade` | `id_acessibilidade` | `id_avaliacao` + um booleano por item de acessibilidade |
| `tb_image` | `id_image` | Imagens associadas a locais e avaliações (modelo previsto, ainda não usado nas telas) |

Relacionamentos: cada avaliação pertence a um usuário e a um local, e cada registro de acessibilidade pertence a uma avaliação.

> As colunas `id_*` geradas pelo banco (como `id_avaliacao`) devem ser autoincrementais, pois o app depende do valor retornado no `insert` para vincular a acessibilidade à avaliação.

## Como executar

### Pré-requisitos

- Android Studio atualizado
- JDK 17 ou superior
- Um projeto no Supabase com as tabelas acima criadas

### Configuração

1. Clone o repositório:
   ```bash
   git clone <url-do-repositorio>
   cd mobbacs
   ```
2. Adicione as credenciais do Supabase em `local.properties` (não versionar este arquivo):
   ```properties
   SUPABASE_URL=https://seu-projeto.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sua-chave-publica
   ```
3. Garanta que o `build.gradle` exponha essas chaves via `BuildConfig` (o app lê `BuildConfig.SUPABASE_URL` e `BuildConfig.SUPABASE_PUBLISHABLE_KEY`):
   ```kotlin
   // app/build.gradle.kts
   val props = java.util.Properties().apply {
       load(rootProject.file("local.properties").inputStream())
   }

   android {
       buildFeatures { buildConfig = true }
       defaultConfig {
           buildConfigField("String", "SUPABASE_URL", "\"${props["SUPABASE_URL"]}\"")
           buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${props["SUPABASE_PUBLISHABLE_KEY"]}\"")
       }
   }
   ```
4. Confirme as permissões no `AndroidManifest.xml` exigidas pelo osmdroid: `INTERNET` e `ACCESS_NETWORK_STATE`.
5. Sincronize o Gradle e execute em um emulador ou dispositivo físico.

## Fluxo de uso

1. Ao abrir o app, a `HomeActivity` carrega os locais do Supabase e os exibe no mapa.
2. Sem sessão ativa, o usuário é levado à tela de login (com link para o cadastro).
3. Dando zoom no mapa, os marcadores aparecem; tocar em um deles abre o balão com as avaliações.
4. Pelo botão **Avaliar** do balão, o usuário logado envia nota e itens de acessibilidade.
5. Pelo botão de criar local na Home, é possível cadastrar um novo ponto.

## Roadmap

- [ ] Cadastro de local por toque longo no mapa, abrindo a tela pelo botão flutuante (FAB)
- [ ] Tela de detalhes de uma avaliação (`showAvaliacao`) com nota e itens marcados
- [ ] Upload e exibição de fotos dos locais e avaliações (`tb_image`)
- [ ] Validação de campos (CPF, CEP, e-mail) nos formulários

## Contribuidores

Veja todos os contribuidores deste projeto:
[Felipe Villalva](https://github.com/felipe-villalva)
[Davi Dundes](https://github.com/davidundes)
[Lucas Soares]((https://github.com/lucasdacostasoaresti-info))
