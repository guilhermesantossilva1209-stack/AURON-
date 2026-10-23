# Compilação e atualização 0.2.1

Esta revisão mantém o aplicativo `br.com.auron`, usa `versionCode = 3` e
`versionName = "0.2.1"`. É uma atualização da família AURON 0.2. Não há mudança
de identificador, chave de assinatura, versão do banco Room ou estratégia de
migração nesta preparação de compilação.

## Estado verificado neste ambiente

Em 22/10/2026 foi encontrada a JVM OpenJDK 21.0.12.1. Não foram encontrados os
executáveis Gradle, `kotlinc`, `sdkmanager` ou `adb`, nem um Android SDK instalado
nos caminhos de ferramentas consultados. Não há compilador Kotlin ou
dependências Android locais disponíveis para executar os testes Kotlin.

A sondagem executada nesta retomada foi:

```sh
curl -I --connect-timeout 3 --max-time 5 https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
```

Ela encerrou imediatamente com código 7: `Failed to connect to proxy port 8080`.
O proxy configurado pelo ambiente foi preservado. Uma segunda tentativa com
permissão adicional de rede foi solicitada, mas a ferramenta encerrou como
`aborted by user` antes de fornecer resultado do comando; isso não comprova
aprovação nem conectividade. Nenhum download foi concluído, nenhuma ferramenta
foi instalada parcialmente e nenhum certificado ou chave foi substituído.

PostgreSQL (`psql`/`postgres`) também não está instalado. A consulta explícita ao
daemon Docker local falha com `operation not permitted` no socket, embora o
processo `dockerd` esteja ativo; o usuário do ambiente é UID 1000. Portanto não
foi possível usar um contêiner PostgreSQL para validar RLS neste ambiente.

**Nenhum APK foi compilado nesta máquina.** Compilação, lint, testes JVM Kotlin
e testes instrumentados continuam dependendo de um ambiente com a cadeia de
ferramentas e acesso aos repositórios Google/Maven/Gradle. As verificações
Python/Node e a análise dos arquivos não substituem essas etapas.

## Android 16 e ferramentas

Antes do trabalho de compilação, o workflow executa `postgres-security` em um
PostgreSQL 16 descartável. Esse trabalho aplica a migração duas vezes e executa
os testes de proprietário, RLS e controle de revisão de
`supabase/tests/checks.sql`. A compilação depende do sucesso desse trabalho.
Os valores de conexão definidos no workflow pertencem somente ao banco de teste
efêmero; não são credenciais do projeto Supabase pessoal. Esse job foi preparado
e seu YAML foi analisado, mas não foi executado nesta máquina nem no GitHub.
Ele não substitui o teste de configuração do Auth Hook no projeto Supabase real.

O projeto mantém Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, Java 17 para os
artefatos, `compileSdk = 35`, `targetSdk = 35` e Android mínimo 26. Um aplicativo
com alvo 35 pode ser instalado no Android 16/API 36; isso não equivale a afirmar
que todas as telas e recursos já foram validados no Lenovo Idea Tab TB336FU.

O trabalho opcional de testes instrumentados no GitHub Actions agora usa um
tablet emulado `pixel_c`, Android 16/API 36, `google_apis`, `x86_64`. A compilação
continua usando a plataforma SDK 35. O emulador verifica o comportamento no
Android 16, mas não substitui os testes físicos de animação, vozes instaladas,
permissões e autenticação no tablet Lenovo.

## Gerar pelo navegador do tablet

1. Atualize os arquivos do **mesmo repositório**, incluindo `.github/workflows/android.yml`.
   O ZIP de fontes deve ser extraído: guardar somente o ZIP no repositório não
   permite executar a compilação. Preserve a pasta `.github` e os subdiretórios.
2. Abra **Actions → Android — compilar e verificar → Run workflow**.
3. Ative **Executar testes instrumentados em tablet Android 16 emulado** para
   executar também a suíte Android. A suíte JVM, lint e compilação dos APKs de
   aplicativo/testes já fazem parte da etapa principal.
4. Para uma atualização instalável sobre a versão atual, configure a **mesma
   chave privada que assinou o APK instalado**, conforme [UPGRADE-0.2.md](UPGRADE-0.2.md),
   e marque **Gerar atualização com a chave original configurada nos Secrets**.
   Não coloque senhas nem o arquivo de chave no repositório ou nesta conversa.
5. Aguarde os trabalhos terminarem. Baixe o artefato
   **AURON-APK-e-relatorios**, extraia-o e localize o APK `release` assinado com
   a chave original. O APK `debug` comum é destinado a validação: a chave
   temporária do runner pode ser diferente da assinatura já instalada.

O workflow usa ferramentas gratuitas. GitHub Actions/Codespaces pode consumir
a franquia da conta; não é necessário contratar plano ou habilitar cobrança
automaticamente. Nenhum workflow remoto foi executado nesta revisão local.

## Comandos em ambiente com as ferramentas instaladas

Com JDK 17, Gradle 8.10.2 e Android SDK 35 disponíveis:

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Com um emulador/dispositivo de teste conectado:

```sh
gradle --no-daemon connectedDebugAndroidTest
```

Os testes instrumentados devem usar um dispositivo de teste: eles criam e
modificam dados de ensaio. Não execute a suíte de testes no perfil pessoal do
proprietário que contém seus registros de produção.

Para gerar o release de atualização, o ambiente deve receber
`AURON_KEYSTORE_PATH`, `AURON_STORE_PASSWORD`, `AURON_KEY_ALIAS` e
`AURON_KEY_PASSWORD` pelo mecanismo de segredos. Então:

```sh
gradle --no-daemon assembleRelease
```

O resultado esperado é `app/build/outputs/apk/release/app-release.apk`.
Sem a chave configurada, o release é **não assinado** e não deve ser apresentado
como atualização pronta. O projeto inclui `scripts/create-wrapper.sh` para
gerar o wrapper oficial quando já houver Gradle 8.10.2 instalado; ele não baixa
nem instala Gradle.

## Instalar sem perder dados

Abra no tablet apenas o APK que tenha o mesmo `applicationId` e certificado de
assinatura da instalação atual, e aceite **Atualizar**. O Android preserva os
dados privados em uma atualização compatível. Não desinstale a versão anterior
nem limpe seus dados. Se aparecer incompatibilidade de assinatura, pare e
recupere a chave original; não há como obter a chave privada a partir de um APK.

Se a versão instalada tiver um `versionCode` maior que 3, será necessário elevar
o número novamente antes de compilar. O código salvo antes desta revisão tinha
`versionCode = 2`; o APK físico instalado ainda não foi inspecionado.
