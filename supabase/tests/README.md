# Ensaios de permissões e persistência PostgreSQL

Use somente PostgreSQL 16 descartável, conectado como administrador a um banco
novo chamado `auron_security_test`. `bootstrap.sql` recusa outro nome de banco e qualquer
schema `auth` ou `auron_private` preexistente. Não execute esses arquivos no
projeto Supabase AURON-0.2 nem em outro banco com dados reais.

```sh
psql -v ON_ERROR_STOP=1 -d auron_security_test -f supabase/tests/bootstrap.sql
psql -v ON_ERROR_STOP=1 -d auron_security_test -f supabase/migrations/202610220001_owner_cloud.sql
psql -v ON_ERROR_STOP=1 -d auron_security_test -f supabase/migrations/202610220001_owner_cloud.sql
psql -v ON_ERROR_STOP=1 -d auron_security_test -f supabase/tests/checks.sql
```

A segunda aplicação verifica que a migração é reaplicável. Cada assertiva é
executada no PostgreSQL sob `anon`, `authenticated` ou `supabase_auth_admin`,
sem SUPERUSER/BYPASSRLS nessas funções. Os testes verificam RLS, privilégios,
e-mail confirmado, bloqueio de outras identidades, hook de cadastro, CRUD por
RPC, revisão otimista, entradas inválidas, exclusão e proteção contra revisão
obsoleta após excluir/recriar. Erros esperados são conferidos por SQLSTATE;
qualquer diferença encerra o processo com falha.

O bootstrap substitui somente `auth.users` e `auth.uid()` por fixtures de ensaio.
Não implementa nem comprova autenticação, validação JWT, funcionamento do hook
no serviço hospedado, e-mail/SMTP ou PostgREST. Esses fluxos precisam ser testados
separadamente no projeto configurado. A sessão emula identidade apenas para
exercitar as regras reais da migração SQL.

Os arquivos foram escritos e revisados nesta execução. Não havia PostgreSQL/psql
disponível no ambiente local; portanto **não foram executados localmente**. Um
resultado futuro do job PostgreSQL no CI é a evidência necessária para afirmar
que essas assertivas passaram. Não trate a existência dos testes como aprovação.
