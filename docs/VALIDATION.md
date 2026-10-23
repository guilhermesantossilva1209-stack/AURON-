# Validação AURON 0.2.1 — 23/10/2026

**A compilação e os testes do CI foram aprovados no GitHub Actions.** Isso não
equivale a teste físico no Lenovo TB336FU, validação do Supabase real ou confirmação
da assinatura necessária para atualizar o aplicativo instalado.

## Evidências do CI

- [Execução aprovada 39996099432](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/39996099432).
- Commit validado: `9f2f34e728b54de543769eb38591edc1a1fcee34`.
- [Artefato APK e relatórios 12520066542](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/39996099432/artifacts/12520066542).
- Comando Android: `gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.

| Verificação executada | Resultado |
|---|---|
| python3 scripts/check-source.py | 9/9 verificações estáticas aprovadas |
| python3 scripts/verify-offline.py | 3/3 testes SQLite e ativos aprovados |
| node server/test/handler.test.mjs | 26/26 aprovados |
| node server/test/server.test.mjs | 20/20 aprovados no CI |
| node docs/recovery/test/policy.test.mjs | 4/4 aprovados |
| PostgreSQL16 descartável: migração idempotente e ensaios RLS | Aprovados |
| Testes JVM | 72 executados, 0 falhas, 0 ignorados |
| Android Lint | 0 erros, 17 avisos |
| APK debug e APK de testes instrumentados | Compilados |

Os testes Node usam identidades e provedores controlados. Os ensaios PostgreSQL
validam scripts em banco descartável, não o projeto Supabase AURON 0.2. Os testes
SQLite verificam a preservação dos registros legados pela migração; não executam
Room ou Android Keystore em um dispositivo.

## APK disponível para validação

Arquivo local: `/workspace/library-files/AURON-0.2.1-validacao-debug.apk`.
Tamanho: **17.953.065 bytes**.

SHA-256: `6f73fbc24dcc9589e43f92c625a1f308b80eddd3d68c3b885db834e34f73d658`.

O APK usa a assinatura debug do CI. **A compatibilidade de atualização sobre a
instalação atual ainda não foi validada.** O usuário informou que o APK original
foi gerado em Android Studio ou outro compilador; a chave original não está
disponível aqui. Não desinstalar o aplicativo nem limpar seus dados para contornar
uma assinatura incompatível.

## Histórico dos bloqueios locais

Antes do CI, Gradle/SDK/kotlinc/ADB estavam ausentes neste ambiente e o build local
terminou com `gradle: command not found`. A rede pelo terminal e o socket Docker
também apresentaram bloqueios. Os 20 testes HTTP falharam localmente em
`listen EPERM 127.0.0.1`; **posteriormente passaram no GitHub Actions**.
Os logs em [verification](verification/) e [static-checks.json](static-checks.json)
registram a validação local anterior; os resultados atuais são os do CI acima.

As correções de compilação preservaram as rotinas, o catálogo e seletor dos
versículos, a persistência, `br.com.auron`, `versionCode=3`, `versionName=0.2.1`
e Room2. A referência visual oficial foi preservada.

## Ainda não testado ou dependente de configuração

1. **36 testes instrumentados:** o APK de testes compilou, mas os testes não
   foram executados. O workflow permite emulador Android16/API36. Para testar os
   quadros da abertura, habilitar animações Android; o teste de movimento é
   ignorado quando elas estão desativadas.
2. **Supabase real:** aplicar/verificar scripts, allowlist e Auth Hook; validar
   cadastro e confirmação do proprietário, login, recuperação, sessão, logout e
   RLS. Configurar a página HTTPS de recuperação e o envio de e-mails conforme
   [AUTH-SETUP.md](AUTH-SETUP.md).
3. **Instalação segura:** comparar pacote, versão e certificado da instalação
   atual e gerar o APK com a chave original antes de recomendar a atualização.
4. **Tablet físico:** testar preservação dos registros, navegação, abertura, voz
   offline instalada, preferências persistidas e comportamento sem rede.

Limitações de produto permanecem: cópia remota manual sem restauração ou
sincronização; voz masculina depende das opções do dispositivo; abertura usa
movimento de câmera sobre imagem 2D. Nenhum serviço real de IA foi validado no CI.
