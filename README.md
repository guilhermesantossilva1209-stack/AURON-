# AURON — Sistema de Inteligência Pessoal · 0.2.1

Continuação do aplicativo Android existente, com o mesmo pacote **br.com.auron**,
Android 8.0+ (API26), **versionCode4** e **versionName0.2.1**. Esta revisão corrige
os campos de autenticação, integra IA opcional nos dois modos, atualiza a imagem
oficial de abertura e acrescenta as automações autorizadas da Fase 3.
99 testes JVM e 59 testes Android16 emulado passaram, além dos testes do servidor.
[Entrega e instalação](docs/DELIVERY-PHASE3.md) · [Resultados e limites](docs/VALIDATION.md).
A assinatura original deve ser reutilizada para atualizar o tablet sem desinstalar.

## Implementação nesta versão

- Campos de acesso editáveis mesmo antes da configuração externa, foco e navegação
  pelo teclado. Cadastro por e-mail/senha com confirmação, validação, mensagens de erro e
  consentimento; login, recuperação, renovação e logout via Supabase Auth real.
  Sessão cifrada no Android Keystore. Exige [configuração externa](docs/AUTH-SETUP.md).
- Perfil local preservado, contas separadas e cópia explícita dos dados locais
  para uma conta, sem apagar os originais. Cadastro inicial restrito por Auth Hook
  e lista do proprietário no Supabase. A lista privada já foi configurada;
  a ativação do Hook no painel ainda depende do proprietário.
- Cópia manual na nuvem por HTTPS, com consentimento, consulta, exclusão, RLS e
  proteção contra revisão desatualizada. Sem restauração ou sincronização automática.
- Central de comando e rotinas matinal/noturna preservadas; robô Canvas original
  de corpo inteiro. A abertura usa a imagem oficial: close no peito, zoom até
  revelar o robô inteiro no novo fundo preto sem painéis, Pular e fade. Movimento de câmera sobre imagem 2D.
- Tarefas, histórico e voz por toque mantidos. Seleção de voz offline pt-BR,
  teste, velocidade e tonalidade salvos por perfil; gênero depende das vozes instaladas.
- Memórias manuais editáveis/excluíveis, cifradas e separadas do histórico.
- Provedor de IA HTTPS e servidor Node implementados; contexto limitado e consentimentos
  separados. Sem servidor/modelo configurado, permanece o modo demonstrativo explícito.
- [Automações autorizadas](docs/AUTOMATION.md): abertura de aplicativos por comando
  explícito, leitura de notificações por lista permitida e respostas WhatsApp por
  ação oficial, sempre com confirmação. Conteúdo transitório, sem histórico ou IA.
- Cinco versículos offline por dia, data local estável, histórico por perfil e
  conclusão somente pelo botão de leitura. [Fonte/licença](docs/BIBLE-LICENSE.md):
  35 versículos BLIVRE em ciclo de sete dias, sem repetição nos seis anteriores.
- Exportação JSON por seletor Android e exclusão explícita por perfil.

Não foram acrescentadas voz contínua, palavra de ativação, controle do tablet,
automações avançadas ou integrações de redes sociais. Os módulos futuros continuam
identificados como planejamento.

## Arquitetura e arquivos

Kotlin2.0.21, Jetpack Compose/Material3, MVVM com StateFlow, Room2.6.1,
DataStore1.1.1, Coroutines, APIs oficiais de voz e Keystore. Um módulo Gradle
organizado por responsabilidade evita reescrita e dependências desnecessárias.
A migração Room1→2 é aditiva, sem fallback destrutivo.

```text
app/src/main/java/br/com/auron/
  MainActivity.kt   ciclo de vida e exportação vinculada ao perfil
  auth/            Supabase REST, validação, sessão cifrada
  cloud/           cópia manual por RPC, limites e revisão esperada
  ai/              cliente HTTPS, contexto autorizado e credencial local cifrada
  automation/      intents, acesso autorizado a notificações e resposta confirmada
  bible/           catálogo e seleção diária
  data/            Room, migração, perfis, cifra de memória, DataStore
  domain/          modelos, repositórios, comandos e AiProvider
  ui/              central, cadastro/login, memória, leitura, robô e tema
  voice/           voz por toque e TTS existentes
app/src/main/assets/bible_daily.json
app/src/test/      testes JVM de domínio, políticas e segurança
app/src/androidTest/ testes de dados, sessão, notificações e UI em emulador
server/            gateway Node opcional, sem dependências npm
supabase/          migração, configuração do proprietário e testes SQL descartáveis
docs/recovery/     página HTTPS de recuperação a ser configurada/hospedada
.github/workflows/android.yml
```

[Arquitetura](docs/ARCHITECTURE.md) · [Privacidade](docs/PRIVACY.md) ·
[Cadastro e recuperação](docs/AUTH-SETUP.md) · [Nuvem](docs/CLOUD.md) ·
[Voz](docs/VOICE.md) · [Abertura](docs/ANIMATION.md) · [IA opcional](docs/AI-SETUP.md)

## Compilar e atualizar

Use JDK17, Gradle8.10.2 e Android SDK plataforma35/Build Tools34.0.0:

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
gradle --no-daemon connectedDebugAndroidTest
```

O segundo comando requer emulador ou dispositivo de teste. O primeiro gera
app/build/outputs/apk/debug/app-debug.apk se todos os passos passarem.
Não há gradlew/JAR oficial no pacote, apenas propriedades; o workflow instala
Gradle diretamente. O script scripts/create-wrapper.sh gera o wrapper usando uma
instalação Gradle8.10.2 já disponível.

Só com tablet, use o mesmo repositório no GitHub e o workflow **Android — compilar
e verificar**. O ZIP-fonte precisa ser extraído no repositório, preservando pastas.
Um ZIP sozinho não fornece os arquivos necessários ao Actions. O fonte atualizado
já foi publicado no [repositório autorizado](https://github.com/guilhermesantossilva1209-stack/AURON-).

**Não desinstale a versão atual.** Para atualizar preservando dados, o APK deve
ter o mesmo pacote e certificado de assinatura. Um novo APK debug gerado em outro
executor pode ter certificado diferente. A chave original não está nesta entrega.
[Procedimento completo de atualização e assinatura](docs/UPGRADE-0.2.md).

## Testes executáveis aqui

```bash
python3 scripts/check-source.py
python3 scripts/verify-offline.py
node server/test/handler.test.mjs
node docs/recovery/test/policy.test.mjs
```

O [CI aprovado](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/40128363671)
executou 9 verificações estáticas, 3 testes SQLite/ativos, 33 testes do handler,
22 testes HTTP, 4 de recuperação, 99 JVM e 59 instrumentados no emulador Android16.
Todos passaram, assim como os testes PostgreSQL16/RLS e a compilação dos APKs.
Lint terminou sem erros e com 18 avisos. Gradle/SDK não estão disponíveis na máquina
local; as verificações Android foram executadas no GitHub Actions.

Os testes do servidor usam fixtures. Não comprovam inferência real nem autenticação
com a conta do proprietário. A migração e a política SQL foram verificadas no
Supabase real, mas ativação do Hook, cadastro/login/recuperação HTTP e testes físicos
no tablet continuam pendentes.
[Validação e limites](docs/VALIDATION.md) · [Como retomar](docs/RESUME.md) ·
[Relatório desta entrega](docs/DELIVERY-PHASE3.md) · [Build Android16](docs/BUILD-0.2.1.md)

## Comandos locais preservados

```text
Bom dia, AURON.
Mostre minha rotina.
O que falta fazer?
Criar tarefa: Revisar roteiro da Clau
Concluir tarefa: Revisar roteiro da Clau
Concluir atividade: Beber água
Boa noite, AURON.
Abrir configurações
Ajuda
```

Conclusão exige título exato e único. Para a atividade bíblica, o comando abre a
leitura; não a conclui automaticamente. Respostas do modelo nunca viram comandos.
