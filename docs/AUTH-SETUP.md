# Cadastro privado e autenticação — AURON 0.2.1

O aplicativo usa endpoints reais do Supabase Auth por HTTPS para cadastro,
login, recuperação, renovação e logout. Em 24/10/2026, a migração existente foi
aplicada ao projeto **AURON 0.2** e o e-mail informado pelo proprietário foi
registrado na lista privada. Nenhuma conta/senha foi criada automaticamente;
a consulta encontrou zero usuários Auth. O e-mail não foi colocado no repositório.

A função SQL de cadastro foi verificada: proprietário permitido, outro e-mail
recusado e evento sem e-mail recusado. RLS está ativada e forçada; anon não pode
consultar a cópia, authenticated não pode inserir diretamente nem ler a lista
privada. Isso **não comprova** login/cadastro HTTP ou envio de e-mails: o Auth Hook
ainda precisa ser ativado no painel. O conector disponível não oferece essa
configuração administrativa. URL/chave pública serão preenchidas no tablet,
conforme escolhido pelo proprietário. Nunca inserir chave administrativa no APK.

O cadastro inicial exige uma política no servidor: um Auth Hook permite somente
o e-mail escolhido pelo proprietário. A confirmação na tela é uma declaração de
que a configuração foi feita, **não uma barreira de segurança**. Uma requisição
fora do app também precisa ser recusada pelo Hook. RLS protege os dados da nuvem
separadamente. Não habilite cadastro antes de ativar e testar essa política.

## O que configurar, sem enviar segredos na conversa

1. No navegador do tablet, abra o painel do projeto **AURON-0.2** que você administra.
   Pode iniciar por uma oferta gratuita disponível na sua conta, respeitando cotas.
   Não selecione planos pagos sem decidir conscientemente. Este projeto não contrata
   serviço algum. A alternativa de código aberto é hospedar Supabase Auth, o que
   exige servidor, manutenção e HTTPS; não é inferência/auth inteiramente no tablet.
2. Mantenha **Allow new users to sign up** desativado durante a preparação.
   **No projeto AURON 0.2 desta entrega, a migração e a lista privada já foram aplicadas.**
   Não é necessário repetir. Em outro projeto, no **SQL Editor**, aplique
   [202610220001_owner_cloud.sql](../supabase/migrations/202610220001_owner_cloud.sql).
   Depois abra [configure-owner.example.sql](../supabase/configure-owner.example.sql),
   substitua o e-mail de exemplo **pelo seu próprio e-mail** e execute essa cópia
   no painel. Não publique a cópia com seu e-mail no repositório. A tabela privada
   aceita um proprietário; o script não substitui um proprietário já cadastrado.
3. Em **Authentication → Hooks**, adicione/ative **Before User Created** usando
   a função PostgreSQL **public.auron_before_user_created**. O SQL cria a função,
   mas **não ativa a configuração do Hook no painel**. Confira que está habilitada.
   Ative e-mail/senha, mantenha confirmação de e-mail e senha mínima de oito
   caracteres. Só agora habilite **Allow new users to sign up** para seu primeiro
   cadastro. CAPTCHA/MFA não têm interface nesta versão; não habilite requisitos
   incompatíveis sem estender e testar o cliente.
4. Obtenha somente **Project URL** e **Publishable key** (prefixo sb_publishable_).
   Chave legada anon também é aceita. Jamais use service_role, sb_secret_, senha de
   banco ou chave de provedor de IA. Chave pública identifica o projeto e não é
   credencial administrativa. O login não envia tarefas, memórias ou conversas.
   O envio de uma cópia para a nuvem exige uma ação e consentimento separados.
5. Prepare a página HTTPS de confirmação/recuperação descrita abaixo e configure
   essa URL como **Authentication → URL Configuration → Site URL**. Inclua a URL
   exata nas URLs de redirecionamento permitidas. Não deixe localhost como Site URL.
6. Verifique o envio de e-mails. O SMTP padrão do Supabase é restrito a endereços
   da equipe do projeto, com cota muito pequena; não serve como entrega pública
   garantida. Para outros destinatários configure SMTP autorizado no próprio painel.
   Usuário/senha SMTP ficam no serviço, nunca no APK. Provedores podem oferecer
   cotas gratuitas ou cobrar; confira limites antes de configurar.
7. No AURON: **Configurar conexões → URL Supabase e chave pública → Salvar conexões**.
   A URL de IA pode ficar vazia. Use **Primeiro acesso do proprietário**, informe
   exatamente o e-mail autorizado, senha e confirmação. Confirme que o Hook está
   configurado, autorize o envio e toque **Criar minha conta privada**.
8. Confirme pelo link recebido e volte ao aplicativo para fazer login. Se a política
   do projeto retornar sessão imediata, o app a aceita; caso contrário informa
   que é necessário confirmar. Repetir cadastro não é forma de recuperar senha.
9. Após concluir seu cadastro e confirmação, desative novamente **Allow new users
   to sign up**. Mantenha o Hook ativo. Confira em **Authentication → Users** que
   apenas sua conta autorizada existe; não apague outras contas ou dados sem avaliar
   sua origem. O Hook restringe novos cadastros, não remove usuários anteriores.

Se preferir não habilitar cadastro temporário, o proprietário pode criar sua própria
conta em **Authentication → Users → Add user** e manter novos cadastros desligados.
Ainda é preciso configurar a lista autorizada para acessar os dados RLS. Não use
confirmação administrativa para um e-mail que não é seu. O app não possui nenhuma
chave ou permissão para administrar usuários ou executar migrações.

## Página de recuperação incluída

Arquivos em [recovery/](recovery/index.html), sem bibliotecas externas:

- Edite `settings.mjs` com a mesma URL HTTPS raiz e **chave pública** do projeto.
- Em `index.html`, substitua apenas a origem de exemplo no `connect-src` da política
  CSP pela URL exata desse Supabase. Não use `*` nem inclua domínios desnecessários.
- Publique `index.html`, `settings.mjs`, `recovery.mjs`, `policy.mjs` e `style.css`
  juntos em hospedagem estática HTTPS sob seu controle. Por exemplo, GitHub Pages
  se disponível na conta/plano escolhido; publicar arquivos deve ser decisão sua.
  O código não publica automaticamente. Sirva `.mjs` como JavaScript.
- Configure a URL pública completa terminando em `/index.html` como Site URL no
  Supabase. Preserve os templates de e-mail que usam o link de confirmação oficial.
- A integração usa o fluxo implícito do Supabase Auth: o link de recuperação entrega
  `type=recovery` e access_token no fragmento. Não espera um código PKCE. Se o projeto
  usa template/fluxo diferente, ajuste e teste antes de considerar recuperação pronta.

A página remove o fragmento da barra/histórico assim que inicia, não salva tokens
em cookies/localStorage e valida a conta no Supabase antes de exibir o formulário.
Mostra o e-mail da conta para conferência. A nova senha é enviada por PUT ao endpoint
de usuário autenticado; o token permanece só em memória. Depois tenta encerrar a
sessão de recuperação e pede retorno ao app. Sem link válido, não altera senha.
Após confirmação de cadastro, orienta voltar ao AURON, sem reaproveitar o token de
cadastro como link de recuperação. Recarregar a página exige abrir novamente o link.
Não adicione analytics, scripts de terceiros ou logs de credenciais a essa página.

## Sessão e dados existentes

Tokens de acesso/renovação são cifrados via AES/GCM com Android Keystore, em arquivo
sem backup. A senha não é persistida. Renovação ocorre antes de usar um token perto
do vencimento; falhas geram mensagem, nunca acesso remoto anônimo. Sair fecha a
identidade imediatamente, remove sessão persistida e tenta revogá-la no servidor.
Falha de rede na revogação não mantém a conta aberta neste aparelho.

A primeira abertura sem preferência anterior mostra login. **Continuar no modo
local** abre explicitamente os registros antigos sem senha; não abre os dados da
conta. Essa escolha é persistida. O logout volta ao login e preserva todos os
bancos de perfil, sem transferir dados para o perfil local.

A sessão restaurada permite consultar dados da conta já armazenados offline. Isso não revalida uma
revogação remota até o próximo uso/renovação da sessão. O login não substitui o
bloqueio físico do Android, nem fornece biometria. Consulte [privacidade](PRIVACY.md).

Cada conta tem armazenamento próprio no aparelho; não há sincronização automática
entre dispositivos. A cópia opcional na nuvem é independente do login e não
substitui o banco operacional local. O perfil local anterior fica separado. Depois de entrar, a ação
**Copiar dados do perfil local** pede confirmação e faz uma cópia, sem apagar a
origem nem substituir registros editados na conta. Preferências não são copiadas.
Trocar contas nunca importa dados automaticamente.

## Aceitação ainda necessária

Em um projeto/instalação de teste, verificar primeiro que uma requisição de cadastro
com e-mail diferente do autorizado recebe recusa e não cria usuário. Verificar
também acesso anônimo negado e conta diferente sem acesso aos registros RLS. Só
essa validação no serviço confirma que o Hook foi realmente ativado no painel.

Verificar cadastro válido, senha divergente, e-mail inválido,
senha incorreta, conta não confirmada, link expirado, recuperação completa, login
com a senha nova, logout offline, reinício do processo e perfis separados sem cruzamento
de tarefas/memórias. Para a edição pessoal, outra conta existente deve ser recusada
na nuvem e pelo gateway de IA. Testar rede indisponível e configuração incorreta. Nenhum desses
fluxos externos foi realizado com uma conta real nesta entrega.

Os testes Kotlin de políticas e Android de tela/Keystore foram escritos, mas não
executados aqui por falta de Gradle/SDK. Quatro testes Node da política de recuperação
passaram; eles não provam envio de e-mail nem alteração real de senha. Consulte
[validação completa](VALIDATION.md).

Referências oficiais para configuração (os links não substituem testes no projeto):
- [Autenticação por senha e recuperação](https://supabase.com/docs/guides/auth/passwords).
- [SMTP e restrições de envio](https://supabase.com/docs/guides/auth/auth-smtp).
- [Hook Before User Created](https://supabase.com/docs/guides/auth/auth-hooks/before-user-created-hook).

As cotas e condições externas podem mudar. Não é necessário contratar IA para
testar cadastro e login.

## Limite da auditoria remota

Os advisors ainda sinalizam as RPCs SECURITY DEFINER autenticadas. Elas são
intencionais, validam auth.uid(), proprietário confirmado e revisão antes de
acessar a cópia. Os testes de segurança SQL passaram no banco descartável do CI.
O projeto já tinha `public.rls_auto_enable()` retornando event_trigger, associado
à ativação automática de RLS; os advisors também sinalizam seu privilégio EXECUTE.
A função preexistente foi apenas inspecionada e não alterada. Referências:
[anônimo](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable)
e [autenticado](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable).
