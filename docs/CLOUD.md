# Supabase: acesso exclusivo e cópia manual dos registros

## Estado da implementação

Esta revisão inclui SQL, cliente Android HTTPS e uma tela de consulta/envio/exclusão
de cópia por conta. **A migração não foi aplicada ao projeto AURON-0.2 nesta sessão**:
não há acesso administrativo, URL/chave configuradas ou sessão do proprietário.
Nenhum teste de armazenamento no serviço real foi executado. Testes PostgreSQL
descartáveis, quando executados pelo CI, não substituem a configuração do Auth Hook
nem testes reais de confirmação de e-mail e sessão.

O Room continua responsável pelos dados usados diariamente. A cópia remota é um
documento estruturado no formato de exportação AURON v2, com perfil/preferências,
tarefas, rotinas, conclusões, mensagens, memórias e leituras. Não é uma sincronização
bidirecional, não há envio em segundo plano e não há importação automática sobre o
tablet. Consultar ou excluir a cópia remota nunca apaga o banco local.

## Configuração no painel, pelo tablet

1. Abra o projeto **AURON-0.2 → SQL Editor** no navegador. Execute integralmente
   [202610220001_owner_cloud.sql](../supabase/migrations/202610220001_owner_cloud.sql).
   Esse script cria somente estruturas `auron_*` e não altera tabelas Android ou
   contas existentes. Guarde o resultado de execução; se houver erro, não prossiga
   presumindo que as políticas foram aplicadas.
2. No SQL Editor, use [configure-owner.example.sql](../supabase/configure-owner.example.sql),
   substituindo o endereço fictício pelo **seu único e-mail real**, em minúsculas.
   Não publique a cópia editada. O comando não sobrescreve um proprietário já cadastrado.
3. Mantenha cadastros desativados até ativar **Authentication → Hooks → Before User
   Created**, selecionando `public.auron_before_user_created`. O hook é o bloqueio
   de cadastro no servidor. A caixa de confirmação no app não substitui esse hook.
4. Ative o cadastro por e-mail temporariamente, mantenha confirmação de e-mail
   habilitada, faça seu cadastro inicial no app e confirme o e-mail. Depois, desative
   novamente **Allow new users to sign up**. Login e recuperação continuam disponíveis.
   Se o projeto não disponibilizar esse hook no plano escolhido, use a alternativa
   gratuita de criar o proprietário em **Authentication → Users → Add user** e manter
   os cadastros públicos desativados, conforme [AUTH-SETUP.md](AUTH-SETUP.md).
5. Em AURON → Configurações → Conexões, preencha **Project URL** e **Publishable key**
   (ou `anon` legada). Nunca use `service_role`, `sb_secret`, senha de banco ou token
   administrativo. Entre com a conta confirmada do proprietário.
6. Na tela de cópia na nuvem, consulte primeiro. Leia o consentimento, autorize o
   envio e confirme. Consulte novamente para conferir a revisão/data e contagens.

Não foi contratado serviço. Supabase possui plano gratuito sujeito a cotas e
restrições; confira o plano do projeto e as limitações de envio de e-mail antes
de ativar funcionalidades. O app não habilita cobrança nem cria assinatura.

## Proteções implementadas

- Lista administrativa com **um único e-mail** em schema privado, sem privilégios
  para `anon`/`authenticated`. Por padrão a lista está vazia: acesso falha fechado.
- RLS habilitada e forçada na tabela `public.auron_snapshots`; leitura exige
  `auth.uid()` igual ao dono da linha e e-mail do dono confirmado em `auth.users`.
- Clientes não têm INSERT/UPDATE/DELETE direto. RPCs com `search_path` vazio validam
  a identidade real antes de qualquer alteração; não aceitam `owner_id` do cliente.
- Revisão esperada e trava transacional impedem sobrescrever uma cópia que mudou
  desde a consulta. Revisões usam sequência monotônica mesmo após exclusão/recriação.
- A migração não exporta segredos. O cliente envia apenas o JSON de dados existente,
  token de sessão no cabeçalho HTTPS e chave pública apropriada. Não há logs desses
  valores; redirecionamentos são recusados e I/O é executado fora da interface.
- Limite remoto de 1 MiB, resposta limitada e timeout de 35 segundos. Após falha ou
  timeout, consulte antes de repetir: o servidor pode ter concluído a gravação.

As memórias são decifradas localmente para compor o documento **somente após o
envio explícito**. No Supabase, o conteúdo não tem criptografia ponta a ponta do
AURON: administradores autorizados do projeto podem lê-lo. TLS/RLS não equivalem
à criptografia local com Android Keystore. A tela informa isso antes do envio.
Excluir a cópia remove a linha ativa; retenções/backups do provedor seguem as
configurações e políticas do projeto, não são apagados pelo app.

O modo local existente continua preservado e não exige conta. Dados em outras
contas locais não são enviados quando o proprietário faz login. Use a cópia
explícita do perfil local, já existente, somente se desejar associá-los à conta.

## Limites e validação necessária

O cliente permite consultar a revisão e as contagens de um documento recuperado.
Restauração/mesclagem sobre o Room, resolução de conflitos por registro, download
de arquivos anexos e sincronização automática continuam **pendentes**, para evitar
migração destrutiva ou mistura de perfis. Memória não é enviada a um modelo de IA
por esta integração. O provedor de IA existente usa consentimentos independentes.

Antes de usar dados pessoais na nuvem, execute os testes SQL em instância de teste
e valide no projeto: usuário não autorizado recusado no cadastro; usuário sem
confirmação sem acesso; proprietário consegue salvar/consultar; outro usuário não
consegue ler/excluir; revisão antiga não sobrescreve; logout não permite usar o
token de outra conta pelo app. No tablet, confirme que todas as rotinas/versículos
continuam disponíveis offline e após falhas de rede.
