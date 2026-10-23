package br.com.auron.data

import android.content.Context
import androidx.room.withTransaction
import br.com.auron.domain.AuronRepository
import br.com.auron.domain.ChatMessage
import br.com.auron.domain.DailyReading
import br.com.auron.domain.InputLimits
import br.com.auron.domain.MemoryRecord
import br.com.auron.domain.Period
import br.com.auron.domain.RoutineItem
import br.com.auron.domain.Task
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RoomAuronRepository(
    private val database: AuronDatabase,
    private val memoryCipher: MemoryCipher = AndroidKeystoreMemoryCipher(),
    private val legacyDatabase: AuronDatabase? = null,
    private val legacyMemoryCipher: MemoryCipher = AndroidKeystoreMemoryCipher(),
) : AuronRepository {
    constructor(context: Context, profileId: String = "local") : this(
        AuronDatabase.open(context, profileId),
        AndroidKeystoreMemoryCipher(profileId),
        if (profileId == "local") null else AuronDatabase.open(context, "local"),
        AndroidKeystoreMemoryCipher("local"),
    )

    private val dao = database.dao()

    override val tasks = dao.observeTasks().map { rows -> rows.map { it.toDomain() } }
    override val routineItems = dao.observeRoutineItems().map { rows -> rows.map { it.toDomain() } }
    override val routineCompletions = dao.observeRoutineCompletions().map { rows -> rows.map { it.toDomain() } }
    override val messages = dao.observeMessages().map { rows -> rows.map { it.toDomain() } }
    override val memories = dao.observeMemories().map { rows ->
        rows.map { decodeMemory(it, memoryCipher) }
    }.flowOn(Dispatchers.IO)
    override val readings = dao.observeReadings().map { rows -> rows.map { it.toDomain() } }

    override suspend fun initialize() = database.withTransaction {
        if (dao.metadata(INITIALIZED_KEY) == null) {
            dao.insertRoutines(initialRoutines())
            dao.saveMetadata(MetadataEntity(INITIALIZED_KEY, "1"))
        }
    }

    override suspend fun saveTask(task: Task) {
        require(task.id.isNotBlank()) { "A tarefa precisa de um identificador." }
        require(task.title.isNotBlank()) { "Informe o título da tarefa." }
        require(task.title.length <= InputLimits.TITLE) { "O título da tarefa é muito longo." }
        require(task.priority in 0..2) { "A prioridade deve estar entre 0 e 2." }
        task.dueDate?.let { LocalDate.parse(it) }
        dao.saveTask(task.toEntity())
    }

    override suspend fun deleteTask(id: String) = dao.deleteTask(id)

    override suspend fun setTaskCompleted(id: String, completed: Boolean, now: Long) = database.withTransaction {
        val task = requireNotNull(dao.task(id)) { "Esta tarefa não existe mais." }
        dao.saveTask(task.copy(completedAt = if (completed) task.completedAt ?: now else null))
    }

    override suspend fun saveRoutine(item: RoutineItem) {
        require(item.id.isNotBlank()) { "A atividade precisa de um identificador." }
        require(item.title.isNotBlank()) { "Informe o título da atividade." }
        require(item.title.length <= InputLimits.TITLE) { "O título da atividade é muito longo." }
        require(item.action == null || item.action == "bible_reading") { "Ação de rotina não reconhecida." }
        dao.saveRoutine(item.toEntity())
    }

    override suspend fun deleteRoutine(id: String) = dao.deleteRoutine(id)

    override suspend fun setRoutineCompleted(item: RoutineItem, day: String, completed: Boolean, now: Long) {
        LocalDate.parse(day)
        database.withTransaction {
            if (completed) {
                val current = requireNotNull(dao.routine(item.id)) { "Esta atividade não existe mais." }
                dao.insertCompletion(RoutineCompletionEntity(current.id, day, current.title, current.period, now))
            } else {
                dao.deleteCompletion(item.id, day)
            }
        }
    }

    override suspend fun addMessage(message: ChatMessage) {
        require(message.id.isNotBlank() && message.text.isNotBlank()) { "A mensagem não pode ficar vazia." }
        val limit = if (message.role == "assistant") InputLimits.RESPONSE else InputLimits.MESSAGE
        require(message.text.length <= limit) { "A mensagem excede o limite de $limit caracteres." }
        dao.addMessage(message.toEntity())
    }

    override suspend fun clearMessages() = dao.clearMessages()

    override suspend fun saveMemory(memory: MemoryRecord) = withContext(Dispatchers.IO) {
        require(memory.id.isNotBlank()) { "A memória precisa de um identificador." }
        require(memory.title.isNotBlank() && memory.title.length <= InputLimits.TITLE) {
            "Informe um título de memória de 1 a ${InputLimits.TITLE} caracteres."
        }
        require(memory.content.isNotBlank() && memory.content.length <= InputLimits.MESSAGE) {
            "Informe uma memória de 1 a ${InputLimits.MESSAGE} caracteres."
        }
        dao.saveMemory(encodeMemory(memory, memoryCipher))
    }

    override suspend fun deleteMemory(id: String) = dao.deleteMemory(id)

    override suspend fun getOrCreateReading(day: String, verseIds: List<String>): DailyReading {
        LocalDate.parse(day)
        require(verseIds.isNotEmpty() && verseIds.size <= 50 && verseIds.distinct().size == verseIds.size) {
            "A leitura precisa de uma lista de versículos sem repetições."
        }
        require(verseIds.all { it.isNotBlank() && it.length <= InputLimits.TITLE }) {
            "A leitura contém um identificador de versículo inválido."
        }
        return database.withTransaction {
            dao.reading(day)?.toDomain() ?: run {
                val entity = DailyReadingEntity(day, JSONArray(verseIds).toString())
                dao.insertReading(entity)
                requireNotNull(dao.reading(day)).toDomain()
            }
        }
    }

    /**
     * Explicit, one-time copy from the separate local profile. The source snapshot
     * and destination commit are each transactional; source records are never changed.
     * Destination records win conflicts, except pristine, unused starter routines.
     * The import marker survives clearAll so deleting a profile never restores data.
     */
    override suspend fun importLegacyData(): Boolean = withContext(Dispatchers.IO) {
        val legacy = legacyDatabase ?: return@withContext false
        if (legacy === database || dao.metadata(IMPORTED_KEY) != null) return@withContext false
        val source = legacy.withTransaction {
            val sourceDao = legacy.dao()
            LegacySnapshot(
                sourceDao.allTasks(), sourceDao.allRoutines(), sourceDao.allCompletions(),
                sourceDao.allMessages(), sourceDao.allMemories(), sourceDao.allReadings(),
            )
        }
        database.withTransaction {
            if (dao.metadata(IMPORTED_KEY) != null) return@withTransaction false
            dao.importTasks(source.tasks)
            val defaults = initialRoutines().associateBy { it.id }
            source.routines.forEach { incoming ->
                val existing = dao.routine(incoming.id)
                when {
                    existing == null -> dao.saveRoutine(incoming)
                    existing == defaults[incoming.id] && dao.completionCount(incoming.id) == 0 ->
                        dao.saveRoutine(incoming)
                    // Keep any routine edited or used in the destination profile.
                    else -> Unit
                }
            }
            dao.importCompletions(source.completions)
            dao.importMessages(source.messages)
            source.memories.forEach { incoming ->
                if (dao.memory(incoming.id) == null) {
                    // The target has its own Keystore key; copying ciphertext would be unreadable.
                    dao.saveMemory(encodeMemory(decodeMemory(incoming, legacyMemoryCipher), memoryCipher))
                }
            }
            source.readings.forEach { dao.insertReading(it) }
            dao.saveMetadata(MetadataEntity(INITIALIZED_KEY, "1"))
            dao.saveMetadata(MetadataEntity(IMPORTED_KEY, "1"))
            true
        }
    }

    override suspend fun clearAll() = database.withTransaction {
        dao.clearMessages()
        dao.clearRoutineCompletions()
        dao.clearTasks()
        dao.clearRoutineItems()
        dao.clearMemories()
        dao.clearReadings()
        // Also records initialization if deletion was requested before the first seed.
        dao.saveMetadata(MetadataEntity(INITIALIZED_KEY, "1"))
    }

    private fun initialRoutines(): List<RoutineItemEntity> {
        val morning = listOf(
            "Levantar e sair da cama",
            "Escovar os dentes e lavar o rosto",
            "Beber água",
            "Tomar café da manhã",
            "Tomar medicação conforme prescrição cadastrada",
            "Arrumar cama e quarto",
            "Fazer oração matinal",
            "Ler cinco versículos bíblicos",
            "Alongamento ou exercícios",
            "Organizar tarefas da casa",
            "Registrar gastos ou receitas, quando houver",
        )
        val night = listOf(
            "Revisar o dia e as tarefas pendentes",
            "Preparar as prioridades de amanhã",
            "Cuidar da higiene e preparar o descanso",
            "Reservar um momento para reflexão ou oração",
        )
        return morning.mapIndexed { index, title ->
            RoutineItemEntity("initial_morning_$index", title, Period.MORNING.name, index,
                action = if (index == 7) "bible_reading" else null)
        } + night.mapIndexed { index, title ->
            RoutineItemEntity("initial_night_$index", title, Period.NIGHT.name, index)
        }
    }

    private companion object {
        const val INITIALIZED_KEY = "routines_seeded_v1"
        const val IMPORTED_KEY = "legacy_profile_imported_v1"
    }

    private data class LegacySnapshot(
        val tasks: List<TaskEntity>,
        val routines: List<RoutineItemEntity>,
        val completions: List<RoutineCompletionEntity>,
        val messages: List<ChatMessageEntity>,
        val memories: List<MemoryEntity>,
        val readings: List<DailyReadingEntity>,
    )

    private fun encodeMemory(memory: MemoryRecord, cipher: MemoryCipher): MemoryEntity {
        val payload = JSONObject().put("v", 1).put("title", memory.title).put("content", memory.content).toString()
        val encrypted = cipher.encrypt(memory.id, payload)
        return MemoryEntity(memory.id, encrypted.ciphertext, encrypted.iv, memory.updatedAt)
    }

    private fun decodeMemory(memory: MemoryEntity, cipher: MemoryCipher): MemoryRecord {
        val payload = JSONObject(cipher.decrypt(memory.id, memory.ciphertext, memory.iv))
        check(payload.getInt("v") == 1) { "Versão do registro de memória não reconhecida." }
        return MemoryRecord(memory.id, payload.getString("title"), payload.getString("content"), memory.updatedAt)
    }
}
