# Abertura AURON 0.2.1 — imagem oficial

A abertura utiliza o JPEG oficial fornecido pelo proprietário, copiado sem
alteração para `app/src/main/res/drawable-nodpi/auron_intro_reference.jpg`.
Dimensão: 1067 × 1280. SHA-256:
`0cd930a3c3ed9fa6e9648ffceefa7e049527f305d466301649ebfdf9d35f060a`.

A câmera mostra a imagem inteira, aproxima-se suavemente do símbolo A e da
palavra AURON no peito e depois se afasta, restaurando a imagem inteira com ContentScale.Fit, inclusive
em tablet horizontal. As proporções e os pixels da referência foram preservados;
faixas escuras laterais são esperadas quando a tela é mais larga que a imagem.
A nova referência de 24/10/2026 mostra o robô inteiro em fundo preto, sem painéis.
A imagem, o ponto focal e a sequência da câmera foram atualizados; a interface
dos demais módulos permanece preservada.

O movimento é uma transformação de câmera sobre a imagem 2D. Não há animação
independente dos membros nem reconstrução 3D. Esta é a aproximação funcional
à sequência pedida que preserva fielmente a aparência da referência.

## Integração e desempenho

- Prazo de 5,2 segundos, independente de rede e autenticação, seguido de fade de
  320 ms. A câmera reserva tempo para exibir o quadro completo antes da transição.
  A aproximação dura 800 ms após uma pausa de 200 ms; o afastamento utiliza o
  tempo restante. Se a decodificação deixar menos de 1,6 segundo para o movimento,
  a imagem fica inteira e estática, sem atrasar o acesso.
- Botão Pular disponível imediatamente; falha de decodificação usa o robô vetorial
  existente e não bloqueia o aplicativo.
- JPEG decodificado em Dispatchers.IO, sem escalonamento de densidade. Bitmap de
  aproximadamente 5,2 MiB; transformação em graphicsLayer, sem recriar bitmap a
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
os quadros inicial, próximo do peito e final, exigindo movimento nos dois trechos
e restauração exata do quadro completo; verifica também o botão Pular e o fallback
vetorial sem movimento.
O teste visual de movimento exige animações do Android habilitadas; com a escala
desativada ele é explicitamente ignorado. O CI final habilitou animações; o teste visual foi executado e aprovado.

**Compilação, três testes de câmera JVM e testes de abertura no emulador Android16
aprovados em 24/10/2026.** A conferência do JPEG empacotado também comprovou identidade
byte a byte com o anexo. Fluidez no Lenovo físico ainda não foi medida.
Consulte [VALIDATION.md](VALIDATION.md).
