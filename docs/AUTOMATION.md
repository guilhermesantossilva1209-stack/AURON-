# Automações Android — AURON 0.2.1

## Uso e limites

Abra **Módulos → Automações Android e notificações** ou diga/digite `abrir automações`.
O microfone mantém o funcionamento anterior: exige toque para escutar.

- `abrir aplicativo WhatsApp` abre uma Activity MAIN/LAUNCHER exportada. Nomes
  ambíguos exigem selecionar o aplicativo na lista; Android pode negar a abertura.
- Para ler notificações, toque **Autorizar acesso no Android**, confirme a explicação
  e conceda o acesso na tela oficial. Depois ative a leitura neste perfil e marque
  os aplicativos permitidos. Nenhum aplicativo é permitido por padrão.
- `ler notificações` lê até cinco notificações disponíveis, se Falar respostas estiver
  ativado. A tela também oferece leitura individual. Não solicita acesso ao histórico
  do WhatsApp e não consegue recuperar mensagens ausentes/ocultas nas notificações.
- **Avisar sobre novas notificações** é opcional; informa apenas o aplicativo, respeita
  intervalo mínimo e só funciona com AURON aberto. A leitura e os rascunhos são limpos
  ao sair do primeiro plano, bloquear o aparelho, revogar acesso ou trocar perfil.
- No WhatsApp, **Preparar resposta** abre um editor. **Revisar resposta** abre outra
  confirmação com aplicativo, destinatário e texto. Somente **Confirmar e enviar**
  utiliza a ação oficial RemoteInput da notificação, quando disponibilizada.
  `responder no WhatsApp: texto` prepara um rascunho apenas se houver um único
  destinatário elegível; nunca envia. Havendo ambiguidade, selecione a notificação.
- O Android aceitar a ação não comprova entrega da mensagem. Confira no WhatsApp.
- Para revogar: desative a leitura no AURON e use **Gerenciar ou revogar acesso no Android**.
  As notificações originais e os dados dos aplicativos permanecem intactos.

## Privacidade e segurança

O Android concede acesso amplo ao NotificationListenerService, protegido pela
permissão de assinatura BIND_NOTIFICATION_LISTENER_SERVICE. O AURON restringe
adicionalmente o processamento à lista permitida do perfil, ao aparelho desbloqueado
 e ao aplicativo em primeiro plano. Não utiliza AccessibilityService,
QUERY_ALL_PACKAGES nem acesso direto ao armazenamento de outros aplicativos.

Corpos, remetentes e rascunhos ficam em memória volátil, limitados a 30 notificações.
Não são enviados à IA, exportados, salvos no banco nem nos logs. Comandos de automação
só são reconhecidos na entrada explícita do proprietário; conteúdo de notificações
ou respostas do modelo nunca vira comando. A resposta exige um rascunho ligado ao
perfil, à notificação e à revisão, com prazo de dois minutos. Mudança de ação,
remoção, expiração, revogação ou troca de perfil invalida a confirmação.

Preferências de autorização não sensíveis são salvas por perfil, separadas do Room.
A versão do banco não mudou; rotinas, histórico, Bíblia, tarefas e voz foram preservados.

## Verificação no Lenovo Android 16

1. Sem conceder acesso, confirme que nenhuma notificação aparece e que abrir apps funciona.
2. Autorize e permita só WhatsApp. Receba uma mensagem com AURON aberto: confira
   o conteúdo disponível e o aviso opcional, sem leitura automática do texto.
3. Prepare uma resposta, cancele e confirme que nada foi enviado. Prepare outra,
   confira destinatário/texto e confirme uma vez; verifique a entrega no WhatsApp.
4. Com confirmação aberta, receba outra mensagem ou remova a notificação: o rascunho
   deve ser invalidado. Desative leitura/troque perfil: nenhum conteúdo anterior aparece.
5. Revogue no Android e confirme interrupção. Conteúdos sensíveis podem ser ocultados
   pelo Android 16; perfis de trabalho, restrições do fabricante ou instalação lateral
   podem exigir habilitar explicitamente configurações restritas pelo proprietário.

Esses testes reais não foram executados no tablet do proprietário. Os testes automáticos
exercitam políticas e interface; não comprovam entrega real de mensagens pelo WhatsApp.
