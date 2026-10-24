# Entrega AURON 0.2.1 — correções e automações

## Implementado

- Login/cadastro: campos editáveis sem conexão previamente configurada, foco e
  navegação pelo teclado; envio exige configuração válida. Autenticação real
  permanece via Supabase, com sessão cifrada e perfil local independente.
- IA: cliente e gateway aceitam sessão Supabase ou token pessoal no modo local.
  Token local cifrado e associado à origem HTTPS; chave de modelo fica no servidor.
  Histórico/memória permanecem opt-in. Sem modelo configurado não há IA real ativa.
- Automação: abrir aplicativos MAIN/LAUNCHER por comando explícito; acesso Android
  às notificações, lista permitida por perfil, leitura com AURON aberto e avisos
  opcionais com intervalo. WhatsApp usa somente RemoteInput disponibilizado pela
  notificação, com rascunho e confirmação separada. Revogação/troca de perfil,
  atualização da notificação e expiração invalidam respostas pendentes.
- Imagem de entrada substituída pelo JPEG exato enviado em 24/10/2026, 1067×1280,
  fundo preto sem painéis. Aproximação do peito seguida de afastamento até revelar
  o corpo inteiro, com transição e prazo máximo preservados.
- Apresentação do painel bíblico ajustada para abrir expandida no tablet. Lógica
  de seleção diária, explicações, dados e confirmação da tarefa permanecem iguais.

## Preservação

Mesmo applicationId `br.com.auron`; versionName `0.2.1`, versionCode `4`.
Nenhuma alteração de chave de assinatura nem esquema Room nesta rodada.
Arquivos de dados, catálogo/seleção bíblica e voz são idênticos aos da versão-base
validada. A única mudança no painel bíblico é a expansão inicial, motivada pelo
resultado do teste de visibilidade. Rotinas, histórico e configurações não são
apagados ou importados automaticamente. A nova autorização de notificações é
armazenada separadamente por perfil.

## Supabase e IA: ações ainda necessárias

A migração e a lista privada do proprietário já foram aplicadas no Supabase real.
A função SQL de cadastro e os privilégios foram verificados; nenhuma conta ou
senha foi criada. No painel do projeto:

1. Em Authentication → Hooks, habilite Before User Created com
   `public.auron_before_user_created`. Mantenha cadastro desativado até isso estar ativo.
2. Configure confirmação de e-mail, URL HTTPS de recuperação e envio de e-mails.
3. Preencha Project URL e Publishable key no AURON → Configurar conexões.
   Nunca use service_role/secret/senha do banco. Crie e confirme a conta com o
   e-mail autorizado; depois desative novos cadastros novamente.
4. O proprietário informou não ter servidor de IA. Prepare o gateway Node e um
   modelo conforme [AI-SETUP.md](AI-SETUP.md). Ollama pode executar no notebook/servidor
   sem cobrança por requisição, mas requer hardware e serviço disponível por HTTPS.
   Nenhum serviço pago foi contratado. O APK sozinho não instala o modelo.

Procedimento detalhado: [AUTH-SETUP.md](AUTH-SETUP.md).
A ativação do Auth Hook não está disponível nas ferramentas administrativas
conectadas; depende do painel. Cadastro/login/recuperação reais e inferência com
modelo real ainda precisam ser exercitados após essa configuração.

## Testes

99 testes JVM, 59 Node, 59 Android16 emulado, 3 testes offline e 9 verificações
estáticas aprovados. SQL de segurança aprovado e lint sem erros (18 avisos).
Resultados finais, falhas encontradas e limites: [VALIDATION.md](VALIDATION.md).
O teste de WhatsApp real, a voz instalada e a instalação por cima do aplicativo
pessoal exigem o Lenovo do proprietário. Emulador não substitui essas verificações.

## Gerar e instalar sem apagar dados

1. Extraia o ZIP e abra a pasta `auron` (contém `settings.gradle.kts`) no Android Studio.
2. Use JDK17, Gradle8.10.2, SDK35/Build Tools34.0.0. Sincronize o projeto.
3. Compile com a mesma configuração de assinatura que gerou o APK instalado.
   Para release: Build → Generate Signed Bundle / APK → APK → escolha a chave original.
   Para debug: o notebook deve preservar a mesma debug.keystore originalmente usada.
4. Confirme pacote `br.com.auron`, versionCode4 não inferior ao instalado, e mesmo
   certificado. Nenhuma chave substituta deve ser criada para contornar erro.
5. Transfira o APK ao tablet, abra-o e escolha **Atualizar**. Não desinstale nem limpe
   dados. Se a assinatura divergir, pare e recupere a chave do compilador original.
6. Confira rotina, histórico, memórias, versículos e voz. Depois teste acesso,
   permissões e respostas conforme [AUTOMATION.md](AUTOMATION.md).

O APK debug gerado no GitHub serve para validação. A assinatura da instalação
pessoal não foi disponibilizada nem comparada; não há garantia de que esse APK
possa atualizá-la diretamente. Instruções completas: [BUILD-0.2.1.md](BUILD-0.2.1.md).

## Principais arquivos alterados

- `ui/AuthScreens.kt`, `ui/AuronApp.kt`, `ui/AuronViewModel.kt`, `MainActivity.kt`:
  acesso, configurações de IA, comandos e integração ao ciclo de vida.
- `ai/*`, `server/*`: autenticação local do gateway, criptografia e validações.
- `automation/*`, `ui/AutomationScreen.kt`, `AndroidManifest.xml`:
  abertura de aplicativos, notificações autorizadas e resposta confirmada.
- `ui/OpeningCamera.kt`, `ui/AuronRobot.kt`, `drawable-nodpi/auron_intro_reference.jpg`:
  imagem exata e enquadramento. `ui/BibleReadingSheet.kt`: expansão do painel.
- `app/build.gradle.kts`, `.github/workflows/android.yml`, testes JVM/instrumentados,
  `scripts/check-source.py` e documentação: versionCode, validação e entrega.

Nenhum módulo futuro, automação oculta, acesso bancário ou voz contínua foi adicionado.
