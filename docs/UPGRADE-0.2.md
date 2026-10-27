> **Procedimento vigente desta entrega (27/10/2026):** [ZIP no notebook e Android Studio](STABILIZATION-20261027.md#entrega-e-atualização-pelo-notebook). Não é necessário Git Pull. O restante deste documento preserva alternativas de compilação pelo tablet discutidas anteriormente.

# Gerar e instalar a atualização 0.2.1 pelo tablet

O aplicativo continua Kotlin/Compose nativo, pacote **br.com.auron**, Android8+
(API26). A versão 0.2.1 usa **versionCode4**. O projeto-fonte não é um APK instalável.
O [CI 40128363671](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/40128363671)
compilou o APK debug e executou 99 testes JVM e 59 testes no emulador Android16,
além das verificações do servidor e de segurança. O APK debug é de validação:
sua assinatura não foi comparada à instalação pessoal. Consulte os
[resultados e artefatos](VALIDATION.md) antes de instalar.

## Antes de atualizar: assinatura e dados

1. No AURON atual, exporte os registros para um destino de sua escolha e mantenha o
   aplicativo instalado. A exportação JSON serve para consulta/backup; restauração
   automática desse arquivo ainda não existe.
2. Identifique onde foi gerado o APK instalado e localize a **mesma chave de
   assinatura** usada nele. O código-fonte não revela a chave; ela não está neste
   ambiente. Não envie a chave ou senhas pelo chat.
3. Android exige pacote igual, versionCode não inferior ao instalado e certificado compatível para
   instalar por cima preservando dados. O fluxo original de CI gerava APK debug;
   executores novos podem criar chaves debug diferentes. Um novo build bem-sucedido
   não prova compatibilidade com a instalação atual.
4. Se a chave original foi perdida, não é possível obtê-la a partir do APK. Pare a
   atualização sobre dados importantes até resolver a assinatura e a estratégia de
   recuperação. **Não desinstale nem limpe o armazenamento para contornar o erro.**

Quando os APKs antigo e novo estiverem disponíveis, um ambiente com Android Build
Tools pode comparar o certificado público, sem revelar chave privada:

```bash
apksigner verify --print-certs auron-antigo.apk
apksigner verify --print-certs auron-novo.apk
```

Compare “Signer certificate SHA-256 digest”. O APK antigo precisa ser o que foi
instalado. Aqui não há acesso ao tablet/APK antigo para verificar esse certificado.

## Colocar o fonte atualizado no mesmo repositório

O código desta revisão já foi publicado no
[repositório autorizado](https://github.com/guilhermesantossilva1209-stack/AURON-).
Não é necessário repetir o envio para compilar essa revisão. As instruções abaixo
servem para quando você precisar enviar alterações futuras pelo seu próprio ambiente.

O ZIP desta entrega contém uma pasta auron com arquivos e subpastas, inclusive
.github. Na raiz do repositório devem aparecer app/, server/, docs/, scripts/,
.github/, build.gradle.kts e settings.gradle.kts. **Enviar apenas o ZIP não basta.**

Se você já usa um Codespace pelo navegador do tablet, aproveite esse mesmo ambiente.
Antes de copiar, confira alterações locais com `git status --short` e preserve
qualquer trabalho diferente desta entrega. Se não houver Codespace, use-o somente
se disponível dentro da cota gratuita desejada; não habilite faturamento por esta
instrução. Outra opção é qualquer ambiente de desenvolvimento existente com Git.

Após enviar AURON-0.2.1-fase3-codigo-atualizado.zip para a raiz do repositório no Codespace,
estes comandos extraem em pasta temporária e copiam a atualização preservando .git:

```bash
python3 -m zipfile -e AURON-0.2.1-fase3-codigo-atualizado.zip /tmp/auron-update-021
cp -a /tmp/auron-update-021/auron/. .
git status --short
```

Revise o diff e confirme somente os arquivos do projeto. Não adicione keystores,
senhas, .env ou arquivos pessoais. Exemplo de seleção dos arquivos desta entrega:

```bash
git add app server supabase docs scripts .github gradle .gitignore README.md SOURCE-SHA256.txt build.gradle.kts settings.gradle.kts gradle.properties
git diff --cached --stat
git commit -m "Integrar AURON 0.2.1 para validação"
git push
```

Os comandos são exemplos para seu ambiente autenticado. Nesta entrega, a publicação
foi realizada pelo conector autorizado do GitHub. Um novo commit pode iniciar o
workflow configurado no repositório e consumir a cota de execução da conta.

## Compilar no GitHub Actions

1. No navegador, abra o mesmo repositório → **Actions → Android — compilar e
   verificar → Run workflow**.
2. Na execução manual, para testes em emulador, marque **instrumentation**.
   O emulador também roda nos pushes à branch principal. A execução completa leva
   mais tempo/cota. Não trate um job vermelho como APK validado: abra o primeiro
   erro do log e corrija antes de instalar.
3. Antes do build, o workflow valida SQL/RLS em PostgreSQL16 descartável. Depois
   instala JDK17, Gradle8.10.2 e SDK, roda verificações Node/Python,
   testes JVM, Lint e compila app/test APK. Não depende de gradlew ausente.
4. Para gerar uma atualização com a assinatura original, configure no próprio
   GitHub os quatro **Actions Secrets** abaixo e marque **signed_update**:
   - AURON_KEYSTORE_BASE64: arquivo original convertido para base64;
   - AURON_STORE_PASSWORD: senha do arquivo;
   - AURON_KEY_ALIAS: alias original;
   - AURON_KEY_PASSWORD: senha da chave.
5. Sem signed_update, o resultado é APK debug para teste em instalação descartável,
   sem garantia de atualizar o tablet atual. Com a opção, o workflow exige todos
   os Secrets; não cria nem substitui uma chave de assinatura automaticamente.
6. Ao terminar com sucesso, baixe **AURON-APK-e-relatorios**, extraia o ZIP e escolha
   o APK correspondente. O de atualização fica em outputs/apk/release/. O debug
   fica em outputs/apk/debug/. Não instale um APK de testes instrumentados como app.

A conversão da chave deve ser feita em ambiente privado que você controla, sem
publicar sua saída nem anexá-la aqui. O workflow usa arquivo temporário restrito e
o remove ao finalizar. Execute com Secrets apenas código revisado e confiável.

## Instalar e testar

Abra o APK correto pelo gerenciador de arquivos do tablet. Se solicitado, permita
temporariamente instalação por esse gerenciador. Escolha **Atualizar**, mantendo
o AURON existente. Se o Android apontar conflito de assinatura, não desinstale:
volte à checagem da chave original. Depois revogue a permissão de instalar apps
desconhecidos caso não precise mais dela.

Após atualizar: confira tarefas/rotinas antigas, histórico e preferências; feche e
reabra. Cadastre/configure a conta conforme [AUTH-SETUP.md](AUTH-SETUP.md), teste
login, confirmação, recuperação e logout. Importe o perfil local somente se desejar
uma cópia na conta. Teste a leitura bíblica antes/depois de confirmar a conclusão.
Teste também a voz nas configurações e a câmera da abertura. Para nuvem, siga
[CLOUD.md](CLOUD.md); envio é manual. O roteiro está em [ACCEPTANCE.md](ACCEPTANCE.md).
Os testes em emulador do workflow usam Android16/API36; isso não comprova teste físico.

## Compilação em outro ambiente disponível

Requisitos: JDK17, Gradle8.10.2, Android SDK plataforma35 e Build Tools34.0.0,
licenças SDK aceitas e internet para baixar dependências na primeira execução.
Defina ANDROID_HOME ou sdk.dir em local.properties (não versionado).

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
gradle --no-daemon connectedDebugAndroidTest
```

O segundo comando exige emulador/dispositivo de teste. Para release, configure no
ambiente seguro AURON_KEYSTORE_PATH, AURON_STORE_PASSWORD, AURON_KEY_ALIAS e
AURON_KEY_PASSWORD com a chave original, então execute `gradle assembleRelease`.
Sem chave, a variante release não é automaticamente assinada para instalação.

Notebook não é obrigatório se o ambiente cloud/CI e a chave original estiverem
acessíveis pelo tablet. Termux sozinho não é uma rota Android Gradle oficialmente
suportada por este projeto. Não houve instalação de ferramentas no tablet nesta
sessão.
