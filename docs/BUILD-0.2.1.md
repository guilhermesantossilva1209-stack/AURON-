# Compilação e atualização 0.2.1

## Revisão de 24/10/2026

Fonte atual: versionCode4, versionName0.2.1, applicationId br.com.auron.
A compilação desta etapa passou no [CI 40062208644](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/40062208644),
com 99 testes JVM e lint sem erros. O estado final dos testes instrumentados e
artefatos está em [VALIDATION.md](VALIDATION.md). Os resultados abaixo de 23/10
são históricos e não substituem a validação da revisão atual.

O emulador Android16 agora roda também a cada push na branch principal.
Na execução manual, marque `instrumentation`. A assinatura original continua
obrigatória para atualizar o APK pessoal; o APK debug do CI usa chave de validação.

## Resultado anterior — 23/10/2026

O [workflow 39996099432](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/39996099432)
passou no commit `9f2f34e728b54de543769eb38591edc1a1fcee34`. Compilação Kotlin/KSP,
72 testes JVM, lint (zero erros, 17 avisos) e geração dos APKs passaram. O artefato
`AURON-APK-e-relatorios` contém `outputs/apk/debug/app-debug.apk` e relatórios.
O job de emulador não foi executado; compilar o APK de testes não executa seus casos.

É um APK debug de validação, assinado pelo CI. Não foi gerado release com a chave
original. O usuário informou que a instalação atual veio do Android Studio ou
outro compilador; é necessário recuperar/reutilizar a chave desse ambiente.
Não há confirmação de compatibilidade de assinatura nem autorização para substituí-la.
Veja [validação](VALIDATION.md) e [atualização segura](UPGRADE-0.2.md).

Esta revisão mantém o aplicativo `br.com.auron`, usa `versionCode = 4` e
`versionName = "0.2.1"`. É uma atualização da família AURON 0.2. Não há mudança
de identificador, chave de assinatura, versão do banco Room ou estratégia de
migração nesta preparação de compilação.

## Histórico do ambiente local — 22/10/2026

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
efêmero; não são credenciais do projeto Supabase pessoal. Esse job passou no GitHub
em 23/10/2026; o PostgreSQL continua indisponível nesta máquina local.
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
automaticamente. A execução remota aprovada está vinculada no início deste documento.

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

Se a versão instalada tiver um `versionCode` maior que 4, será necessário elevar
o número novamente antes de compilar. O código salvo antes desta revisão tinha
`versionCode = 2`; o APK físico instalado ainda não foi inspecionado.
