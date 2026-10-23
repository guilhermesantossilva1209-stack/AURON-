# Contrato interno da Fase 1

Pacote `br.com.auron`. Android minSdk 26, compile/target 35. Kotlin 2.0.21,
Compose BOM 2024.12.01, AGP 8.7.3, Gradle 8.10.2, Java 17.

## domain (modelos sem Android)

`enum class Period { MORNING, NIGHT }`

`data class Task(val id: String, val title: String, val notes: String = "", val priority: Int = 1, val dueDate: String? = null, val createdAt: Long, val completedAt: Long? = null)`

`data class RoutineItem(val id: String, val title: String, val period: Period, val orderIndex: Int)`

`data class RoutineCompletion(val itemId: String, val day: String, val title: String, val period: Period, val completedAt: Long)`

`data class ChatMessage(val id: String, val role: String, val text: String, val createdAt: Long, val source: String = "text")`

`data class AppSettings(val userName: String = "comandante", val speakReplies: Boolean = false, val speechRate: Float = 1f, val allowOnlineSpeech: Boolean = false, val humor: Int = 95, val sarcasm: Int = 60, val formality: Int = 55, val proactivity: Int = 90)`

`interface AuronRepository`: flows `tasks: Flow<List<Task>>`, `routineItems: Flow<List<RoutineItem>>`, `routineCompletions: Flow<List<RoutineCompletion>>`, `messages: Flow<List<ChatMessage>>`; suspend `initialize()`, `saveTask(task: Task)`, `deleteTask(id: String)`, `setTaskCompleted(id: String, completed: Boolean, now: Long)`, `saveRoutine(item: RoutineItem)`, `deleteRoutine(id: String)`, `setRoutineCompleted(item: RoutineItem, day: String, completed: Boolean, now: Long)`, `addMessage(message: ChatMessage)`, `clearMessages()`, `clearAll()`.

`interface SettingsRepository`: `settings: Flow<AppSettings>`; suspend `update(settings: AppSettings)`, `reset()`.

`sealed interface LocalCommand` com `data object Morning`, `Night`, `ShowRoutine`, `Pending`, `Help`, `data class CreateTask(val title: String)`, `CompleteTask(val title: String)`, `CompleteRoutine(val title: String)`, `Navigate(val destination: String)`, `Unknown`. `object CommandParser { fun parse(text: String): LocalCommand }`. Destinations: dashboard, chat, routine, tasks, history, modules, settings. Match explícito, nunca inferir execução externa. Só o texto enviado pelo usuário é analisado.

Provedor: `interface AiProvider { val id: String; val isDemo: Boolean; suspend fun reply(input: String, settings: AppSettings): ProviderResult }`, `sealed interface ProviderResult { data class Success(val text: String) : ProviderResult; data class Unavailable(val reason: String) : ProviderResult }`, `class DemoAiProvider : AiProvider`.

## data

`class RoomAuronRepository(context: Context) : AuronRepository` e `class DataStoreSettingsRepository(context: Context) : SettingsRepository`. Banco privado, backup desativado, sem criptografia própria nesta fase (documentar). Inicialização única transacional de rotinas. Excluir tudo não deve semear novamente sem solicitação; initialize só no primeiro uso. `clearAll` apaga conteúdo incluindo rotinas. Exportação JSON implementada por root via snapshots de estado, SAF.

## ViewModel / UI (root implementa ViewModel)

`data class AuronUiState(val ready: Boolean = false, val tasks: List<Task> = emptyList(), val routineItems: List<RoutineItem> = emptyList(), val routineCompletions: List<RoutineCompletion> = emptyList(), val messages: List<ChatMessage> = emptyList(), val settings: AppSettings = AppSettings(), val day: String = LocalDate.now().toString(), val error: String? = null, val busy: Boolean = false)` em `br.com.auron.ui`.

`class AuronViewModel(application: Application) : AndroidViewModel` tem `state: StateFlow<AuronUiState>`, `events: Flow<UiEvent>`, funções `addTask(title: String, notes: String, priority: Int, dueDate: String?)`, `updateTask(task: Task)`, `deleteTask(id: String)`, `toggleTask(task: Task)`, `saveRoutine(item: RoutineItem)`, `addRoutine(title: String, period: Period)`, `deleteRoutine(id: String)`, `toggleRoutine(item: RoutineItem)`, `send(text: String, source: String = "text")`, `saveSettings(settings: AppSettings)`, `clearChat()`, `clearAll()`, `dismissError()`, `refreshDay()`, `suspend fun exportJson(): String`.

`sealed interface UiEvent { data class Navigate(val destination: String): UiEvent; data class Speak(val text: String): UiEvent }`.

UI: `@Composable fun AuronApp(viewModel: AuronViewModel, voice: VoiceController, onExport: () -> Unit)`. UI agent owns ui files except AuronViewModel.kt, and owns theme. Root owns MainActivity.kt manifest resources. State collection lifecycle-aware. Responsive side rail >=840dp vs compact navigation. UI can use material-icons-extended dependency.

## voice

`class VoiceController(context: Context, onTranscript: (String) -> Unit)` in `br.com.auron.voice` with `val state: StateFlow<VoiceState>`; `fun start(allowOnline: Boolean)`, `fun stopListening()`, `fun speak(text: String, rate: Float)`, `fun stopSpeaking()`, `fun pause()`, `fun close()`. `data class VoiceState(val listening: Boolean = false, val speaking: Boolean = false, val available: Boolean = true, val error: String? = null, val level: Float = 0f)`.

Permission RECORD_AUDIO requested on explicit UI tap, not by controller. Prefer on-device recognizer API31+; if unavailable fail visibly unless settings consented allowOnlineSpeech. No background service. TTS choose installed offline pt-BR voice; unavailable errors visible. `pause()` cancels listening and speaking; Activity calls onStop. Ui call main thread. Callback transcript sends to VM. Manifest queries RecognitionService + TTS_SERVICE.

## Verification

Meaningful domain parser tests, Room persistence/reopen and settings instrumentation, Compose navigation/tasks smoke. Never call emulator/device tests executed without actually executing. No real AI provider or paid integrations in phase 1.
