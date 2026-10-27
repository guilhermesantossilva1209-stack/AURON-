# IA conectada — AURON 0.2.1

A implementação conecta o aplicativo a um servidor próprio que consulta Ollama
ou uma API compatível com Chat Completions. Há dois acessos independentes: sessão
Supabase do proprietário ou token pessoal do gateway para o modo local sem conta.
Um modelo, domínio e gateway de IA ainda precisam ser configurados pelo proprietário.
Sem isso, comandos e rotinas locais continuam disponíveis; respostas demonstrativas
continuam identificadas. Se a solicitação remota falhar, o chat apresenta o motivo e
a resposta demonstrativa local, sem marcar sucesso de IA nem alterar o consentimento.
O código de integração não significa que existe um modelo conectado.

## Arquitetura e privacidade

```text
Android -- HTTPS + sessão Supabase --> AURON Node
                                     |-- valida sessão em /auth/v1/user
                                     |-- exige o UUID do proprietário configurado
                                     |-- Ollama no próprio servidor
                                     `-- ou API compatível configurada pelo administrador

Android no modo local -- HTTPS + token pessoal --> mesmo gateway/modelo
                                                 (sem conta Supabase)
```

O Android envia a mensagem atual e quatro números de personalidade quando a IA
remota está ativada. Histórico e memórias só são enviados após consentimentos
separados. Compartilhar memória também exige que a memória esteja habilitada.
Desativar a IA remota impede o provedor de solicitar o token ou enviar conteúdo.
O nome do usuário, IDs e datas dos registros não são adicionados ao contexto.

O contexto usa no máximo 12 mensagens anteriores de até 2.000 caracteres cada,
6 memórias de até 1.000 caracteres e títulos de até 160. O limite agregado é
10.000 caracteres, priorizando memórias recentes e depois mensagens recentes.
Os trechos podem ser cortados nesses limites. A mensagem atual aceita até 4.000
caracteres; o corpo JSON deve caber em 32 KiB. Unicode pode atingir o limite em
bytes antes do limite em caracteres; nesse caso aparece um erro explicativo.

O backend também aplica os consentimentos e valida papéis `user`/`assistant`;
um cliente não pode injetar uma mensagem de papel `system`. Memórias entram como
dados citados. Essa separação reduz instruções indevidas no contexto; não garante
que um modelo nunca produza texto incorreto. **Respostas da IA são somente texto**,
sem ferramentas ou execução automática de comandos no Android.

O backend não mantém conversas nem registra conteúdo, tokens ou chaves em logs.
Ele mantém temporariamente contadores de solicitações do proprietário e por IP.
No modo autenticado, o Supabase recebe somente a sessão para validá-la. No modo
local, o gateway valida o token pessoal sem chamar o Supabase. O token pessoal
nunca é enviado ao modelo. O modelo recebe a mensagem e o contexto autorizado.
As políticas de retenção do provedor escolhido e de qualquer proxy ou hospedagem
precisam ser avaliadas pelo administrador. O histórico local do AURON continua
sob os controles de exportação e exclusão do aplicativo.

## Preparação do servidor

É necessário Node.js 22 ou superior. O backend usa módulos nativos, sem `npm install`.
Ollama exige uma máquina capaz de executar o modelo escolhido. Neste desenho,
“Ollama local” significa **local no servidor**, não inferência dentro do tablet.
Sem computador, use um servidor que você já possua ou providencie hospedagem
compatível conscientemente; instalar o APK sozinho não instala o modelo.

1. Para o modo autenticado, configure um projeto Supabase e obtenha sua URL HTTPS e chave
   publicável/`anon`. Nunca use `service_role` ou `sb_secret_` no app ou nesta
   configuração: o backend rejeita essas chaves. Veja a documentação de autenticação
   para cadastro, confirmação de e-mail e recuperação de senha.
2. Para Ollama, instale o programa por sua distribuição oficial e escolha um modelo
   compatível com a memória/RAM disponível e com a licença pretendida. Baixe o
   modelo conscientemente e confira seu nome com `ollama list`. Mantenha o serviço
   acessível apenas na máquina do backend, normalmente `127.0.0.1:11434`.
3. Dentro de `server/`, copie `.env.example` para `.env`, que já é ignorado pelo Git.
   Substitua todos os valores de exemplo. `AI_MODEL` deve corresponder a um modelo
   realmente instalado. Não coloque chaves em commits, prints ou no código Android.
4. Depois de criar e confirmar a conta autorizada, copie seu UUID em
   **Authentication → Users → User UID** para `OWNER_USER_ID`. O modo de sessão não inicia
   sem esse UUID válido. Ele consulta a sessão real no Supabase e rejeita outra
   conta antes de enviar qualquer mensagem ao modelo. Um ID enviado no corpo da
   requisição não altera essa autorização. O UUID é um identificador, não uma
   chave administrativa; ainda assim, não foi preenchido com um valor inventado.

Configuração Ollama:

```dotenv
SUPABASE_URL=https://SEU_PROJETO.supabase.co
SUPABASE_PUBLISHABLE_KEY=SUA_CHAVE_PUBLICAVEL_OU_ANON
OWNER_USER_ID=UUID_REAL_DO_PROPRIETARIO
LOCAL_ACCESS_TOKEN_SHA256=
AI_PROVIDER=ollama
AI_PROVIDER_URL=http://127.0.0.1:11434
AI_MODEL=NOME_EXATO_DO_MODELO_INSTALADO
AI_PROVIDER_KEY=
PORT=8787
REQUEST_TIMEOUT_MS=45000
RATE_LIMIT_PER_MINUTE=20
MAX_CONCURRENCY=4
```

Inicie na pasta `server/`:

```bash
node --env-file=.env server.mjs
```

O processo escuta somente em `127.0.0.1`. Publique-o por um proxy HTTPS com
certificado válido. Exemplo de configuração Caddy, após configurar seu domínio,
DNS e acesso às portas necessárias:

```caddyfile
auron.seudominio.example {
    reverse_proxy 127.0.0.1:8787
}
```

O domínio acima é ilustrativo e precisa ser substituído. Não foi registrado nem
publicado. Evite logs de corpos e cabeçalhos de autorização no proxy. Ollama não
deve ser publicado diretamente na internet.

`GET /health` retorna `{"status":"ok"}` sem autenticação. Isso confirma somente
que o processo responde; não verifica sessão, disponibilidade do modelo ou inferência.
`POST /v1/chat` aceita `Authorization: Bearer <access_token>` de uma sessão validada
pelo projeto Supabase configurado, ou `Authorization: AuronLocal <token>` quando
esse acesso foi provisionado explicitamente. A chave publicável sozinha não autoriza
inferência. No modo de sessão, o usuário também precisa ser o `OWNER_USER_ID`.
Criar uma conta no mesmo projeto não concede acesso ao servidor de IA. A política
de cadastro inicial e o Auth Hook estão em [AUTH-SETUP.md](AUTH-SETUP.md).

## IA no modo local, sem criar conta

O modo local não exige login, mas um servidor de IA exposto à rede não pode ficar
sem proteção. A credencial abaixo é exclusiva do gateway AURON e permite inferência;
não concede acesso ao banco Supabase nem substitui a chave do provedor.

1. Na sua máquina confiável, dentro de `server/`, execute:

   ```bash
   node generate-local-access.mjs
   ```

2. O comando gera 32 bytes aleatórios e exibe um token `auron_local_…` e seu hash.
   Guarde o token em local privado para inserir no tablet. Copie **somente** a linha
   `LOCAL_ACCESS_TOKEN_SHA256=…` para o `.env` privado do servidor e reinicie-o.
   Não envie esses valores na conversa nem inclua o token no código, em logs,
   capturas ou commits. O repositório contém apenas o gerador, sem uma credencial real.
3. Para usar apenas o modo local, deixe `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`
   e `OWNER_USER_ID` vazios no `.env`. Para aceitar ambos os modos, configure também
   os três campos reais do Supabase. Uma configuração parcial falha ao iniciar;
   nenhum modo é ativado como alternativa automática quando o outro falha.
4. No tablet em modo local, configure a URL HTTPS do gateway e o token pessoal
   na área de conexões. Ative a IA remota nas preferências do perfil local.
   O token é criptografado com Android Keystore, excluído dos backups e vinculado
   à origem HTTPS. Mudar a URL não encaminha o token antigo a outro servidor.
5. Para revogar o acesso, gere um novo token/hash, substitua o hash no servidor e
   reinicie o processo. Remova o token antigo das conexões do tablet. A remoção
   dessa credencial não apaga rotinas, memórias, sessão ou preferências.

O gateway compara o hash em tempo constante, limita requisições e compartilha o
mesmo orçamento entre os dois modos. Ainda é necessário instalar/configurar um
modelo no servidor: “modo local sem conta” não significa inferência offline no tablet.

## Alternativa: API compatível

Use apenas se você decidir configurar o provedor e aceitar seus custos e termos:

```dotenv
AI_PROVIDER=openai
AI_PROVIDER_URL=https://PROVEDOR_EXEMPLO/v1
AI_MODEL=MODELO_ESCOLHIDO
AI_PROVIDER_KEY=SEGREDO_APENAS_NO_SERVIDOR
```

O backend acrescenta `/chat/completions` à URL base e usa mensagens, `stream:false`
e `max_tokens:1024`. A compatibilidade depende do serviço: APIs que exigem outro
formato, outro nome de parâmetro ou uma API de Responses precisam de adaptador.
A ausência de chave só funciona para serviços que explicitamente não a exigem.
Não há descoberta automática de modelos, streaming ou escolha de modelo no app.

Nenhum consumo pago foi feito nesta entrega. Software aberto não garante custo
zero de hardware, energia, armazenamento, domínio ou hospedagem. Supabase hospedado
pode ter cotas ou cobrança conforme o plano; serviços de inferência podem cobrar
por uso. Assinatura de chatbot não inclui automaticamente acesso à API. Consulte
os preços atuais do serviço antes de configurar; este projeto não contém preços
estimados nem orçamento automático. Os limites de requisições reduzem volume,
mas não substituem um limite de gastos no provedor.

## Configuração no tablet

1. Abra as conexões e informe a URL HTTPS de
   origem do backend, por exemplo `https://auron.seudominio.example`, **sem** `/v1/chat`,
   parâmetros, fragmentos ou credenciais na URL.
2. Para o modo autenticado, configure URL/chave pública Supabase e entre na sua
   conta. Para o modo local, configure o token pessoal conforme a seção anterior.
   Sessões são obtidas/renovadas pela camada de autenticação; o Android nunca
   solicita nem armazena a chave do provedor de IA.
3. Ative IA remota nas preferências. Compartilhe histórico e memória somente se
   desejar. Esses controles ficam desligados por padrão.
4. Envie uma pergunta livre. Comandos locais conhecidos continuam no aplicativo;
   uma resposta remota nunca deve ser reenviada ao parser como comando do usuário.
5. Se houver erro de sessão, entre novamente. Se o modelo estiver indisponível,
   confira servidor/modelo/rede. O erro não deve ser apresentado como resposta
   inteligente e não dispara compra, contratação ou fallback pago.

## Limites operacionais

- Toda inferência autenticada valida a sessão pelo Supabase e compara seu UUID com
  `OWNER_USER_ID`. No modo local, valida-se o token pessoal provisionado. Erro,
  timeout, credencial ausente/inválida ou conta diferente bloqueiam a inferência.
- Padrão de 20 pedidos por minuto para o proprietário (ambos os modos), 4 requisições simultâneas
  e 120 tentativas por minuto por endereço de conexão. `X-Forwarded-For` não é
  confiado; atrás de um proxy, o limite por conexão pode ser compartilhado.
- Contadores ficam na memória deste processo, são limitados em quantidade e
  reiniciam com ele. Vários processos exigem coordenação externa para limite global.
- Backend: prazo total padrão de 45 segundos, corpo máximo de 32 KiB, resposta
  upstream máxima de 64 KiB, texto final de até 16.000 caracteres e orçamento
  de saída de até 1.024 tokens solicitado ao modelo.
- Android: conexão 10 segundos, leitura 55 segundos, teto de 65 segundos para
  a operação HTTP e cancelamento com desconexão. Redirecionamentos não são
  seguidos; erros não mostram corpos upstream, credenciais ou stack traces.
- Falhas de autenticação retornam 401; conta diferente do proprietário, 403;
  formato inválido 400/413/415; limites 429;
  modelo indisponível 502; serviço de autenticação indisponível 503; timeout 504.
- Não há sincronização de histórico/memória em nuvem neste backend, armazenamento
  de sessões, execução de ações, transcrição ou geração de áudio.

## Verificação e limites desta entrega

```bash
cd server
node --check server.mjs
node test/handler.test.mjs
node test/server.test.mjs
```

`test/server.test.mjs` usa servidores HTTP locais de fixture, incluindo autenticação
e respostas de modelos simuladas. Esses testes exercitam o transporte real em
loopback, mas **não** comprovam um Supabase real nem um modelo Ollama/API real.

`test/handler.test.mjs` usa o handler real com streams locais e `fetch` injetado,
sem abrir sockets. Essa suíte permite verificar autenticação, consentimentos,
limites, isolamento e erros quando o ambiente proíbe `listen()`. Ela não verifica
rede, TLS, conectividade ou qualidade de respostas do modelo.

O ambiente local desta execução bloqueia a suíte HTTP com
`listen EPERM: operation not permitted 127.0.0.1`; o workflow GitHub executa essa
suíte em um runner com loopback disponível. Consulte o relatório de validação
para os resultados efetivamente obtidos. Testes JVM cobrem o contexto e a separação
de credenciais antes da rede. Testes instrumentados de `LocalAiAccessStoreTest`
verificam criptografia, persistência, vínculo à origem e remoção isolada; só contam
como executados quando houver resultado de emulador/dispositivo, não apenas compilação.

Antes de considerar a integração validada, execute no seu ambiente: login real,
pergunta livre ao modelo realmente instalado, histórico/memória com consentimentos
ligados e desligados, token expirado/revogado, backend e modelo indisponíveis,
timeout, cancelamento ao trocar de perfil e rejeição de outra conta antes da inferência.
No modo local, teste token correto, inválido, removido/revogado e mudança da URL.
Não há evidência de inferência real ou validação TLS no tablet nesta entrega.
