# Validação — estabilização AURON 0.2.1, 27/10/2026

Código desta correção: `2a68de2dfdf8027849cfcaa4a32b2775eddfeb1e`.
[GitHub Actions 40430241481](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/40430241481).
**Todos os jobs aprovados: PostgreSQL, compilação e emulador Android16.**
Alterações posteriores de documentação/manifesto não alteram esse código.

## Executado neste ambiente

- `python3 scripts/check-source.py`: 9/9 verificações aprovadas.
- `python3 scripts/verify-offline.py`: 3/3 testes SQLite/migração/catálogo aprovados.
- `node server/test/handler.test.mjs`: 33/33 aprovados com respostas de ensaio.
- `node docs/recovery/test/policy.test.mjs`: 4/4 aprovados.
- `node server/test/server.test.mjs`: 22 falharam antes de abrir servidor, com
  `listen EPERM 127.0.0.1` (restrição de socket do ambiente). Os mesmos22 passaram
  no CI. Não foram ignorados ou removidos para obter aprovação.
- A consulta do changelog Supabase por CLI não conectou ao proxy. A documentação
  oficial de Auth Hooks/códigos foi consultada pelo conector; sem tentativas ilimitadas.

## Executado no CI

- `gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`:
  compilação Kotlin/app/testes aprovada; **104 testes JVM aprovados**, zero falhas/ignorados.
- Android Lint: zero erros, 18 avisos (dependências/target/constantes e heurística
  preexistente do singleton com applicationContext). Sem troca de SDK/dependências.
- 33 testes do handler, 22 HTTP e 4 de recuperação: **59 aprovados**.
- 9 verificações estáticas e 3 testes offline aprovados.
- PostgreSQL16 descartável: migração aplicada duas vezes e testes de proprietário,
  RLS/revisão aprovados. Scripts de teste nunca executados no banco pessoal.
- `gradle --no-daemon connectedDebugAndroidTest`: emulador API36/Android16, pixel_c,
  animações habilitadas: **73 testes aprovados**, zero falhas/erros/ignorados.
  Os 14 casos adicionais cobrem AuthFlow (7), transporte IA (5) e continuidade local (2).

Os testes AuthFlow usam o gerenciador real, serialização REST e Keystore com
transporte HTTPS substituído por respostas controladas. Cobrem login, cadastro com
confirmação, sessão/restauração/logout, recuperação, revogação, erro de configuração,
resposta malformada e DNS/TLS/timeout. Os testes de UI cobrem foco/IME/validação e a
ação de configurar. RemoteAiTransport valida endpoint, cabeçalhos, consentimentos,
respostas e falhas nos acessos local/autenticado. ChatFallback usa a Activity,
ViewModel, Room e DataStore reais no emulador para ausência de servidor/token,
resposta local identificada e comandos locais. Nenhum teste usa conta/modelo real.

## Verificações remotas e limites

Consulta Supabase somente leitura em27/10: usuáriosAuth0, confirmados0, proprietários
permitidos1 e funçãoHook existente. Logs disponíveis24h: fontesPostgreSQL/PgBouncer,
sem evidência de requisiçãoAuth. Não houve alteração remota/destrutiva nesta rodada.
Esse resultado não comprova ativação do Hook, acesso direto do tablet, criação de
conta, recebimento de e-mail, login ou recuperação reais.

Ainda não testados: conta real, inferência/modelo, HTTPS do futurogateway, voz e
instalação no Lenovo físico. A assinatura do APK instalado não está disponível
para comparação. Nenhuma chave existente foi substituída.

## Preservação

Todos os154 arquivos da entrega anterior continuam presentes. Imagem/splash,
voz, temas, rotinas, Bíblia, banco e automações não foram alterados. Novos arquivos
são testes, política de erros e documentação. Pacote `br.com.auron`, versão0.2.1,
versionCode4 e esquemaRoom2 preservados. Sem acesso aos dados pessoais do tablet.

Entrega pelo [ZIP e Android Studio](STABILIZATION-20261027.md); sem Git Pull.
[Relatório da entrega anterior](verification/VALIDATION-20261024.md), histórico.
