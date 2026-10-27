# AURON 0.2.1 — estabilização de login e conversa, 27/10/2026

## Diagnóstico confirmado

O botão de acesso dependia de URL/chave pública previamente salvas. Sem elas, os
campos aceitavam digitação, mas a ação ficava desabilitada. Agora a ação principal
mostra **Configurar para entrar/criar conta/recuperar** e abre as conexões; nenhuma
credencial é enviada nesse caminho. Com a configuração salva, o envio segue o
Supabase Auth real, com validação e indicador de processamento.

Em consulta somente leitura ao projeto AURON 0.2 em 27/10/2026, foram encontrados
**zero usuários Auth**, zero e-mails confirmados, um proprietário permitido e a
função `public.auron_before_user_created(jsonb)` existente. Nos logs disponíveis
das últimas 24 horas havia apenas fontes PostgreSQL/PgBouncer, sem evidência de
requisição Auth. Isso não permite concluir qual configuração está no tablet,
nem comprova que o Hook está ativado no painel. Não criamos conta ou senha.

A mensagem imediata da IA vinha da verificação de URL vazia, antes de qualquer
requisição. O proprietário informou anteriormente que ainda não possuía servidor.
Não houve evidência de servidor/modelo posteriormente configurado. Instalar um APK
ou criar um projeto Supabase não provisiona um modelo de IA.

## Correções implementadas

- Ação explícita para configurar o acesso quando faltam URL/chave; campos, foco e
  IME preservados. Sem cadastro fictício ou autenticação local simulada.
- Mensagens distintas para senha/e-mail recusados, confirmação pendente, cadastro
  desativado, provedor de e-mail, Hook, SMTP, chave pública, limite, DNS, HTTPS e
  timeout. Corpos de erros, senhas e tokens não são exibidos ou registrados.
- Resposta Auth malformada não abre sessão. Revogação conhecida invalida a sessão;
  falha transitória/configuração recusada não apaga a credencial persistida.
- IA: diagnóstico de DNS, certificado, redirecionamento, rota errada, acesso recusado,
  limite, modelo indisponível e resposta inválida. TLS permanece validado e
  redirecionamentos não recebem credenciais.
- Se a IA online estiver sem configuração ou falhar, a conversa oferece a resposta
  demonstrativa local existente, identificada como **AURON · DEMO / Demonstração local**,
  junto da causa. Não é marcada como resposta de modelo. A preferência de IA remota
  não é alterada silenciosamente. Comandos locais continuam independentes.
- Protocolo existente mantido: `POST /v1/chat`; sessão Supabase no modo autenticado,
  token pessoal do gateway no perfil local. Chaves de modelo permanecem no servidor.

## Configuração necessária — proprietário

### Supabase

1. Abra seu projeto em [Authentication → Hooks](https://supabase.com/dashboard/project/rejecmdixeeivueqwxcz/auth/hooks).
   A migração e a lista privada já existem: **não repita nem apague essas estruturas**.
   Ative **Before User Created** com `public.auron_before_user_created` e confira
   que está habilitado. Não habilite cadastro público antes dessa proteção.
2. Em Authentication → Sign In / Providers, habilite e-mail/senha e confirmação
   de e-mail. Configure a URL HTTPS de confirmação/recuperação e o envio de e-mail
   conforme [AUTH-SETUP.md](AUTH-SETUP.md), inclusive as restrições do SMTP padrão.
3. No aplicativo, abra **Configurar conexões**. Project URL:
   `https://rejecmdixeeivueqwxcz.supabase.co`. Copie a **Publishable key** do mesmo
   projeto em Settings → API Keys. Não use senha, `service_role` ou `sb_secret_`.
   Toque **Salvar conexões**. A URL de IA pode permanecer vazia.
4. Com o Hook ativo, habilite novos cadastros somente para o primeiro cadastro
   controlado. No AURON, use **Primeiro acesso do proprietário**, o e-mail que já
   autorizou, senha e confirmação. Confirme as opções de consentimento/configuração.
   Leia a mensagem retornada. Confirme o e-mail recebido e depois use **Entrar**.
5. Após criar e confirmar a conta, desative novamente novos cadastros. Mantenha o
   Hook ativo. Teste também recuperação e logout. Uma senha nunca deve ser enviada
   nesta conversa, salva no código ou incluída no ZIP.

O cliente está implementado, mas a configuração do Hook não é acessível pelo
conector administrativo disponível. Login/cadastro reais ainda precisam dessa
configuração e da conta do proprietário; testes com respostas de ensaio não a substituem.

### IA online

- Sem servidor: use o modo local agora. Ele oferece comandos e respostas fixas,
  não raciocínio por um modelo offline. Não é necessário login para esse modo.
- Para IA real: configure `server/` em uma máquina com Node22, um modelo disponível
  e uma origem HTTPS com certificado válido, seguindo [AI-SETUP.md](AI-SETUP.md).
  Ollama pode rodar no notebook/servidor com hardware compatível; o notebook precisa
  permanecer ligado e acessível. Hospedagem/API pode ter custos; nada foi contratado.
- `GET /health` só comprova que o gateway responde. A validação de inferência exige
  `POST /v1/chat` autenticado e resposta real do modelo.
- No perfil local, configure o token `auron_local_…` gerado na máquina confiável.
  Na conta autenticada, configure URL/chave pública/UUID do proprietário no gateway
  e use a sessão do aplicativo. Nenhuma chave privada pertence ao APK.
- A URL base deve ser a **raiz HTTPS do gateway AURON**: não a URL do painel,
  do Supabase, da API do modelo nem `/v1/chat` anexado. Erros agora explicam essa diferença.

## Entrega e atualização pelo notebook

**ZIP → extrair → Android Studio → gerar APK → instalar como atualização → testar.**
Não é necessário Git Pull, Git, Codespaces ou enviar arquivos manualmente ao GitHub.

1. Extraia `AURON-0.2.1-estabilizacao-20261027.zip` e abra no Android Studio a pasta
   `auron`, que contém `settings.gradle.kts`.
2. Use JDK17, Gradle8.10.2 e SDK35/Build Tools34.0.0. Sincronize. Não reutilize
   `local.properties` de outra máquina; o Android Studio configura seu SDK local.
3. Gere o APK com **a mesma chave e configuração de assinatura do APK instalado**.
   Para release, escolha a chave original em Build → Generate Signed Bundle / APK.
   Para debug, preserve a debug.keystore original do notebook. Não crie uma chave
   substituta se houver incompatibilidade.
4. Pacote `br.com.auron`, versionName0.2.1 e versionCode4 foram mantidos. O código
   da versão não pode ser inferior ao instalado. Não foi alterado o esquema Room.
5. Transfira o APK, abra no Lenovo e escolha **Atualizar**. Não desinstale nem limpe
   dados. O certificado original não está disponível neste ambiente para comparação.
6. Teste primeiro rotinas, histórico, cinco versículos, memórias e voz. Em seguida,
   configure a conta e teste login/cadastro. Sem IA configurada, confirme a resposta
   local identificada; depois de configurar servidor/modelo, teste os dois acessos.

Splash, imagem, tema, voz, automações, catálogo bíblico, banco e seleção de versículos
não foram alterados nesta correção. Nenhuma nova fase foi iniciada.

## Arquivos principais alterados

- `auth/AuthErrors.kt`, `auth/SupabaseAuthClient.kt`, `auth/AuthManager.kt`:
  erros seguros, validação de sessão e pontos de teste do transporte.
- `ui/AuthScreens.kt`: ação de configuração explícita sem alterar a identidade visual.
- `ai/RemoteAiProvider.kt`: diagnóstico do protocolo e validação da resposta.
- `ui/AuronViewModel.kt`: resposta local quando a IA online não está disponível.
- Testes `AuthErrorsTest`, `AuthFlowTest`, `AuthScreenTest`, `RemoteAiTransportTest`
  e `ChatFallbackTest`; documentação e manifesto de integridade.

## Testes e limites

**Aprovados:** 104 testes JVM, 73 Android16 emulado, 59 Node, 3 offline e 9 verificações
estáticas; SQL/RLS e compilação aprovados. Lint: zero erros, 18 avisos.

Consulte [VALIDATION.md](VALIDATION.md) para os comandos, resultados finais e
identificação do código validado. Os testes de transporte utilizam respostas
controladas de ensaio; não comprovam uma sessão real ou inferência externa.
O Lenovo pessoal, entrega de e-mail, conta real, certificado HTTPS do servidor
futuro e modelo real não estão acessíveis para validação nesta execução.
