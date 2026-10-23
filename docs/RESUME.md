# Retomar a entrega AURON 0.2.1

Fonte atual: /workspace/auron. Esta revisão continua o pacote br.com.auron;
versionCode3, versionName0.2.1, Room2. Não reiniciar nem copiar uma base antiga
sobre estes arquivos. Cópias preservadas: /workspace/auron-preserved-0.2-before-0.2.1
e /workspace/auron-preserved-2026-10-22.

## Implementação integrada

- Supabase Auth: criação inicial com confirmação de senha, política de proprietário,
  login, recuperação, sessão Keystore e logout; perfil local preservado e explícito.
- SQL Supabase: allowlist administrativa de um e-mail, Hook de cadastro, RLS,
  RPCs de cópia manual com revisão esperada. Cliente HTTPS e tela ligados ao VM.
  Sem sincronização automática ou restauração do banco local.
- Voz: seleção offline pt-BR, teste, velocidade, tonalidade, persistência por perfil,
  tratamento de falhas e temporizadores. Não garante voz masculina específica.
- Abertura: JPEG oficial, close no peito, zoom para imagem completa, Pular e fade.
  Referência em drawable-nodpi; Canvas original preservado na central.
- Rotinas/versículos preservados. Não alterar sua lógica para prosseguir.

## Próximas ações reais

1. Executar o workflow existente após atualizar os arquivos do mesmo repositório:
   PostgreSQL descartável → testes JVM/Lint/APKs → emulador Android16 opcional.
2. Corrigir qualquer falha real de compilação/testes que aparecer. Não há aprovação
   Kotlin/Android nesta entrega, porque Gradle/SDK/kotlinc/ADB não estão instalados.
3. Usuário preencherá URL/chave pública no tablet. Aplicar migração e allowlist,
   ativar Auth Hook, preparar página HTTPS/SMTP, cadastrar e confirmar proprietário.
   Leia [autenticação](AUTH-SETUP.md) e [nuvem](CLOUD.md).
4. Usar a chave de assinatura original para gerar atualização. Não desinstalar,
   limpar dados nem criar chave substituta prometendo compatibilidade.
5. Instalar no Lenovo Idea Tab TB336FU Android16 e testar voz/abertura/autenticação.

## Limites conhecidos

Nenhum projeto remoto foi configurado, workflow publicado/executado ou APK novo
gerado aqui. Não existe teste físico do tablet. SQL PostgreSQL/RLS foi escrito,
mas precisa executar no job próprio; testes SQLite não validam RLS.

A sondagem de rede padrão falhou no proxy. Uma solicitação de permissão adicional
foi abortada sem execução do comando; não há autorização/resultados para reutilizar.
Não repetir loops de instalação ou aguardar permissão indefinidamente. PostgreSQL
ausente e socket Docker bloqueado. Testes HTTP com sockets falham listen EPERM.

As evidências atuais estão em [VALIDATION.md](VALIDATION.md) e docs/verification.
O relatório desta revisão é [DELIVERY-0.2.1.md](DELIVERY-0.2.1.md).
