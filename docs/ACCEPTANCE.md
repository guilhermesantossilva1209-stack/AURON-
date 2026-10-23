# Aceitação da fundação e atualização 0.2 — AURON

Este é um roteiro de verificação, não um relatório de testes executados. Preencha
os resultados somente depois de realizar cada teste. A compilação de um APK e a
presença de um teste automatizado não comprovam comportamento em um tablet.

## Ambiente e evidências

Registre antes da rodada:

- Data, versão do app e identificação do commit ou ZIP do código.
- Fabricante/modelo, versão Android/API, resolução, orientação e escala de fonte.
- Mecanismos de reconhecimento e TTS instalados; disponibilidade de pt-BR offline.
- Para cada cenário: aprovado, reprovado ou não executado; anexe erro, captura ou
  relatório quando aplicável. Não registre dados pessoais reais nas evidências.

Use preferencialmente emulador, perfil ou dispositivo de teste. Os testes Compose
criam uma tarefa identificada por `Teste AURON` e uma mensagem de boa noite. Não
apagam registros preexistentes. Os testes de Room/DataStore usam arquivos próprios.

## Testes automatizados disponíveis

Com JDK 17, Gradle 8.10.2, Android SDK 35 e as dependências instaladas, na raiz do
projeto (o wrapper ainda não está incluído; consulte o README):

```bash
gradle testDebugUnitTest
gradle assembleDebug assembleDebugAndroidTest
gradle connectedDebugAndroidTest
```

O último comando exige um Android ou emulador conectado, autorizado e visível em
`adb devices`. Para executar somente os testes Compose:

```bash
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=br.com.auron.ui.AuronSmokeTest
```

Relatórios: `app/build/reports/tests/testDebugUnitTest/` e
`app/build/reports/androidTests/connected/`. As localizações exatas podem incluir
subpastas da variante e do dispositivo.

| Camada | Evidência prevista | O que não comprova |
| --- | --- | --- |
| JVM/domínio | Comandos explícitos, acentos, entradas ambíguas, limites e identificação do demonstrativo | Android, áudio ou apresentação visual |
| Room instrumentado | Dados em arquivo após fechar/reabrir o banco, inicialização única e exclusão | Migrações de versões futuras |
| DataStore instrumentado | Preferências após reabrir arquivo e rejeição de valores inválidos | Interações da tela de configurações |
| Compose instrumentado | Navegação, criação/conclusão de tarefa, histórico após recriar Activity e comando noturno | Morte do processo, voz real, todos os tamanhos e acessibilidade |

`ActivityScenario.recreate()` simula recriação da Activity e pode preservar seu
ViewModel. O teste de reinício completo abaixo é complementar e obrigatório para
validar a experiência no tablet. Sem dispositivo, os testes instrumentados ficam
**não executados**, mesmo que o APK de testes compile.

## Checklist no tablet

### Instalação e navegação

- [ ] Instalar o APK de desenvolvimento e abrir sem erro. Não há solicitação de
  microfone durante a abertura; o modo demonstrativo aparece claramente.
- [ ] Acessar Comando, Conversa, Rotina, Tarefas, Histórico, Módulos e Configurações.
  Cada destino apresenta conteúdo e controles utilizáveis.
- [ ] Repetir em retrato e paisagem; a barra lateral aparece em largura suficiente
  e a navegação compacta permanece utilizável em largura menor.
- [ ] Abrir teclado no chat e no formulário de tarefa. Enviar/salvar/cancelar
  continuam acessíveis sem conteúdo essencial cortado.
- [ ] Conferir fonte ampliada, toque e TalkBack. Se houver sobreposição ou controle
  sem nome, registrar como defeito; não declarar acessibilidade completa sem revisão.

### Tarefas e histórico

- [ ] Criar `Revisar roteiro da Clau` com observação, prioridade e prazo válidos.
  Verificar título, prazo e prioridade exibidos.
- [ ] Tentar título vazio e data impossível, como `2026-02-30`: o formulário deve
  explicar o problema sem criar um registro inválido.
- [ ] Editar título, observação e prioridade; conferir os valores atualizados.
- [ ] Concluir a tarefa; ela sai de Pendentes, aparece em Concluídas e no Histórico
  com data/hora de conclusão.
- [ ] Reabrir pelo Histórico; a tarefa volta a Pendentes. Concluir novamente e
  conferir a nova data de conclusão.
- [ ] Cancelar uma exclusão e verificar que a tarefa continua presente. Em um
  registro de teste, confirmar exclusão e verificar sua remoção.

### Persistência após reinício real

- [ ] Criar uma tarefa pendente e outra concluída, marcar uma atividade da rotina,
  enviar uma mensagem e salvar um nome personalizado nas configurações.
- [ ] Esperar as alterações aparecerem na interface. Nas configurações Android,
  usar **Forçar parada** do AURON e abrir novamente. Não usar **Limpar dados**.
- [ ] Conferir as duas tarefas, conclusão no Histórico, rotina do dia, conversa e
  nome salvo. Nenhum registro deve desaparecer nem duplicar.
- [ ] Reiniciar o tablet e repetir a conferência.
- [ ] Depois da virada de data, conferir progresso novo da rotina e preservação
  das conclusões do dia anterior no Histórico. Tarefas comuns não devem reiniciar.

### Rotinas e comandos locais

- [ ] Na primeira instalação, conferir 11 itens matinais e o checklist noturno.
  Editar, adicionar e remover uma atividade de teste; repetir após reinício.
- [ ] Marcar/desmarcar atividade; a contagem e a barra de progresso acompanham a
  alteração. O Histórico agrupa as conclusões por dia.
- [ ] Enviar `Bom dia, AURON.`: abrir rotina Manhã. Enviar `Boa noite, AURON.`:
  abrir rotina Noite. O comando não completa itens automaticamente.
- [ ] Enviar `criar tarefa: Estudar edição`, depois
  `concluir tarefa: Estudar edição`; conferir o efeito real e seu histórico.
- [ ] Enviar `concluir atividade: Beber água` com esse título na rotina;
  conferir conclusão. Se existirem títulos duplicados, pedir identificação em vez
  de completar um item arbitrário.
- [ ] Enviar `Marque a atividade como concluída.` sem título: obter orientação,
  sem modificar tarefas ou rotinas.
- [ ] Enviar `abrir configurações`, `mostre minha rotina` e
  `o que falta fazer?`; conferir navegação e resumo coerentes com os registros.
- [ ] Escrever uma pergunta livre: a resposta deve declarar demonstração local
  e ausência de modelo de IA. Não deve afirmar análise de documentos, memória
  inteligente, pesquisa ou execução externa.

### Configurações, exportação e exclusão

- [ ] Alterar nome, velocidade e preferências de personalidade; salvar, sair e
  voltar. Conferir persistência após Forçar parada. Os parâmetros armazenados
  não significam adaptação inteligente no demonstrativo.
- [ ] Exportar dados; escolher o destino no seletor Android. Abrir o JSON e
  conferir tarefas, rotinas, conclusões, mensagens e preferências esperadas.
  A exportação é legível, contém informações privadas e não oferece importação
  nesta versão.
- [ ] Cancelar o seletor de exportação: retornar ao app sem indicar exportação
  bem-sucedida. Testar destino indisponível quando possível e conferir erro.
- [ ] Cancelar `Limpar conversas`; confirmar que nada mudou. Depois confirmar
  apenas com registros de teste: apagar mensagens e preservar tarefas/rotinas.
- [ ] Em instalação de teste, exportar antes e confirmar `Apagar todos os dados
  locais`. Conferir registros vazios e preferências padrão, inclusive após
  reinício. A rotina inicial não deve reaparecer automaticamente.

### Voz e privacidade

Execute integralmente [VOICE.md](VOICE.md), que cobre permissão negada/revogação,
pt-BR offline, consentimento online, transcrição, interrupção, saída do app,
indisponibilidade do mecanismo e TTS. Registre as combinações testadas.

- [ ] Sem consentimento online, não recorrer a reconhecimento externo quando
  o reconhecimento local faltar. Android 8–11 pode apresentar indisponibilidade
  explícita nessa configuração; texto deve continuar funcionando.
- [ ] Confirmar que indicadores de microfone refletem uma captura real e que
  sair do app cancela a escuta. Não há palavra de ativação ou escuta permanente.
- [ ] Com voz offline instalada e respostas faladas ativadas, testar fala,
  velocidade e interrupção. Ausência de voz deve produzir erro legível.

## Checklist adicional 0.2

### Atualização e contas

- [ ] Confirmar pacote/certificado original e instalar por cima sem desinstalar;
  conferir tarefas, rotina personalizada, histórico e preferências da 0.1.
- [ ] Sem Supabase configurado, cadastro/login ficam indisponíveis de forma clara;
  o perfil local e seus registros continuam utilizáveis.
- [ ] Configurar projeto real e página de recuperação conforme AUTH-SETUP.md.
  Cadastro rejeita e-mail inválido, senha curta, confirmação divergente e ausência
  de consentimento. Cadastro válido solicita confirmação por e-mail quando exigida.
- [ ] Confirmar e-mail; entrar com senha válida; senha incorreta mostra erro sem
  abrir conta. E-mail não confirmado não deve ser tratado como sessão válida.
- [ ] Fechar/reabrir processo e reiniciar tablet; verificar restauração cifrada.
- [ ] Pedir recuperação, abrir link, conferir e-mail, alterar senha, voltar ao app
  e entrar com a senha nova. Link inválido/expirado não altera senha.
- [ ] Sair online/offline; telas privadas desaparecem imediatamente. Reabrir app
  não restaura a conta encerrada. Perfil local continua sendo separado.
- [ ] Entrar em duas contas de teste. Tarefas, memórias, rascunhos e histórico não
  atravessam a troca. Copiar legado exige confirmação e não apaga os originais.
- [ ] Trocar conta durante resposta remota ou com seletor de exportação aberto:
  resposta/exportação anterior não deve usar o perfil novo.

### Memória, IA e leitura

- [ ] Criar, editar, buscar e excluir memória autorizada. Desativar mantém registros
  e impede novas gravações/contexto; consentimento de envio é separado.
- [ ] Configurar modelo real somente se desejar: conversar e verificar histórico e
  memória com flags ligados/desligados. Simular token inválido, rede/modelo ausente
  e timeout. Não apresentar erro como resposta de IA.
- [ ] Tocar atividade bíblica: abrir cinco textos e referências sem marcar como
  concluída. Confirmar no botão, verificar progresso/histórico e reabrir leitura.
- [ ] Reabrir durante o mesmo dia mantém os cinco IDs. No dia seguinte muda o
  conjunto; durante seis dias consecutivos não repete versos. Verificar offline.
- [ ] Comando de concluir a atividade bíblica abre painel e exige botão. Se mudar
  a data com painel aberto, não concluir retroativamente sem reabrir leitura atual.
- [ ] Exportar inclui memórias decifradas e planos diários, sem tokens/senhas.

### Aparência e desempenho

- [ ] Abertura do robô e painéis aparece uma vez por inicialização, pode ser pulada
  e não reaparece ao navegar. Não impede acesso indefinidamente.
- [ ] Desativar abertura/reduzir movimento nas preferências e animações no Android;
  conferir fallback estático e consumo no tablet.
- [ ] Testar retrato/paisagem, tela dividida, teclado, fonte grande e TalkBack em
  login, cadastro, recuperação, memória e painel bíblico.

## Critério de fechamento

A Fase 1 só deve ser declarada validada no dispositivo depois dos cenários
aplicáveis aprovados e das falhas conhecidas registradas. Capacidades dos módulos
Estudos, Clau Estética, Editor Audiovisual, Finanças, Operações, Espaço Pessoal,
Espiritualidade ampliada e integrações externas continuam planejadas; a tela de Módulos
deve identificá-las dessa forma.

Não considerar aprovado um item bloqueado pela ausência de hardware, serviço de
voz ou ferramenta de compilação. Registrar a limitação concreta e o próximo teste
necessário permite continuar o desenvolvimento sem simular uma verificação.


## Validação da atualização 0.2.1 (a executar no tablet de teste)

- Confirmar mesmos dados antigos após instalar com assinatura compatível, sem desinstalar.
- Abertura: close no A e AURON do peito, afastamento até imagem completa, fade,
  Pular, retrato/paisagem, rotação, segundo plano e Reduzir movimento.
- Cadastro: aplicar migração/allowlist/Hook; recusar e-mail não autorizado;
  cadastrar proprietário, confirmar e-mail, desativar novos cadastros.
- Login, senha incorreta, recuperação HTTPS, reinício e logout; sem sessão,
  login aparece. Perfil local só abre por escolha explícita, preservando registros.
- Nuvem: consultar, confirmar envio, consultar contagens; atualizar em duas sessões
  e conferir recusa de revisão antiga. Excluir cópia remota preserva dados locais.
  Verificar indisponibilidade, sessão revogada e conta não proprietária.
- Voz: ouvir opções offline pt-BR, salvar velocidade/tonalidade, reabrir, interromper,
  trocar mecanismo, testar ausência de voz e saída durante inicialização.
- Confirmar rotinas e versículos já existentes, sem mudar sua lógica.

O teste de movimento exige animações Android habilitadas. O workflow padrão com
animações desativadas ignora explicitamente esse teste; fluidez é validação física.
