# Abertura AURON 0.2.1 — imagem oficial

A abertura utiliza o JPEG oficial fornecido pelo proprietário, copiado sem
alteração para `app/src/main/res/drawable-nodpi/auron_intro_reference.jpg`.
Dimensão: 853 × 1280. SHA-256:
`84ef47f3b312be9d5931970f92e63d057fecbab083d8679f7d13e803b365cde7`.

A câmera começa perto do símbolo A e da palavra AURON no peito, afasta-se
suavemente e termina mostrando a imagem inteira com ContentScale.Fit, inclusive
em tablet horizontal. As proporções e os pixels da referência foram preservados;
faixas escuras laterais são esperadas quando a tela é mais larga que a imagem.
Os painéis da ilustração são parte da identidade visual, não indicadores de
módulos implementados.

O movimento é uma transformação de câmera sobre a imagem 2D. Não há animação
independente dos membros nem reconstrução 3D. Esta é a aproximação funcional
à sequência pedida que preserva fielmente a aparência da referência.

## Integração e desempenho

- Prazo de 5,2 segundos, independente de rede e autenticação, seguido de fade de
  320 ms. A câmera reserva tempo para exibir o quadro completo antes da transição.
- Botão Pular disponível imediatamente; falha de decodificação usa o robô vetorial
  existente e não bloqueia o aplicativo.
- JPEG decodificado em Dispatchers.IO, sem escalonamento de densidade. Bitmap de
  aproximadamente 4,2 MiB; transformação em graphicsLayer, sem recriar bitmap a
  cada quadro ou carregar vídeo/biblioteca adicional.
- Abertura uma vez por inicialização da Activity, preservada na recriação de tela;
  navegação entre módulos não reinicia a sequência.
- Preferências Mostrar abertura/Reduzir movimento e animações do Android
  respeitadas. Dois indicadores visuais são guardados fora do perfil para permitir
  a decisão antes do login; nenhum conteúdo pessoal é copiado para esse cache.
- O mascote vetorial original permanece na central de comando. Rotinas, tarefas
  e leitura bíblica não foram substituídas.

## Validação

OpeningCameraTest verifica foco no peito em retrato/paisagem, cobertura no close,
afastamento monotônico e quadro final sem corte. OpeningAnimationTest compara
quadros renderizados, verifica o botão Pular e o fallback vetorial sem movimento.
O teste visual de movimento exige animações do Android habilitadas; com a escala
desativada ele é explicitamente ignorado. O CI padrão desativa animações.

**Kotlin, testes instrumentados e fluidez no Lenovo TB336FU ainda não foram
executados neste ambiente.** A conferência do hash comprova preservação do
arquivo, não execução da animação Android. Após compilar, testar close, quadro
final, Pular, rotação, retorno do segundo plano e preferências de movimento.
