# Evolução do AURON

## Fase 1 — fundação

Implementação de interface, persistência, tarefas, rotina, histórico, configurações,
demo e adaptadores de voz. Ver [validação](VALIDATION.md) para distinguir código,
compilação e testes executados. A conclusão de aceitação no tablet depende do
checklist de dispositivo, sobretudo voz, acessibilidade e reinício do processo.

## Fase 2 — inteligência e memória

Na 0.2 há implementação de provedor HTTPS substituível, gateway autenticado,
contexto limitado, consentimentos e memória manual cifrada por perfil. Cadastro,
login e recuperação via Supabase foram implementados. A conexão com serviços reais
e a aceitação Android dependem da configuração/testes em [validação](VALIDATION.md).
Não confundir código integrado e testes com fixtures com serviço operacional.

Trabalhos posteriores:

Escolher um modelo local compatível com RAM/chipset do tablet ou serviço explicitamente
autorizado. Comparar licença, consumo, disponibilidade, latência e custo. Implementar
contexto limitado, cancelamento, orçamento, falhas, memória que o usuário possa
revisar/corrigir/excluir, busca e importação de backup. Não presumir que assinatura
de chatbot inclui API. Validar ferramentas internas separadamente de texto gerado.

## Fase 3 — automações

Intents oficiais, notificações Android 13+, lembretes com WorkManager (sem prometer
horário exato), temporizadores e confirmação para efeitos externos sensíveis.
MacroDroid opcional via mecanismos documentados. Nenhum uso de acessibilidade para
contornar autorização. Proatividade com limite de frequência e horários de silêncio.

## Fase 4 — módulos especializados

1. Estudos em ciclos longos: social media/edição, planos, foco e revisão.
2. Clau Estética: serviços, calendário, roteiros, campanhas e métricas manuais;
   identidade branco/rosa/preto/dourado. Publicação só após integração oficial testada.
3. Editor audiovisual: briefing obrigatório, roteiro, storyboard, plano de cortes,
   qualidade e exportação por etapas. Adaptador Remotion somente com ambiente de
   execução real; registrar evidências de análise/render antes de afirmar sucesso.
4. Finanças: valores em centavos inteiros/decimal, testes de arredondamento,
   orçamento, categorias e exportação. Criptografia e bloqueio antes de dados sensíveis.
5. Operações: registro WIN/LOSS, payout e exposição com fórmulas auditáveis, stops e
   sessões. Sem execução de apostas/operações e sem garantia de lucro.
6. Apoio pessoal: diário privado e exercícios, sem substituir acompanhamento clínico.
7. Espiritualidade opcional: leituras, orações e lembretes configuráveis; fontes com
   licença adequada e controle do usuário.

## Fase 5 — expansão

Orquestração de provedores, integrações oficiais autenticadas, voz com baixa latência,
eventual hotword opt-in com indicadores visíveis e respeito às limitações Android.
Revisão de bateria, desempenho, acessibilidade, migrations e testes de longa duração.

## Ordem imediata recomendada

Recuperar assinatura original → compilar/testar 0.2 → configurar/testar cadastro,
login e recuperação → atualizar tablet preservando dados → configurar IA opcional.
Depois: backup restaurável, proteção integral de dados e módulos futuros.
Não iniciar todos os módulos ao mesmo tempo.
