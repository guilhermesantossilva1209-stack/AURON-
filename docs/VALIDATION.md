# Validação AURON 0.2.1 — 22/10/2026

O fonte foi integrado e revisado; **não houve compilação Kotlin nem execução
Android nesta máquina**. Nenhum resultado abaixo equivale a login Supabase,
RLS hospedada, áudio ou teste físico do Lenovo TB336FU.

## Executado e aprovado

| Comando/verificação | Resultado | Evidência |
|---|---|---|
| python3 scripts/check-source.py | 9/9 estáticas | [JSON](static-checks.json) |
| python3 scripts/verify-offline.py | 3/3 SQLite e ativos | [Log](verification/sql-assets-final.txt) |
| node server/test/handler.test.mjs | 26/26 handler com fixtures | [Log](verification/server-handler-final.txt) |
| node docs/recovery/test/policy.test.mjs | 4/4 políticas de recuperação | [Log](verification/recovery-policy-final.txt) |
| node --check server/server.mjs e docs/recovery/recovery.mjs | Sintaxe aprovada | Execução local |
| sh -n scripts/create-wrapper.sh | Sintaxe aprovada | Execução local |
| Leitura YAML + conferência dos caminhos/jobs SQL | Aprovada | Execução Python/PyYAML |
| Preservação da referência oficial | SHA-256 igual ao original recebido | [Abertura](ANIMATION.md) |
| Comparação com snapshot anterior à etapa atual | Catálogo/seletor/tela de rotina/repositório sem mudanças | Comparação byte a byte |

Os testes SQLite executam as instruções de migração extraídas do fonte contra
fixtures legadas e verificam preservação dos registros. Não executam Room,
Android Keystore ou PostgreSQL. Os testes Node usam identidades/provedores
controlados; não chamam o projeto AURON-0.2 nem um modelo real.

## Falhas e bloqueios efetivamente observados

- `gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`
  encerrou com código 127: **gradle: command not found**. Gradle, kotlinc, SDK,
  sdkmanager e ADB ausentes; Java21 presente. Não há APK gerado nem resultado de Lint.
- A sondagem curta à distribuição Gradle falhou no proxy. Uma tentativa de permissão
  adicional terminou abortada antes de executar o comando; não há aprovação ou
  resultado de rede a presumir. Não foram repetidos ciclos de instalação.
- `node server/test/server.test.mjs`: **20 casos bloqueados** em
  `listen EPERM 127.0.0.1`, antes dos cenários HTTP. [Log](verification/server-http-final.txt).
  São falhas de infraestrutura, não passes.
- PostgreSQL/psql ausentes, Docker sem acesso ao socket. SQL/RLS não executados aqui.
- Sem acesso administrativo ao Supabase, credenciais públicas preenchidas, SMTP,
  página publicada, emulador ou tablet conectado.

## Escritos, mas não executados

A contagem atual em static-checks.json é **72 testes JVM e 36 instrumentados**.
Incluem autenticação, perfil/sessão, memória, persistência, consentimento de cópia,
voz e enquadramento da câmera. Existem ainda os ensaios SQL em
[supabase/tests](../supabase/tests/README.md).

O workflow prepara PostgreSQL16 descartável, aplica a migração duas vezes e
verifica proprietário/anon/outro usuário, confirmação, RLS, permissões e revisões.
Só depois libera o build Android. Este workflow **não foi executado remotamente**.
A correção do nome do banco de teste e o uso de pipefail foram revisados no YAML;
a execução SQL permanece pendente.

O emulador opcional usa Android16/API36. O teste de movimento ignora a execução
quando as animações Android estão desativadas; habilitá-las é necessário para
validar os quadros da abertura. O código usa compile/target35 e min26, sem
alteração de applicationId ou schema Room nesta etapa.

## Aceitação externa necessária

1. CI: SQL, compilação Kotlin/KSP, JVM, Lint e instrumentação.
2. Supabase real: aplicar scripts, ativar Hook, criar/confirmar proprietário,
   testar bloqueio de outros e-mails, login/recuperação/logout e dados RLS.
3. Tablet: atualização com assinatura original, registros antigos, câmera completa,
   voz offline instalada, configurações persistidas e falhas de rede.
4. Confirmar que fonte antigo e novo usam a mesma assinatura: não houve acesso à
   chave privada ou ao certificado da instalação atual.

Limitações de produto: cópia remota manual sem restauração/sincronização; voz
masculina depende das opções do dispositivo; animação é câmera sobre imagem 2D.
Nada disso foi apresentado como integração real testada em produção.
