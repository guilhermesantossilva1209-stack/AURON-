# Privacidade da versão 0.2.1

- Permissões: microfone por toque e INTERNET para conexões opcionais HTTPS. Sem
  contatos, localização, arquivos gerais, acessibilidade ou serviço de fundo.
- Nenhum analytics, anúncio, chave de IA embutida ou envio automático de registros.
- Modo local mantém os arquivos da 0.1 e continua acessível sem conta. Não é um
  cofre biométrico: proteja o tablet pelo bloqueio do Android.
- Contas Supabase usam bancos/preferências separados por URL do projeto e ID do
  usuário. Não há sincronização automática; sair da conta esconde o perfil, mantendo
  dados para o próximo login. Outra conta não abre esse banco pela interface.
- Sessão é cifrada com AES/GCM e chave Android Keystore; senha não é gravada. A
  sessão restaurada permite consultar registros offline; revogação no servidor
  só é verificada quando a sessão é usada/renovada. Não promete bloqueio remoto
  imediato sem internet. Logout invalida armazenamento local e tenta revogar no
  servidor. Falhas de revogação são informadas.
- Memória é manual e opt-in, separada do chat. Título e conteúdo são cifrados;
  identificadores e datas são metadados em claro. Desativar não apaga os registros.
  Perda da chave Keystore pode impedir leitura; não há recuperação da chave.
- Tarefas, rotinas, histórico de chat e preferências permanecem no sandbox privado
  em Room/DataStore, **sem cifragem adicional integral do banco**. Não há proteção
  contra root, malware com controle do dispositivo ou tablet desbloqueado.
- IA remota é desativada por padrão. Ao ativá-la, mensagens que não forem comandos
  locais são enviadas ao servidor configurado. Histórico e memórias exigem escolhas
  separadas. O servidor valida o token no Supabase e repassa contexto ao modelo
  escolhido; política de retenção/custos depende desse operador/provedor.
- Respostas de IA são texto; nunca são executadas pelo parser de comandos.
- A cópia manual Supabase exige consulta, consentimento e confirmação separados.
  Inclui memórias decifradas, conversas, tarefas e preferências do perfil atual.
  RLS/RPC restringem o acesso ao proprietário confirmado; administradores do
  projeto podem ler o conteúdo. Não há criptografia ponta a ponta dessa cópia.
  Excluir a linha remota não controla retenções/backups do provedor. Não há
  restauração automática no Room. Consulte [CLOUD.md](CLOUD.md).
- O cache de abertura contém apenas Mostrar abertura/Reduzir movimento, para
  respeitar a apresentação antes de abrir qualquer perfil. A imagem oficial é
  empacotada no aplicativo; não é obtida de um servidor ao iniciar.
- Reconhecimento externo de voz continua opt-in. Serviços Android podem transmitir
  áudio segundo sua própria política. TTS exige voz pt-BR offline disponível.
  Captura e fala param ao sair do app ou trocar perfil.
- Backups e transferência automática são excluídos no manifesto/regras Android;
  mecanismos modificados pelo fabricante precisam de teste no tablet.
- Exportação exige ação e escolha de destino via Android. JSON inclui memórias
  **decifradas**, tarefas, histórico, leituras e preferências. Não inclui tokens,
  senhas ou configuração de conexões. Um destino de nuvem é responsabilidade da
  escolha do usuário; o app não exporta automaticamente. Exportação se vincula
  à identidade que a iniciou e falha se ela mudar.
- Importar dados locais para uma conta é ação explícita de cópia. Originais e outros
  perfis são preservados. Preferências não são copiadas; não equivale a restaurar
  arquivo JSON externo. Restauração de backup JSON ainda não foi implementada.
- “Apagar tudo” afeta somente o perfil aberto, preserva o marcador de importação
  para evitar reaparecimento automático e não apaga conta Supabase, outros perfis
  ou arquivos exportados. Não promete apagamento forense de memória flash.

Para produto comercial ainda são necessários revisão de segurança independente,
bloqueio local configurável, política de retenção/exclusão de conta,
consentimentos e termos do serviço, proteção integral de dados sensíveis e backup
restaurável testado. Nenhuma dessas etapas é anunciada como concluída.
