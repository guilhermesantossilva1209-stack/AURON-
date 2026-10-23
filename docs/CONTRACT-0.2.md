# Contrato de implementação 0.2 (integração entre frentes)

Preservar pacote br.com.auron, minSdk26, dependências atuais, aplicação local existente.
Root possui MainActivity.kt, AuronViewModel.kt e documentação final. Cada frente
edita apenas os arquivos atribuídos. Nenhum download/requisição de permissão em loop.

## Models.kt e Repositories.kt (frente data)

Estender RoutineItem com `val action: String? = null` como último campo. Ação
`bible_reading`. Demais campos/ordem preservados. AppSettings adicionar últimos campos:
`useRemoteAi: Boolean = false`, `memoryEnabled: Boolean = false`,
`shareHistoryWithAi: Boolean = false`, `shareMemoryWithAi: Boolean = false`,
`showIntro: Boolean = true`, `reducedMotion: Boolean = false`.

`data class MemoryRecord(val id: String, val title: String, val content: String, val updatedAt: Long)`
`data class DailyReading(val day: String, val verseIds: List<String>)`

AuronRepository mantém tudo e adiciona flows `memories: Flow<List<MemoryRecord>>`,
`readings: Flow<List<DailyReading>>`; suspend `saveMemory(memory: MemoryRecord)`,
`deleteMemory(id: String)`, `getOrCreateReading(day: String, verseIds: List<String>): DailyReading`,
`importLegacyData(): Boolean` (false se já importou; cópia transacional, não apagar legado).

`RoomAuronRepository(context: Context, profileId: String = "local")`. local abre
auron.db original; contas usam nome SHA256 perfil e nunca expõem banco legado sem
importação explícita. `DataStoreSettingsRepository(context: Context, profileId: String = "local")`.
Memória cifrada com Android Keystore AES/GCM. Sem migração destrutiva. Schema v2
aditivo: action em rotina + memórias + planos diários. Importação cópia Room incluindo
IDs e registros, evita sobrescrever dados novos e deduplica; preferências antigas
permanecem no perfil local (não copiar silenciosamente). ClearAll inclui novas tabelas.

## Auth (frente auth owns auth/* e ui/AuthScreens.kt)

`data class ConnectionConfig(val supabaseUrl: String = "", val supabasePublishableKey: String = "", val aiBaseUrl: String = "")`
`data class AuthSession(val userId: String, val email: String, val accessToken: String, val refreshToken: String, val expiresAt: Long)` expiresAt segundos UNIX.
`data class AuthState(val ready: Boolean = false, val session: AuthSession? = null, val localMode: Boolean = true, val busy: Boolean = false, val error: String? = null, val notice: String? = null, val passwordRecovery: Boolean = false)`
`class AuthManager(context: Context)` com `state: StateFlow<AuthState>`,
`config: StateFlow<ConnectionConfig>`; suspend `signIn(email,password)`, `signUp(email,password)`,
`requestPasswordReset(email)`, `signOut()`, `saveConfig(config)`;
`fun enterLocalMode()`, `fun showLogin()`, `fun clearMessages()`;
`suspend fun accessToken(): String?` renova token no vencimento; `fun close()`.
Sessões Keystore AES/GCM, não armazenar senha, anon/publishable somente; rejeitar
service_role/secret. HTTPS obrigatório. Supabase REST real com timeout e erros
legíveis, não salvar chaves de provedor de IA. Recuperação envia e-mail com redirect
configurado no Supabase para página HTTPS hospedada pelo usuário; sem deep-link
inseguro. Documentar procedimento. Autenticação gerenciada não configurada é
explicitamente indisponível, modo local legado continua acessível.

Auth UI composables desacoplados:
`AuthScreen(state: AuthState, configured: Boolean, onLogin:(String,String)->Unit, onRegister:(String,String)->Unit, onReset:(String)->Unit, onLocal:()->Unit, onConfigure:()->Unit)`
`ConnectionSettings(config: ConnectionConfig, busy:Boolean, onSave:(ConnectionConfig)->Unit)`

## IA (frente IA owns domain/AiProvider.kt, ai/*, server/* + testes IA)

Preservar contrato existente AiProvider e DemoAiProvider e adicionar default method
`suspend fun reply(request: AiRequest): ProviderResult = reply(request.input, request.settings)`.
`data class AiRequest(val input:String, val settings:AppSettings, val history:List<ChatMessage> = emptyList(), val memories:List<MemoryRecord> = emptyList())`.
`class RemoteAiProvider(val baseUrl:String, val tokenProvider:suspend () -> String?) : AiProvider`
Implementa reply(input,settings) delegando AiRequest; responde endpoint HTTPS
`/v1/chat`; bearer do Supabase. Contexto limitado, memórias só com consentimento,
sem executar resposta como comando. Cancelamento, limites e mensagens seguras.
Servidor Node sem dependências obrigatórias, autentica token no Supabase antes de
chamar Ollama ou API compatível; endpoint /health, timeouts/tamanho/rate limits,
nenhum log conteúdo/token; chaves provider só env servidor. Testes Node nativos
com fixtures locais claramente separados de teste IA real. Documentar configuração.

## Bíblia (frente bible owns bible/*, assets/bible*, ui/BibleReadingSheet.kt + testes bible)

Fonte textual verificada/licenciada, preservar referência e licença. Sem texto
bíblico inventado. Offline corpus suficiente para rotação; seleção determinística
por LocalDate (mesmo conjunto no dia) sem repetição nos dias recentes de janela
documentada. Registro via repo.getOrCreateReading chamado pelo VM root.

`data class BibleVerse(val id:String, val reference:String, val text:String, val reflection:String)`
`object BibleCatalog { fun load(context:Context): List<BibleVerse> }`
`object DailyVerseSelector { fun select(day: LocalDate, verses:List<BibleVerse>): List<BibleVerse> }`
`fun RoutineItem.isBibleReading(): Boolean` extensão action ou correspondência
conservadora dos títulos conhecidos na manhã (não heurística ampla).
`BibleReadingSheet(day:String, verses:List<BibleVerse>, completed:Boolean, onComplete:()->Unit, onDismiss:()->Unit)`.
Botão Concluir leitura só confirma via VM; tocar de novo permite releitura sem
desmarcar. VM também intercepta comando de concluir atividade bíblica para abrir leitura.

## UI geral (frente ui owns ui/* EXCETO VM, AuthScreens e BibleReadingSheet)

Preservar AuronApp signature para compatibilidade com root e testes.
VM state atual acrescentará `memories:List<MemoryRecord>`, `readings:List<DailyReading>`,
`aiStatus:String = "Demonstração local"`, `profileLabel:String = "Perfil local"`.
VM métodos novos: `saveMemory(id:String?, title:String, content:String)`, `deleteMemory(id:String)`,
`openReading(item:RoutineItem)`, `completeReading(itemId:String, day:String)`, `importLegacyData()`.
Eventos `UiEvent.OpenReading(val itemId:String)`, `OpenAuth`, `OpenConnections`.
VM expõe `auth: AuthManager`; função `requestLogin()` e `logout()` wrapper.

AuronApp coleta auth.state e config. Mostra AuthScreen quando ready && !localMode &&
session==null; nenhum dado de perfil anterior deve ser visível na troca (VM root
limpa estado antes de trocar repositório). ConnectionSettings acessível sem login
via diálogo/configurações. Integra eventos de leitura abrindo sheet; VM oferece
`readingItemId: String?`, `readingDay:String`, `readingVerses:List<BibleVerse>` no state;
`dismissReading()`. UI reading completed usa state.routineCompletions.
Memória screen nova, configurações com consentimento histórico/memória/useRemoteAi,
identidade de sessão, login/logout, importação legado com confirmação explícita.
Scene robot corpo inteiro Canvas reutilizável dashboard+abertura; intro uma vez
por Activity (rememberSaveable), máx ~2s pulável, disabled com reducedMotion e
acessibilidade escala animação do sistema. Nada de prometer vídeo cinematográfico.

## Build/QA (frente build)

Preservar applicationId. versionCode2/versionName0.2.0. Auditar ferramentas e uma
sondagem rede default limitada5s; se sem proxy, parar downloads. Não repetir
permissão adicional travada. Tentar caminhos locais cached; relatório explícito.
CI Gradle direto já existe; acrescentar Node --test server/test e testes migração.
Assinatura release só via keystore existente/env configurado, nenhuma chave criada
para substituir instalação. APK atual/certificado não disponíveis aqui; reportar.
