# Validação AURON 0.2.1 — 24/10/2026

**Código compilado e testado:** `bfdafa97c315853613504f3fdee9db916d4e1743`.
[Execução completa do GitHub Actions](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/40128363671): todos os jobs aprovados.
Alterações posteriores de documentação e manifesto de hashes não mudam esse código.

| Verificação | Resultado efetivamente observado |
|---|---|
| Kotlin/JVM | 99 testes aprovados, zero falhas/ignorados |
| Servidor Node | 33 testes do handler + 22 testes HTTP aprovados |
| Recuperação web | 4 testes de política aprovados |
| Fonte/empacotamento | 9 verificações aprovadas |
| SQLite, migração e catálogo bíblico | 3 testes offline aprovados |
| PostgreSQL16 descartável | Migração aplicada duas vezes; proprietário, RLS e revisão aprovados |
| Android16/API36 emulado, pixel_c | 59 testes aprovados, zero falhas/erros/ignorados |
| Android Lint | Zero erros; 18 avisos |
| APK de app e instrumentação | Compilados com sucesso |

Comandos executados no CI:

```sh
python3 scripts/check-source.py
python3 scripts/verify-offline.py
node server/test/handler.test.mjs
node server/test/server.test.mjs
node docs/recovery/test/policy.test.mjs
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
gradle --no-daemon connectedDebugAndroidTest
```

O job SQL executou os scripts `supabase/tests/bootstrap.sql`, a migração existente
(duas vezes, para verificar idempotência) e `supabase/tests/checks.sql` **somente
em PostgreSQL descartável**, nunca no banco pessoal. O bootstrap é exclusivo de teste.

Os 59 testes Android cobrem toque/foco/entrada e IME nos formulários de acesso,
validação e callbacks de login/cadastro, navegação, tarefas/histórico, migração,
preferências, isolamento de perfis e memórias, sessões/credenciais cifradas,
leitura bíblica, controles de voz e nuvem, imagem/zoom/Pular e notificações.
Os cinco testes do controlador usam permissão real do Android concedida temporariamente
por shell **no emulador**, com restauração, e notificações sintéticas. Não enviam
mensagens reais. A animação estava habilitada; o teste de movimento foi executado.

## Falhas encontradas e corrigidas

- Na primeira execução Android, o estado concluído da leitura não estava visível
  no painel parcialmente expandido. O painel agora abre expandido, sem alteração
  da lógica bíblica ou da conclusão da rotina. O teste passou na execução final.
- O novo teste do controlador usou inicialmente uma assinatura de construtor de
  StatusBarNotification ausente da API pública. Corrigido no código de teste; a
  suíte foi compilada e executada integralmente depois.
- O teste de movimento foi ignorado na primeira rodada, pois o workflow desativava
  animações. Agora estão habilitadas e nenhum teste Android foi ignorado.
- A revisão de código corrigiu invalidação de respostas quando a ação da notificação
  muda, quando o conteúdo fica vazio/resumo, e interrupção da voz ao revogar consentimento.

Os 18 avisos Lint são sugestões de atualização de dependências/target, constantes
SpeechRecognizer e um alerta heurístico sobre singleton que retém somente
applicationContext. Não foram trocadas dependências/SDK nem a voz nesta etapa.

## Supabase real

Projeto AURON 0.2: migração `auron_owner_cloud` aplicada e e-mail autorizado registrado
na tabela privada. Consultas retornaram: proprietário permitido=true, outro e-mail
recusado=true, evento sem e-mail recusado=true, RLS forçada=true, leitura anon=false,
inserção direta authenticated=false, leitura da lista privada=false. Zero usuários
Auth foram encontrados; nenhuma conta, senha ou cópia de dados do tablet foi criada.

Isso verifica a função SQL e privilégios, **não a ativação do Auth Hook**, que depende
do painel. Cadastro, confirmação, login e recuperação reais ainda dependem dessa
configuração e de testes com a conta do proprietário. [Procedimento](AUTH-SETUP.md).
Os avisos dos advisors e a função preexistente inspecionada estão documentados ali.

## APK e preservação

Artefato [AURON-APK-e-relatorios](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/40128363671/artifacts/12574560610).
APK debug: `17951808` bytes, SHA-256:
`957795985ca1a35491f3141c186482965a4b9d89e2888607ad5f595e4cda3d32`.

A nova imagem está dentro do APK e seu SHA-256 coincide com o anexo original:
`0cd930a3c3ed9fa6e9648ffceefa7e049527f305d466301649ebfdf9d35f060a`.
Os 132 arquivos da versão-base continuam presentes. Arquivos de dados, seleção/
catálogo bíblico e voz não mudaram. Nenhuma operação acessou/apagou dados do tablet.
Persistência e migração passaram com dados de ensaio; os dados pessoais do Lenovo
não foram inspecionados. Atualização mantém `br.com.auron`, versionName0.2.1,
versionCode4, esquema Room existente e configuração de assinatura original.

## Limites

- Não houve acesso físico ao Lenovo TB336FU; voz masculina instalada, entrega real
  pelo WhatsApp e desempenho físico precisam de teste no tablet.
- O proprietário não possui servidor IA configurado. Handler/HTTP usam fixtures;
  nenhum teste comprova inferência real. [Instalação do servidor](AI-SETUP.md).
- Não há APK/chave da instalação original para comparação. O APK debug do CI é de
  validação; só atualize a instalação pessoal com a chave original do Android Studio.
- Gradle/SDK/adb não estão instalados na máquina local; o CI executou as verificações
  Android. Aqui, os testes HTTP falham com listen EPERM; os mesmos 22 passaram no CI.

[Entrega e instalação](DELIVERY-PHASE3.md) · [Automações](AUTOMATION.md).
