# Entrega atualizada — 24/10/2026

O relatório atual desta versão, incluindo a Fase 3, está em
[DELIVERY-PHASE3.md](DELIVERY-PHASE3.md) e [VALIDATION.md](VALIDATION.md).
O conteúdo abaixo registra a entrega anterior de 23/10/2026 e é histórico.

# Entrega AURON 0.2.1

## Atualização verificada — 23/10/2026

O código foi publicado no repositório autorizado. Foram corrigidos somente bloqueios
de compilação: pacote SDK descontinuado, importação AuthPolicies, tema com atributo
restrito a API27+ e falso positivo do lint no carregamento assíncrono da abertura.
O [CI final](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/39996099432)
passou: 72 testes JVM, 50 Node, verificações Python e SQL/RLS; lint sem erros.
APK debug disponível no artefato `AURON-APK-e-relatorios`. Instrumentação e tablet
físico não executados. Atualização sobre a instalação existente depende da chave
original, ainda ausente. [Resultados completos](VALIDATION.md).

## Histórico da entrega local — 22/10/2026

O texto abaixo registra o estado anterior à compilação remota; os resultados acima
e em VALIDATION.md substituem os bloqueios de CI descritos neste histórico.

Esta entrega continua o aplicativo existente, pacote br.com.auron, versionCode3.
**O código está integrado, mas não existe APK novo compilado ou validação Android
neste ambiente. Não é uma declaração de conclusão total em produção.**

## Concluído no código; validação Android pendente

- Cadastro inicial com e-mail, senha, confirmação e orientação para acesso exclusivo.
  Login, recuperação, sessão AES/GCM/Keystore e logout preservados. A segurança do
  cadastro exige aplicar e ativar o Hook Supabase; a interface sozinha não o substitui.
- SQL aditivo com lista do proprietário, RLS e RPCs; cliente HTTPS e tela para
  consultar/salvar/excluir cópia manual de tarefas, rotinas, histórico, preferências,
  memórias e conversas. Consentimento, timeout e controle de revisão incluídos.
- Seleção de vozes offline pt-BR, teste de reprodução, velocidade e tonalidade por
  perfil. O usuário escolhe ouvindo as opções; não há voz masculina específica
  garantida nem equivalência comprovada com a voz do ChatGPT.
- Imagem oficial intacta na abertura: close no símbolo do peito e nome AURON,
  zoom para revelar o cenário completo, botão Pular e fade. Movimento de câmera
  sobre imagem 2D; sem animação independente dos membros.
- Componentes integrados ao fluxo existente, tratamento de troca de perfil,
  serialização da cópia fora da interface e manutenção dos arquivos locais.

Rotinas e versículos foram preservados nesta etapa. Não houve reescrita dos
módulos nem alteração da seleção diária nesta finalização.

## Corrigido nesta finalização

- Controles de voz salvos anteriormente agora conectados à tela e às respostas.
- Fluxo de abertura funciona antes do login, com prazo independente da rede.
- Botão da nuvem passou a abrir tela real, conectada às operações do ViewModel.
- Testes/fixtures do gateway atualizados para proprietário autorizado.
- Testes SQL e CI usam o mesmo banco descartável; pipefail impede ocultar erro SQL.
- Documentação atualizada para a versão, referência oficial e dependências reais.

## Arquivos principais

`auth/*`, `cloud/*`, `voice/*`, `ui/AuronApp.kt`, `AuronViewModel.kt`,
`SettingsAndModules.kt`, `VoiceSettings.kt`, `CloudBackupScreen.kt`,
`AuronRobot.kt`, `OpeningCamera.kt`, `OpeningPreferences.kt`, modelos/DataStore,
imagem em `res/drawable-nodpi`, `supabase/`, testes, workflow e documentação.
O inventário completo está em [CHANGES-0.2.1.md](CHANGES-0.2.1.md).

## Testes e limites

Resultados e comandos em [VALIDATION.md](VALIDATION.md). Passaram verificações
estáticas, migração SQLite/catálogo e testes Node do gateway/recuperação.
Testes HTTP com sockets foram bloqueados pelo sandbox. Kotlin, Compose, Room no
Android, Keystore, RLS PostgreSQL, áudio real e login Supabase precisam executar
nos ambientes próprios. A existência de testes não comprova que passaram.

## Parcial e pendente

- Supabase AURON-0.2 não foi configurado remotamente: não há acesso ao painel nem
  URL/chave pública informadas. Nenhuma conta real foi criada ou autenticada aqui.
- Persistência remota implementada como cópia manual. Restauração/mesclagem e
  sincronização bidirecional permanecem pendentes; não há promessa de restauração
  de dados após desinstalação. Não foi iniciada outra fase para implementar isso.
- SMTP e página HTTPS de recuperação ainda precisam de configuração/publicação.
- Gradle, Kotlin e Android SDK não estão instalados. Rede padrão falhou no proxy;
  a solicitação adicional foi abortada sem executar o comando. Nenhum download
  foi mantido em repetição. Compilação/Lint e execução no Lenovo ainda faltam.
- Chave original de assinatura e certificado do APK instalado não estão disponíveis
  aqui. Compatibilidade de atualização ainda precisa ser confirmada.

## O que o proprietário precisa fazer

1. No projeto Supabase existente, aplicar a migração, cadastrar seu e-mail na
   allowlist e ativar Before User Created. Configurar confirmação/recuperação e
   e-mail conforme [AUTH-SETUP.md](AUTH-SETUP.md). Só então liberar cadastro inicial;
   após confirmar sua conta, desligar novos cadastros. Alternativa: criar a própria
   conta pelo painel e manter cadastro público desligado.
2. Preencher Project URL e Publishable/anon no tablet. Nunca colocar service_role,
   senha de banco ou segredos administrativos no APK. Para dados remotos, seguir
   [CLOUD.md](CLOUD.md). Nenhum serviço pago foi contratado.
3. Atualizar os arquivos extraídos no mesmo repositório GitHub e executar o workflow
   existente. O projeto contém processo de build/testes, mas ainda precisa passar
   por ele. Seguir [BUILD-0.2.1.md](BUILD-0.2.1.md).
4. Gerar APK com a assinatura original e instalar escolhendo Atualizar. **Não
   desinstalar nem limpar dados.** Se houver conflito, recuperar a chave original.
   Passos pelo tablet: [UPGRADE-0.2.md](UPGRADE-0.2.md).
5. Testar abertura, voz, autenticação, cópia manual e dados antigos no Lenovo
   Idea Tab TB336FU Android16, conforme [ACCEPTANCE.md](ACCEPTANCE.md).

O próximo trabalho é compilar, instalar e testar esta versão. Não foram iniciadas
voz contínua, controle do tablet, novas automações ou outra fase do produto.
