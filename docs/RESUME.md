# Estado final — AURON 0.2.1, Fase 3

Fonte atual: `/workspace/auron`; não reiniciar nem substituir por snapshots antigos.
ApplicationId `br.com.auron`, versionName0.2.1, versionCode4, Room2, min26/target35.
Código validado: `bfdafa97c315853613504f3fdee9db916d4e1743`; [CI aprovado](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/40128363671).

## Concluído nesta etapa

Campos de autenticação, IA opcional em modo local/autenticado, automação autorizada,
notificações com lista por perfil, confirmação WhatsApp via RemoteInput e nova imagem
exata de entrada. Rotinas, versículos e voz preservados. Abertura aproxima a câmera
do peito e depois restaura o corpo completo. Painel de leitura bíblica abre expandido após falha de teste.

99 testes JVM, 59 Node, 3 offline, 9 verificações estáticas, PostgreSQL segurança e
59 testes Android16 passaram. Lint0erros18avisos. APK debug gerado e imagem conferida.
[Resultados completos](VALIDATION.md) e [entrega](DELIVERY-PHASE3.md).

## Pendências externas — não reiniciar desenvolvimento

1. Supabase: migração e allowlist JÁ aplicadas. Ativar Hook Before User Created no
   painel, configurar confirmação/recuperação/SMTP, preencher URL/chave pública no
   tablet e criar/confirmar a conta. Não criar senhas nem publicar e-mail/segredos.
2. IA: proprietário informou que ainda não tem servidor. Instalar gateway/modelo
   conforme AI-SETUP.md; nenhum serviço foi contratado e não há modelo ativo.
3. Usar a chave ORIGINAL do compilador que gerou o APK instalado; não substituir
   a assinatura, desinstalar ou apagar dados para contornar incompatibilidade.
4. Testar no Lenovo Android16 abertura, dados antigos, voz e WhatsApp real. Testes
   instrumentados usam emulador descartável, nunca o perfil pessoal do proprietário.

Esta etapa não contém voz contínua, shell, AccessibilityService, leitura de bancos
WhatsApp nem automação oculta. Notificações são processadas só com AURON aberto.
