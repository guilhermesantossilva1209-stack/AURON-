# Voz do AURON 0.2.1

## Implementação e limites reais

O AURON utiliza `TextToSpeech` nativo do Android, sem assinatura ou API de voz
contratada. O catálogo contém somente vozes de português brasileiro que o
mecanismo instalado declara como offline e já instaladas. A lista não inclui
vozes que exigem rede, downloads pendentes ou outros idiomas. Nenhum download
é iniciado automaticamente. A seleção automática usa a qualidade e a latência
informadas pelo mecanismo, não uma avaliação humana da naturalidade.

O Android não oferece metadados confiáveis de gênero. Por isso, o AURON não
adivinha que uma voz é masculina pelo nome técnico. O proprietário pode ouvir
cada opção e escolher a masculina que preferir. A qualidade, expressividade e
naturalidade dependem das vozes instaladas no tablet. Não foi possível ouvir
as opções do Lenovo nesta sessão; nenhuma delas foi validada como equivalente
à voz do ChatGPT. Ajustar a tonalidade não transforma uma voz em outra.

O mecanismo é o padrão escolhido nas configurações de texto para fala do
Android. A interface do AURON permite escolher entre as vozes offline desse
mecanismo. Para trocar de mecanismo, use as configurações Android e, ao voltar,
toque em **Atualizar vozes instaladas**. Se uma escolha salva desaparecer,
usa-se outra voz offline pt-BR com aviso; a preferência original é preservada.
Sem uma alternativa offline disponível, a resposta permanece em texto e o app
orienta instalar os dados de voz. O funcionamento offline depende das
declarações e da implementação do serviço de TTS instalado.

## Controles

Em **Configurações → Voz**:

1. Ative **Falar respostas** para ouvir futuras respostas do assistente.
2. Abra a seleção de voz, escolha uma opção e toque em **Testar voz**.
3. Ajuste velocidade entre 0,5 e 2,0 e tonalidade entre 0,7 e 1,3.
   Um ponto inicial para experimentar é velocidade 1,0 e tonalidade 0,9;
   isso não garante uma voz masculina e pode afetar a pronúncia.
4. **Interromper** encerra a reprodução. O teste não ativa respostas faladas.
5. Toque em **Salvar preferências** para persistir a escolha no perfil atual.

A escolha é armazenada por nome da voz e identificação do mecanismo, junto
com velocidade e tonalidade no DataStore do perfil. Preferências existentes
permanecem; campos novos usam tonalidade 1,0 e seleção automática. A seleção
de outro perfil não altera as preferências anteriores. Não há sincronização
automática de vozes nem transferência de áudio para um serviço contratado.

## Ciclo de vida e falhas

`VoiceController` expõe `StateFlow<VoiceState>` e mantém as operações Android
na thread principal, conforme os contratos de SpeechRecognizer/TTS. A
inicialização assíncrona de TTS tem limite de 12 segundos. Somente a última
fala aguardando inicialização é mantida. Há limite de 12 segundos para o
mecanismo iniciar a fala e limite de conclusão proporcional ao texto/velocidade,
limitado a 15 minutos, caso o mecanismo deixe de enviar callbacks.

`pause()` em `Activity.onStop()` cancela escuta e falas pendentes. `close()`
em `onDestroy()` libera os serviços e temporizadores. Troca de perfil também
chama `pause()`. Resultados atrasados de reconhecimento usam identificação
de sessão e são descartados; eventos de fala interrompida usam identificação
de reprodução. Nenhuma fala deve iniciar depois de uma pausa.

Erros de inicialização, voz removida, reprodução e tempo limite aparecem na
interface. Texto acima do limite de `TextToSpeech.getMaxSpeechInputLength()`
não é truncado silenciosamente: a fala é recusada com mensagem, e o conteúdo
continua no chat. Segmentação de respostas longas ainda não foi implementada.

## Reconhecimento preservado

O microfone só começa após toque e permissão `RECORD_AUDIO`. O AURON prioriza
`createOnDeviceSpeechRecognizer()` em Android 12/API31 ou superior. Sem
consentimento de reconhecimento online, não usa um serviço que possa enviar
áudio para rede. `EXTRA_PREFER_OFFLINE` é apenas uma indicação e não substitui
a escolha do reconhecedor local. Em Android anterior ou sem modelo local,
permanece disponível o uso de texto.

A captura tem limite de 45 segundos e espera até 12 segundos pela transcrição
após encerramento. O áudio não é salvo em arquivo pelo aplicativo. A
transcrição é tratada como mensagem no histórico. Não foram adicionados
escuta contínua, palavra de ativação, identificação de locutor ou serviço em
segundo plano.

## Verificação

Os testes JVM `VoiceSelectionTest` cobrem exclusão de rede/idioma inadequado,
voz não instalada, seleção explícita, ordenação, troca de mecanismo e ausência
de alternativa offline. `SettingsPersistenceTest` cobre persistência dos novos
campos, defaults de perfis antigos, validação e separação por perfil.
`VoiceSettingsTest` cobre o envio de um teste com a seleção atual e a
indisponibilidade do botão sem mecanismo pronto. Os testes de interface usam
estado controlado; não simulam nem comprovam áudio real.

Esses testes Kotlin/Android precisam de Gradle/SDK para execução. Nesta
revisão do módulo não havia essas ferramentas, nem dispositivo Android
conectado. O relatório geral registra qualquer execução posterior. Não houve
teste físico de naturalidade, voz masculina, latência ou consumo no Lenovo.

Roteiro obrigatório no tablet após compilar:

- Instale dados offline pt-BR no mecanismo Android. Em modo avião, escolha,
  ouça, salve, feche e reabra o AURON; confira voz, velocidade e tonalidade.
- Teste com e sem **Falar respostas**. O teste manual deve funcionar sem ativar
  a leitura automática; desligar e salvar deve impedir próximas respostas faladas.
- Remova a voz escolhida ou troque o mecanismo Android e atualize a lista;
  confirme o aviso e a preservação da preferência.
- Interrompa a fala e depois saia do app durante inicialização/reprodução.
  Nenhuma fala atrasada deve iniciar. Repita após troca de perfil.
- Negue/conceda permissão do microfone. Teste o reconhecedor local em modo avião,
  frases não reconhecidas e serviço ausente; confirme os erros sem travamento.
- Teste Android 16 no Lenovo TB336FU. A voz e o comportamento do mecanismo
  do fabricante não podem ser verificados somente com testes JVM ou emulador.
