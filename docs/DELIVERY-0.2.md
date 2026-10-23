# Entrega AURON 0.2 — 19/10/2026

## O que já existia

Aplicativo Android nativo com tarefas, rotinas manhã/noite, histórico, persistência,
configurações, comandos locais e voz por toque. O usuário confirmou instalação e
teste da versão anterior. Na retomada também havia arquivos parciais 0.2 de
autenticação, cliente IA, memória e robô. A inspeção identificou integração pendente;
o projeto foi continuado sobre esses arquivos, sem reinício.

## Implementação concluída no código desta rodada

Cadastro por e-mail/senha com confirmação, erros e consentimento; login Supabase,
pedido de recuperação e página HTTPS para nova senha, sessão cifrada, refresh e
logout. Interfaces preto/dourado integradas à navegação, separação de perfis e cópia
explícita do legado. As operações usam endpoints reais, sem login simulado.

Também foram integrados os itens 0.2 já planejados: memória cifrada controlada pelo
usuário, cliente/gateway IA, contexto autorizado, robô Canvas com painéis e abertura
curta pulável, cinco versículos offline licenciados e confirmação da leitura.
Versionamento e workflow foram preparados para compilar e usar assinatura original.

**“Implementado” não significa aceito no tablet.** Toda a camada Android desta
atualização ainda precisa compilar e executar; o ambiente não permite validá-la.

## Correções

Foram corrigidos limite de resposta IA versus entrada, token/retorno atravessando
troca de conta, exportação usando identidade diferente da inicial, logout antes de
IO, sessão atrasada tentando retornar, falha de memória bloqueando tarefas, API de
leitura incompatível com Android8, divergência de URL HTTPS, rascunhos sensíveis em
saved state e callbacks de voz após mudança de perfil. A migração é aditiva e não
apaga registros. Revisão independente dos contratos complementou os testes locais.

## Principais arquivos

- `auth/*.kt`, `ui/AuthScreens.kt`: cadastro, sessão e configurações públicas.
- `ui/AuronViewModel.kt`, `ui/AuronApp.kt`, `MainActivity.kt`: perfis, gate, fluxo de
  conversa, leitura e exportação vinculados à identidade.
- `data/*.kt`, `domain/Models.kt`, `domain/Repositories.kt`: schema 2, configurações,
  criptografia, leituras diárias e importação transacional.
- `ai/*.kt`, `domain/AiProvider.kt`, `server/`: integração real configurável e testes.
- `ui/AuronRobot.kt`, dashboard, memória, configurações e chat: interface 0.2.
- `bible/*.kt`, `assets/bible_daily.json`, `ui/BibleReadingSheet.kt`: leitura offline.
- Manifesto, app/build.gradle.kts e .github/workflows/android.yml: INTERNET,
  versão 0.2 e compilação/assinatura opcional.
- Testes JVM/instrumentados, scripts de verificação e documentação. A lista completa
  em [CHANGES-0.2.md](CHANGES-0.2.md) compara os arquivos com a base preservada 0.1.

## Resultados verificáveis

| Conjunto | Resultado |
| --- | --- |
| Handler Node com fixtures | 23 passaram |
| Políticas da recuperação | 4 passaram |
| SQL real da migração e ativos locais | 3 passaram |
| Verificações estáticas de fonte/recursos | 9 passaram |
| HTTP em sockets | 19 falharam ao preparar sockets: listen EPERM |
| JVM/Android | 48/29 escritos, nenhum executado |
| Supabase/LLM reais e tablet físico | Não executados |

Comandos, evidências e limites estão em [VALIDATION.md](VALIDATION.md). Os testes
de servidor não provam login real, envio de e-mail nem inferência real. Não há
afirmação de compilação Kotlin ou teste físico. Falhas de infraestrutura não foram
convertidas em resultados aprovados.

## Pendente de configuração e validação

1. Projeto Supabase: URL, chave pública, e-mail/senha, confirmação, envio de e-mails
   e página HTTPS de recuperação. [Passo a passo](AUTH-SETUP.md).
2. Gradle8.10.2, JDK17 e SDK35 em CI/ambiente com rede; compilar e corrigir qualquer
   falha real que aparecer. Executar testes JVM e instrumentados.
3. Assinatura original do APK instalado; ela não está disponível aqui. Validar
   certificado e atualização com dados existentes antes de usar esta versão.
4. Servidor/modelo IA, somente se desejar ativar IA nesta rodada. O modo local e
   cadastro não exigem contratar IA. [Configuração opcional](AI-SETUP.md).

Nenhum serviço foi contratado, publicado ou acessado com credenciais reais.
Serviços hospedados, SMTP e hardware podem ter cotas/custos. Não inclua senhas ou
chaves privadas no chat, no repositório ou no APK.

## APK e instalação

**Não foi gerado APK 0.2:** Gradle/SDK/dependências ausentes e conexão ao proxy de
download indisponível. Não se insistiu em instalações bloqueadas. O ZIP-fonte é a
entrega disponível, não um instalador. Nenhum push foi feito no GitHub; este
workspace não é um checkout Git editável, então a comparação usa a base preservada.

Pelo tablet, atualize as fontes no mesmo repositório e execute o workflow existente
no GitHub Actions. Para atualizar sem perder dados é obrigatório usar a assinatura
original. O procedimento completo está em [UPGRADE-0.2.md](UPGRADE-0.2.md).
**Não desinstale o AURON atual para contornar conflito de assinatura.**

## Fora desta entrega

Automações avançadas, voz contínua, notificações, WhatsApp/Spotify, integrações
bancárias e assinaturas não foram implementados. Módulos de estudos, Clau,
audiovisual, finanças, operações, diário e espiritualidade ampliada seguem no plano.
Restauração de backup, sincronização em nuvem, proteção integral do banco e
biometria também permanecem pendentes, sem promessa de funcionamento.

Retomada exata: [RESUME.md](RESUME.md). A próxima etapa útil é compilação e teste
real de cadastro/recuperação; não reconstruir o aplicativo.
