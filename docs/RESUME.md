# Retomar a entrega AURON 0.2.1

Fonte atual: `/workspace/auron`. Aplicativo `br.com.auron`, `versionCode=3`,
`versionName=0.2.1`, Room2, Android mínimo26 e compile/target35. Não reiniciar o
projeto nem substituir estes arquivos por uma cópia antiga. Snapshots preservados:
`/workspace/auron-preserved-0.2-before-0.2.1` e
`/workspace/auron-preserved-2026-10-22`.

## Estado confirmado em 23/10/2026

O fonte foi publicado no [repositório AURON-](https://github.com/guilhermesantossilva1209-stack/AURON-).
O [CI 39996099432](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/39996099432)
passou no commit `9f2f34e728b54de543769eb38591edc1a1fcee34`:

- PostgreSQL16: migração idempotente e ensaios RLS aprovados.
- 50 testes Node, 9 verificações estáticas e 3 testes SQLite/ativos aprovados.
- 72 testes JVM aprovados; Lint com 0 erros e 17 avisos.
- APK debug e APK de instrumentação compilados; os 36 testes instrumentados
  **não foram executados**.

[Artefato 12520066542](https://github.com/guilhermesantossilva1209-stack/AURON-/actions/runs/39996099432/artifacts/12520066542).
APK local: `/workspace/library-files/AURON-0.2.1-validacao-debug.apk`,
17.953.065 bytes, SHA-256
`6f73fbc24dcc9589e43f92c625a1f308b80eddd3d68c3b885db834e34f73d658`.

**Esse APK debug ainda não tem compatibilidade de assinatura confirmada para
atualizar a instalação atual.** O usuário informou que o APK instalado foi
gerado em Android Studio ou outro compilador. Não há APK original ou chave
original disponível aqui.

## Implementação preservada

- Supabase Auth: cadastro inicial, confirmação de senha, política de proprietário,
  login, recuperação, sessão protegida por Keystore e logout; perfil local explícito.
- SQL: allowlist de um e-mail, Auth Hook, RLS e RPCs de cópia manual com revisão
  esperada. Cliente HTTPS integrado à tela e ao ViewModel; sem sincronização
  automática ou restauração do banco local.
- Voz nativa: seleção offline pt-BR, teste, velocidade, tonalidade, persistência
  por perfil e tratamento de falhas. Nenhuma voz masculina específica é garantida.
- Abertura: imagem oficial, close no peito, zoom até o cenário completo, Pular
  e transição; Canvas original preservado na central.
- Rotinas e cinco versículos diários preservados. Não alterar sua lógica.

## Próximas ações necessárias

1. Localizar a chave original no ambiente que gerou o APK instalado e comparar
   pacote, versão e certificado. Configurar os quatro Secrets indicados em
   [BUILD-0.2.1.md](BUILD-0.2.1.md) e executar `signed_update` com essa chave.
   Não solicitar senhas na conversa, criar chave substituta, desinstalar ou limpar
   dados para contornar incompatibilidade.
2. Concluir a configuração e os testes do Supabase real. O usuário escolheu
   preencher URL/chave pública no tablet. Verificar migração, allowlist, Auth Hook,
   confirmação de e-mail e recuperação HTTPS/SMTP conforme
   [AUTH-SETUP.md](AUTH-SETUP.md) e [CLOUD.md](CLOUD.md).
3. Executar os testes instrumentados em Android16/API36; habilitar animações ao
   validar o movimento da abertura. Não confundir APK de testes compilado com
   testes instrumentados executados.
4. Instalar a atualização assinada no Lenovo Idea Tab TB336FU Android16 e validar
   dados antigos, navegação, voz, abertura, autenticação e operação sem rede.

## Limites e histórico

O CI aprovado superou os antigos bloqueios de compilação local, testes HTTP com
`listen EPERM` e ausência de PostgreSQL. Não repetir instalações de ferramentas
locais só para reproduzir verificações já aprovadas; usar o workflow existente.
O CI não configurou nem testou o projeto Supabase real, não acessou o tablet e
não confirmou a assinatura da instalação atual.

Resultados e limites: [VALIDATION.md](VALIDATION.md).
Entrega: [DELIVERY-0.2.1.md](DELIVERY-0.2.1.md).
