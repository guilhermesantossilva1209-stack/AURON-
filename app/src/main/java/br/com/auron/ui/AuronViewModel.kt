package br.com.auron.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.auron.data.DataStoreSettingsRepository
import br.com.auron.data.RoomAuronRepository
import br.com.auron.domain.*
import br.com.auron.auth.AuthManager
import br.com.auron.ai.RemoteAiProvider
import br.com.auron.ai.AiAccessMode
import br.com.auron.ai.LocalAiAccessStore
import br.com.auron.automation.AutomationController
import br.com.auron.automation.AutomationCommand
import br.com.auron.automation.AutomationCommandParser
import br.com.auron.bible.*
import br.com.auron.cloud.CloudFailure
import br.com.auron.cloud.CloudUiState
import br.com.auron.cloud.SupabaseCloudClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.util.UUID

data class AuronUiState(
    val ready: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val routineItems: List<RoutineItem> = emptyList(),
    val routineCompletions: List<RoutineCompletion> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val day: String = LocalDate.now().toString(),
    val error: String? = null,
    val busy: Boolean = false,
    val loadError: String? = null,
    val memories: List<MemoryRecord> = emptyList(),
    val memoryError: String? = null,
    val readings: List<DailyReading> = emptyList(),
    val aiStatus: String = "Demonstração local",
    val profileLabel: String = "Perfil local",
    val profileId: String = "",
    val readingItemId: String? = null,
    val readingDay: String = "",
    val readingVerses: List<BibleVerse> = emptyList(),
    val cloud: CloudUiState = CloudUiState(),
)

sealed interface UiEvent {
    data class Navigate(val destination: String) : UiEvent
    data class Speak(val text: String) : UiEvent
    data class OpenReading(val itemId: String) : UiEvent
    data class Automation(val command: AutomationCommand, val profileId: String) : UiEvent
    data object OpenAuth : UiEvent
    data object OpenConnections : UiEvent
}

/** Single owner of user actions. Only explicit user submissions reach CommandParser. */
class AuronViewModel(application: Application) : AndroidViewModel(application) {
    val auth = AuthManager(application)
    val automation = AutomationController.get(application)
    private val localAiAccess = LocalAiAccessStore(application)
    private val localAiMutex = Mutex()
    private val _localAiConfigured = MutableStateFlow(false)
    val localAiConfigured = _localAiConfigured.asStateFlow()
    private val _localAiError = MutableStateFlow<String?>(null)
    val localAiError = _localAiError.asStateFlow()
    private data class Profile(val id: String, val label: String, val repository: AuronRepository, val preferences: SettingsRepository)
    private var profile: Profile? = null
    private var profileScope: CoroutineScope? = null
    private val provider: AiProvider = DemoAiProvider()
    private val actionMutex = Mutex()
    private val cloudMutex = Mutex()
    private val _state = MutableStateFlow(AuronUiState())
    val state: StateFlow<AuronUiState> = _state.asStateFlow()
    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = eventChannel.receiveAsFlow()

    private var loadJob: Job? = null

    private fun currentIdentity(): String? {
        val status = auth.state.value
        return when {
            !status.ready -> null
            status.localMode -> "local"
            status.session != null -> auth.config.value.supabaseUrl.trimEnd('/') + "|" + status.session.userId
            else -> null
        }
    }

    private fun requireCurrent(active: Profile) {
        if (profile !== active || currentIdentity() != active.id) throw CancellationException("Profile changed")
    }

    init {
        viewModelScope.launch {
            combine(auth.state, auth.config) { status, config ->
                when {
                    !status.ready -> null
                    status.localMode -> "local" to "Perfil local"
                    status.session != null -> (config.supabaseUrl.trimEnd('/') + "|" + status.session.userId) to status.session.email
                    else -> null
                }
            }.distinctUntilChanged().collect { identity ->
                profileScope?.cancel()
                loadJob?.cancel()
                profile = null
                automation.setProfile(identity?.first)
                while (eventChannel.tryReceive().isSuccess) { /* discard events from previous identity */ }
                _state.value = AuronUiState()
                if (identity != null) {
                    val (id, label) = identity
                    profile = Profile(id, label, RoomAuronRepository(application, id), DataStoreSettingsRepository(application, id))
                    profileScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
                    retryLoad()
                }
            }
        }
        viewModelScope.launch {
            auth.config.map { it.aiBaseUrl }.distinctUntilChanged().collect { url ->
                _state.update { current -> current.copy(aiStatus = when {
                    !current.settings.useRemoteAi -> "Demonstração local"
                    url.isBlank() -> "Modo local · IA online não configurada"
                    else -> "Aguardando solicitação"
                }) }
                localAiMutex.withLock {
                    _localAiConfigured.value = withContext(Dispatchers.IO) { runCatching { localAiAccess.read(url) != null }.getOrDefault(false) }
                }
            }
        }
    }

    fun retryLoad() {
        val active = profile ?: return
        loadJob?.cancel()
        _state.update { it.copy(ready = false, loadError = null, profileId = active.id, profileLabel = active.label) }
        loadJob = profileScope?.launch {
            try {
                active.repository.initialize()
                val base = combine(active.repository.tasks, active.repository.routineItems, active.repository.routineCompletions,
                    active.repository.messages, active.preferences.settings) { tasks, items, completions, messages, settings ->
                    AuronUiState(true, tasks, items, completions, messages, settings)
                }
                val memoryFlow = active.repository.memories.map { Pair<List<MemoryRecord>, String?>(it, null) }
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        emit(emptyList<MemoryRecord>() to "Não foi possível decifrar as memórias deste perfil. Os registros foram preservados. Tarefas e rotinas continuam disponíveis.")
                    }
                combine(base, memoryFlow, active.repository.readings) { data, memory, readings ->
                    data.copy(memories = memory.first, memoryError = memory.second, readings = readings)
                }.collect { data ->
                    if (profile === active) _state.update { previous -> previous.copy(
                        ready = true, tasks = data.tasks, routineItems = data.routineItems,
                        routineCompletions = data.routineCompletions, messages = data.messages, settings = data.settings,
                        memories = data.memories, memoryError = data.memoryError, readings = data.readings,
                        aiStatus = when {
                            !data.settings.useRemoteAi -> "Demonstração local"
                            auth.config.value.aiBaseUrl.isBlank() -> "Modo local · IA online não configurada"
                            previous.aiStatus == "Demonstração local" -> "Aguardando solicitação"
                            else -> previous.aiStatus
                        }
                    ) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (profile === active) _state.update { it.copy(ready = false, loadError = "Não foi possível abrir os dados locais. Verifique o armazenamento e tente novamente. Nenhum dado foi apagado.") }
            }
        }
    }

    private fun action(block: suspend Profile.() -> Unit) {
        val active = profile ?: return
        profileScope?.launch {
            actionMutex.withLock {
                if (profile !== active || currentIdentity() != active.id) return@withLock
                _state.update { it.copy(busy = true) }
                try {
                    check(_state.value.ready) { "Aguarde a abertura dos dados locais." }
                    active.block()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    val message = if (error is IllegalArgumentException || error is IllegalStateException)
                        error.message ?: "Revise os campos informados."
                    else "Não foi possível salvar a alteração. Verifique o armazenamento e tente novamente."
                    if (profile === active) _state.update { it.copy(error = message) }
                } finally {
                    if (profile === active) _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    private fun validatedTitle(value: String): String = value.trim().also {
        require(it.isNotEmpty() && it.length <= 160) { "Use um título de 1 a 160 caracteres." }
    }

    private fun validateTask(task: Task): Task {
        require(task.notes.length <= 4000) { "As observações devem ter até 4.000 caracteres." }
        require(task.priority in 0..2) { "Prioridade inválida." }
        val date = task.dueDate?.trim()?.takeIf { it.isNotEmpty() }
        if (date != null) {
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) && runCatching { LocalDate.parse(date) }.isSuccess) {
                "Informe uma data válida no formato AAAA-MM-DD."
            }
        }
        return task.copy(title = validatedTitle(task.title), notes = task.notes.trim(), dueDate = date)
    }

    fun addTask(title: String, notes: String, priority: Int, dueDate: String?) = action {
        repository.saveTask(validateTask(Task(UUID.randomUUID().toString(), title, notes, priority, dueDate, System.currentTimeMillis())))
    }

    fun updateTask(task: Task) = action {
        val current = requireNotNull(repository.tasks.first().find { it.id == task.id }) { "Esta tarefa não existe mais." }
        repository.saveTask(validateTask(current.copy(title = task.title, notes = task.notes, priority = task.priority, dueDate = task.dueDate)))
    }
    fun deleteTask(id: String) = action { repository.deleteTask(id) }
    fun toggleTask(task: Task) = action {
        val current = requireNotNull(repository.tasks.first().find { it.id == task.id }) { "Esta tarefa não existe mais." }
        repository.setTaskCompleted(task.id, current.completedAt == null, System.currentTimeMillis())
    }
    fun addRoutine(title: String, period: Period) = action {
        val order = (repository.routineItems.first().filter { it.period == period }.maxOfOrNull { it.orderIndex } ?: -1) + 1
        repository.saveRoutine(RoutineItem(UUID.randomUUID().toString(), validatedTitle(title), period, order))
    }
    fun saveRoutine(item: RoutineItem) = action {
        val current = requireNotNull(repository.routineItems.first().find { it.id == item.id }) { "Esta atividade não existe mais." }
        repository.saveRoutine(current.copy(title = validatedTitle(item.title)))
    }
    fun deleteRoutine(id: String) = action { repository.deleteRoutine(id) }
    fun toggleRoutine(item: RoutineItem) = action {
        val current = requireNotNull(repository.routineItems.first().find { it.id == item.id }) { "Esta atividade não existe mais." }
        if (current.isBibleReading()) {
            prepareReading(current)
            return@action
        }
        val today = LocalDate.now().toString()
        val done = repository.routineCompletions.first().any { it.itemId == item.id && it.day == today }
        repository.setRoutineCompleted(current, today, !done, System.currentTimeMillis())
        refreshDay()
    }
    fun saveSettings(settings: AppSettings) = action {
        require(settings.userName.trim().length in 1..60) { "Use um nome de 1 a 60 caracteres." }
        require(settings.speechRate.isFinite()) { "Velocidade inválida." }
        require(settings.speechPitch.isFinite()) { "Tonalidade inválida." }
        preferences.update(settings.copy(userName = settings.userName.trim(), speechRate = settings.speechRate.coerceIn(0.5f, 2f),
            speechPitch = settings.speechPitch.coerceIn(0.7f, 1.3f),
            humor = settings.humor.coerceIn(0, 100), sarcasm = settings.sarcasm.coerceIn(0, 100),
            formality = settings.formality.coerceIn(0, 100), proactivity = settings.proactivity.coerceIn(0, 100)))
    }
    fun dismissError() { _state.update { it.copy(error = null) } }
    fun refreshDay() {
        val today = LocalDate.now().toString()
        val opened = _state.value.readingItemId
        val stale = opened != null && _state.value.readingDay != today
        _state.update { it.copy(day = today) }
        if (stale) {
            dismissReading()
            _state.value.routineItems.find { it.id == opened }?.let(::openReading)
        }
    }
    fun clearChat() = action { repository.clearMessages() }
    fun clearAll() = action {
        repository.clearAll()
        preferences.reset()
        dismissReading()
    }

    fun requestLogin() { auth.showLogin() }
    fun saveLocalAiAccess(baseUrl: String, token: String) {
        viewModelScope.launch {
            localAiMutex.withLock {
                _localAiError.value = null
                try {
                    check(baseUrl == auth.config.value.aiBaseUrl && baseUrl.isNotBlank())
                    withContext(Dispatchers.IO) { localAiAccess.save(baseUrl, token.trim()) }
                    _localAiConfigured.value = true
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _localAiError.value = "Não foi possível salvar. Salve a URL HTTPS do servidor e confira o token pessoal auron_local_."
                }
            }
        }
    }

    fun clearLocalAiAccess() {
        viewModelScope.launch {
            localAiMutex.withLock {
                _localAiError.value = null
                try {
                    withContext(Dispatchers.IO) { localAiAccess.clear() }
                    _localAiConfigured.value = false
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _localAiError.value = "Não foi possível remover o acesso local à IA. Tente novamente."
                }
            }
        }
    }

    fun logout() { viewModelScope.launch { auth.signOut() } }

    /** Cloud operations are opt-in and never block local routine/task writes. */
    private fun cloudOperation(block: suspend (Profile, SupabaseCloudClient, CloudUiState) -> CloudUiState) {
        val active = profile ?: return
        profileScope?.launch {
            cloudMutex.withLock {
                requireCurrent(active)
                val previous = _state.value.cloud
                _state.update { it.copy(cloud = previous.copy(loading = true, error = null, notice = null)) }
                try {
                    check(active.id != "local" && _state.value.ready) { "Entre na conta e aguarde a abertura dos dados." }
                    val client = SupabaseCloudClient(auth.config.value) {
                        requireCurrent(active)
                        val token = auth.accessToken()
                        requireCurrent(active)
                        token
                    }
                    val result = block(active, client, previous)
                    requireCurrent(active)
                    _state.update { it.copy(cloud = result.copy(loading = false)) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    requireCurrent(active)
                    val message = when (error) {
                        is CloudFailure -> error.safeMessage
                        is IllegalStateException, is IllegalArgumentException -> error.message ?: "Consulte a cópia antes de alterar."
                        else -> "Não foi possível acessar a cópia na nuvem. Seus registros locais foram preservados."
                    }
                    // A failed request may have reached the server. Always re-read before retrying a write.
                    _state.update { it.copy(cloud = previous.copy(loading = false, inspected = false, error = message, notice = null)) }
                }
            }
        }
    }

    fun inspectCloud() = cloudOperation { _, client, _ ->
        val snapshot = client.fetch()
        CloudUiState(inspected = true, snapshot = snapshot,
            notice = if (snapshot == null) "Não há cópia nesta conta." else "Cópia consultada. Nenhum dado local foi alterado.")
    }

    fun uploadCloud() = cloudOperation { active, client, previous ->
        check(previous.inspected) { "Consulte a cópia antes de enviar seus registros." }
        val json = withContext(Dispatchers.IO) { exportJson(active.id) }
        requireCurrent(active)
        val snapshot = client.save(json, previous.snapshot?.revision ?: 0L)
        CloudUiState(inspected = true, snapshot = snapshot, notice = "Cópia salva no Supabase. Os registros do tablet foram preservados.")
    }

    fun deleteCloud() = cloudOperation { _, client, previous ->
        check(previous.inspected) { "Consulte a cópia antes de excluir." }
        val snapshot = requireNotNull(previous.snapshot) { "Não há cópia para excluir." }
        client.delete(snapshot.revision)
        CloudUiState(inspected = true, notice = "Cópia removida da nuvem. Os registros do tablet foram preservados.")
    }

    fun importLegacyData() = action {
        check(id != "local") { "Entre em uma conta antes de copiar os dados locais." }
        val imported = repository.importLegacyData()
        _state.update { it.copy(error = if (imported) "Dados locais copiados para esta conta. Os originais foram preservados; preferências não foram copiadas." else "Os dados locais já foram copiados para esta conta.") }
    }
    fun saveMemory(id: String?, title: String, content: String) = action {
        check(_state.value.memoryError == null) { "Reabra as memórias antes de editar. Os registros existentes foram preservados." }
        check(preferences.settings.first().memoryEnabled) { "Ative a memória nas configurações antes de salvar." }
        require(content.trim().length in 1..4000) { "Use um conteúdo de 1 a 4.000 caracteres." }
        if (id != null) require(repository.memories.first().any { it.id == id }) { "Esta memória não existe mais." }
        repository.saveMemory(MemoryRecord(id ?: UUID.randomUUID().toString(), validatedTitle(title), content.trim(), System.currentTimeMillis()))
    }
    fun deleteMemory(id: String) = action { repository.deleteMemory(id) }
    fun openReading(item: RoutineItem) = action {
        val current = requireNotNull(repository.routineItems.first().find { it.id == item.id }) { "Esta atividade não existe mais." }
        require(current.isBibleReading()) { "Esta atividade não está vinculada à leitura bíblica." }
        prepareReading(current)
    }
    private suspend fun Profile.prepareReading(item: RoutineItem) {
        val day = LocalDate.now()
        val verses = withContext(Dispatchers.IO) { BibleCatalog.load(getApplication()) }
        val record = repository.getOrCreateReading(day.toString(), DailyVerseSelector.select(day, verses).map { it.id })
        val byId = verses.associateBy { it.id }
        val selected = record.verseIds.map { requireNotNull(byId[it]) { "O catálogo não contém uma leitura já registrada. Nenhum histórico foi alterado." } }
        check(selected.size == 5) { "A leitura registrada está incompleta." }
        requireCurrent(this)
        _state.update { it.copy(day = day.toString(), readingItemId = item.id, readingDay = day.toString(), readingVerses = selected) }
    }
    fun completeReading(itemId: String, day: String) = action {
        require(day == LocalDate.now().toString()) { "A data mudou. Reabra a leitura de hoje antes de concluir." }
        require(_state.value.readingItemId == itemId && _state.value.readingDay == day && _state.value.readingVerses.size == 5) { "Abra a leitura antes de concluir." }
        val item = requireNotNull(repository.routineItems.first().find { it.id == itemId && it.isBibleReading() }) { "A atividade de leitura não existe mais." }
        if (repository.routineCompletions.first().none { it.itemId == itemId && it.day == day }) {
            repository.setRoutineCompleted(item, day, true, System.currentTimeMillis())
        }
        refreshDay()
    }
    fun dismissReading() { _state.update { it.copy(readingItemId = null, readingDay = "", readingVerses = emptyList()) } }

    fun send(text: String, source: String = "text") = action {
        val input = text.trim()
        require(input.length in 1..4000) { "Envie uma mensagem de 1 a 4.000 caracteres." }
        require(source == "text" || source == "voice") { "Origem de comando não autorizada." }
        val command = CommandParser.parse(input)
        if (command == LocalCommand.Unknown) {
            AutomationCommandParser.parse(input)?.let { automationCommand ->
                requireCurrent(this)
                // Commands and reply text stay transient: notifications must never enter chat/AI history.
                eventChannel.send(UiEvent.Automation(automationCommand, id))
                return@action
            }
        }
        val now = System.currentTimeMillis()
        val lastUserMessageId = UUID.randomUUID().toString()
        repository.addMessage(ChatMessage(lastUserMessageId, "user", input, now, source))
        val settings = preferences.settings.first()
        val remote = command == LocalCommand.Unknown && settings.useRemoteAi
        var responseSource = if (command == LocalCommand.Unknown || command == LocalCommand.Help) "demo" else "system"
        val reply = if (remote) {
            val config = auth.config.value
            _state.update { it.copy(aiStatus = if (config.aiBaseUrl.isBlank()) "Modo local · IA online não configurada" else "Consultando IA…") }
            val response = if (config.aiBaseUrl.isBlank()) ProviderResult.Unavailable("Nenhum servidor de IA foi configurado. Em Configurar conexões, informe a URL HTTPS de um gateway AURON com modelo disponível. A URL do Supabase não substitui esse servidor.")
            else if (settings.memoryEnabled && settings.shareMemoryWithAi && _state.value.memoryError != null) ProviderResult.Unavailable("A memória não pôde ser aberta. Desative seu compartilhamento para conversar sem ela.")
            else RemoteAiProvider(config.aiBaseUrl, if (id == "local") AiAccessMode.LocalGateway else AiAccessMode.SupabaseSession) {
                requireCurrent(this)
                val token = if (id == "local") localAiMutex.withLock {
                    withContext(Dispatchers.IO) { localAiAccess.read(config.aiBaseUrl) }
                } else auth.accessToken()
                requireCurrent(this)
                token
            }.reply(AiRequest(
                input, settings,
                history = if (settings.shareHistoryWithAi) repository.messages.first().filterNot { it.id == lastUserMessageId } else emptyList(),
                memories = if (settings.memoryEnabled && settings.shareMemoryWithAi) repository.memories.first() else emptyList()
            ))
            requireCurrent(this)
            when (response) {
                is ProviderResult.Success -> {
                    responseSource = "ai"
                    _state.update { it.copy(aiStatus = "IA respondeu · última solicitação") }
                    response.text
                }
                is ProviderResult.Unavailable -> {
                    // A remote failure must not disable the existing offline assistant.
                    // The source and text explicitly identify deterministic local output;
                    // this is not reported as a successful model response.
                    responseSource = "demo"
                    _state.update { it.copy(aiStatus = if (config.aiBaseUrl.isBlank()) "Modo local · IA online não configurada" else "Modo local · IA online indisponível") }
                    val localReply = when (val local = provider.reply(input, settings)) {
                        is ProviderResult.Success -> local.text
                        is ProviderResult.Unavailable -> "Modo local: tarefas, rotinas e navegação continuam disponíveis. Digite “ajuda” para ver os comandos."
                    }
                    "IA online indisponível: ${response.reason}\n\n$localReply"
                }
            }
        } else handle(command, input)
        requireCurrent(this)
        repository.addMessage(ChatMessage(UUID.randomUUID().toString(), "assistant", reply, System.currentTimeMillis(), responseSource))
        requireCurrent(this)
        if (settings.speakReplies) eventChannel.send(UiEvent.Speak(reply))
    }

    private suspend fun Profile.handle(command: LocalCommand, input: String): String {
        val snapshot = _state.value.copy(tasks = repository.tasks.first(), routineItems = repository.routineItems.first(), settings = preferences.settings.first())
        return when (command) {
            LocalCommand.Morning -> {
                eventChannel.send(UiEvent.Navigate("routine/morning"))
                "Bom dia, ${snapshot.settings.userName}. Rotina matinal aberta. ${pending(Period.MORNING)}"
            }
            LocalCommand.Night -> {
                eventChannel.send(UiEvent.Navigate("routine/night"))
                "Checklist noturno aberto. ${pending(Period.NIGHT)}"
            }
            LocalCommand.ShowRoutine -> {
                eventChannel.send(UiEvent.Navigate("routine"))
                "Sua rotina está aberta. Você pode editar cada atividade."
            }
            LocalCommand.Pending -> {
                val tasks = snapshot.tasks.filter { it.completedAt == null }
                "Há ${tasks.size} tarefa(s) pendente(s). ${pending(Period.MORNING)}" +
                    tasks.take(5).joinToString("", prefix = if (tasks.isEmpty()) "" else " Prioridades:") { "\n• ${it.title}" }
            }
            is LocalCommand.CreateTask -> {
                repository.saveTask(validateTask(Task(UUID.randomUUID().toString(), command.title, createdAt = System.currentTimeMillis())))
                "Tarefa criada no dispositivo: ${command.title}."
            }
            is LocalCommand.CompleteTask -> {
                val matches = snapshot.tasks.filter { it.completedAt == null && normalized(it.title) == normalized(command.title) }
                if (matches.size == 1) {
                    repository.setTaskCompleted(matches.single().id, true, System.currentTimeMillis())
                    "Tarefa concluída: ${matches.single().title}. Ela está no histórico."
                } else if (matches.isEmpty()) "Não encontrei uma tarefa pendente com esse título exato. Abra Tarefas para conferir."
                else "Há mais de uma tarefa com esse título. Selecione a desejada na tela Tarefas."
            }
            is LocalCommand.CompleteRoutine -> {
                val matches = snapshot.routineItems.filter { normalized(it.title) == normalized(command.title) }
                if (matches.size == 1) {
                    if (matches.single().isBibleReading()) {
                        prepareReading(matches.single())
                        "Leitura bíblica aberta. Confirme no botão Concluir leitura depois de ler os cinco versículos."
                    } else {
                        repository.setRoutineCompleted(matches.single(), LocalDate.now().toString(), true, System.currentTimeMillis())
                        "Atividade concluída hoje: ${matches.single().title}."
                    }
                } else "Preciso de um título único e completo. Selecione a atividade na tela Rotina."
            }
            is LocalCommand.Navigate -> {
                eventChannel.send(UiEvent.Navigate(command.destination))
                "Tela solicitada aberta."
            }
            LocalCommand.Help, LocalCommand.Unknown -> when (val response = provider.reply(input, snapshot.settings)) {
                is ProviderResult.Success -> response.text
                is ProviderResult.Unavailable -> "Provedor indisponível: ${response.reason}. As tarefas e rotinas locais continuam disponíveis."
            }
        }
    }

    private suspend fun Profile.pending(period: Period): String {
        val items = repository.routineItems.first()
        val today = LocalDate.now().toString()
        val done = repository.routineCompletions.first().filter { it.day == today }.map { it.itemId }.toSet()
        val pending = items.count { it.period == period && it.id !in done }
        return "${pending} atividade(s) restante(s) na rotina ${if (period == Period.MORNING) "matinal" else "noturna"}."
    }

    private fun normalized(value: String) = Normalizer.normalize(value.trim().lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")

    /** Explicit user export only. Never invoked by a provider or a parsed command. */
    suspend fun exportJson(expectedProfileId: String): String = actionMutex.withLock {
        val active = requireNotNull(profile) { "Entre em um perfil para exportar." }
        check(expectedProfileId.isNotEmpty() && active.id == expectedProfileId) { "O perfil mudou desde a solicitação de exportação." }
        requireCurrent(active)
        val repository = active.repository
        val preferences = active.preferences
        check(state.value.ready) { "Aguarde o carregamento dos dados." }
        val tasks = repository.tasks.first()
        val routines = repository.routineItems.first()
        val completions = repository.routineCompletions.first()
        val messages = repository.messages.first()
        val settings = preferences.settings.first()
        JSONObject().apply {
            put("format", "auron-export")
            put("version", 2)
            put("exportedAt", java.time.Instant.now().toString())
            put("tasks", JSONArray().apply { tasks.forEach { task -> put(JSONObject().apply {
                put("id", task.id); put("title", task.title); put("notes", task.notes); put("priority", task.priority)
                put("dueDate", task.dueDate ?: JSONObject.NULL); put("createdAt", task.createdAt)
                put("completedAt", task.completedAt ?: JSONObject.NULL)
            }) } })
            put("routineItems", JSONArray().apply { routines.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("period", item.period.name); put("orderIndex", item.orderIndex)
                put("action", item.action ?: JSONObject.NULL)
            }) } })
            put("routineCompletions", JSONArray().apply { completions.forEach { completion -> put(JSONObject().apply {
                put("itemId", completion.itemId); put("day", completion.day); put("title", completion.title)
                put("period", completion.period.name); put("completedAt", completion.completedAt)
            }) } })
            put("messages", JSONArray().apply { messages.forEach { message -> put(JSONObject().apply {
                put("id", message.id); put("role", message.role); put("text", message.text)
                put("createdAt", message.createdAt); put("source", message.source)
            }) } })
            put("settings", JSONObject().apply {
                put("userName", settings.userName); put("speakReplies", settings.speakReplies); put("speechRate", settings.speechRate)
                put("speechPitch", settings.speechPitch); put("speechVoiceName", settings.speechVoiceName); put("speechVoiceEngine", settings.speechVoiceEngine)
                put("allowOnlineSpeech", settings.allowOnlineSpeech); put("humor", settings.humor); put("sarcasm", settings.sarcasm)
                put("formality", settings.formality); put("proactivity", settings.proactivity)
                put("useRemoteAi", settings.useRemoteAi); put("memoryEnabled", settings.memoryEnabled)
                put("shareHistoryWithAi", settings.shareHistoryWithAi); put("shareMemoryWithAi", settings.shareMemoryWithAi)
                put("showIntro", settings.showIntro); put("reducedMotion", settings.reducedMotion)
            })
            put("memories", JSONArray().apply { repository.memories.first().forEach { memory -> put(JSONObject().apply {
                put("id", memory.id); put("title", memory.title); put("content", memory.content); put("updatedAt", memory.updatedAt)
            }) } })
            put("readings", JSONArray().apply { repository.readings.first().forEach { reading -> put(JSONObject().apply {
                put("day", reading.day); put("verseIds", JSONArray(reading.verseIds))
            }) } })
            requireCurrent(active)
        }.toString(2)
    }

    override fun onCleared() {
        profileScope?.cancel()
        automation.setProfile(null)
        auth.close()
        super.onCleared()
    }
}
