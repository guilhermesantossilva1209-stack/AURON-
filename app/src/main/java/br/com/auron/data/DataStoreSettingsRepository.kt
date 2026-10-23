package br.com.auron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import br.com.auron.domain.AppSettings
import br.com.auron.domain.InputLimits
import br.com.auron.domain.SettingsRepository
import kotlinx.coroutines.flow.map

private val Context.auronSettingsStore by preferencesDataStore(name = "auron_preferences")

private object ProfileSettingsStores {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()

    fun open(context: Context, profileId: String): DataStore<Preferences> {
        val app = context.applicationContext
        if (profileId == "local") return app.auronSettingsStore
        val name = ProfileStorage.settingsName(profileId)
        return synchronized(stores) {
            stores.getOrPut(name) {
                PreferenceDataStoreFactory.create(produceFile = { app.preferencesDataStoreFile(name) })
            }
        }
    }
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    constructor(context: Context, profileId: String = "local") : this(ProfileSettingsStores.open(context, profileId))

    // IO/corruption errors intentionally propagate so the caller can display a real failure.
    override val settings = dataStore.data.map { preferences ->
        val defaults = AppSettings()
        AppSettings(
            userName = preferences[Keys.userName] ?: defaults.userName,
            speakReplies = preferences[Keys.speakReplies] ?: defaults.speakReplies,
            speechRate = preferences[Keys.speechRate] ?: defaults.speechRate,
            allowOnlineSpeech = preferences[Keys.allowOnlineSpeech] ?: defaults.allowOnlineSpeech,
            humor = preferences[Keys.humor] ?: defaults.humor,
            sarcasm = preferences[Keys.sarcasm] ?: defaults.sarcasm,
            formality = preferences[Keys.formality] ?: defaults.formality,
            proactivity = preferences[Keys.proactivity] ?: defaults.proactivity,
            useRemoteAi = preferences[Keys.useRemoteAi] ?: defaults.useRemoteAi,
            memoryEnabled = preferences[Keys.memoryEnabled] ?: defaults.memoryEnabled,
            shareHistoryWithAi = preferences[Keys.shareHistoryWithAi] ?: defaults.shareHistoryWithAi,
            shareMemoryWithAi = preferences[Keys.shareMemoryWithAi] ?: defaults.shareMemoryWithAi,
            showIntro = preferences[Keys.showIntro] ?: defaults.showIntro,
            reducedMotion = preferences[Keys.reducedMotion] ?: defaults.reducedMotion,
            speechPitch = preferences[Keys.speechPitch] ?: defaults.speechPitch,
            speechVoiceName = preferences[Keys.speechVoiceName] ?: defaults.speechVoiceName,
            speechVoiceEngine = preferences[Keys.speechVoiceEngine] ?: defaults.speechVoiceEngine,
        )
    }

    override suspend fun update(settings: AppSettings) {
        require(settings.userName.isNotBlank()) { "Informe como deseja ser chamado." }
        require(settings.userName.length <= InputLimits.USER_NAME) { "O nome é muito longo." }
        require(settings.speechRate.isFinite() && settings.speechRate in 0.5f..2f) {
            "A velocidade da fala deve estar entre 0,5 e 2." }
        require(settings.speechPitch.isFinite() && settings.speechPitch in 0.7f..1.3f) {
            "A tonalidade deve estar entre 0,7 e 1,3." }
        require(settings.speechVoiceName.length <= 256 && settings.speechVoiceEngine.length <= 256) {
            "A identificação da voz é muito longa." }
        require(settings.speechVoiceName.isBlank() == settings.speechVoiceEngine.isBlank()) {
            "Selecione uma voz e seu mecanismo juntos ou use a seleção automática." }
        require(listOf(settings.humor, settings.sarcasm, settings.formality, settings.proactivity).all { it in 0..100 }) {
            "Os ajustes de personalidade devem estar entre 0 e 100." }
        dataStore.edit { preferences ->
            preferences[Keys.userName] = settings.userName
            preferences[Keys.speakReplies] = settings.speakReplies
            preferences[Keys.speechRate] = settings.speechRate
            preferences[Keys.allowOnlineSpeech] = settings.allowOnlineSpeech
            preferences[Keys.humor] = settings.humor
            preferences[Keys.sarcasm] = settings.sarcasm
            preferences[Keys.formality] = settings.formality
            preferences[Keys.proactivity] = settings.proactivity
            preferences[Keys.useRemoteAi] = settings.useRemoteAi
            preferences[Keys.memoryEnabled] = settings.memoryEnabled
            preferences[Keys.shareHistoryWithAi] = settings.shareHistoryWithAi
            preferences[Keys.shareMemoryWithAi] = settings.shareMemoryWithAi
            preferences[Keys.showIntro] = settings.showIntro
            preferences[Keys.reducedMotion] = settings.reducedMotion
            preferences[Keys.speechPitch] = settings.speechPitch
            preferences[Keys.speechVoiceName] = settings.speechVoiceName
            preferences[Keys.speechVoiceEngine] = settings.speechVoiceEngine
        }
    }

    override suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val userName = stringPreferencesKey("user_name")
        val speakReplies = booleanPreferencesKey("speak_replies")
        val speechRate = floatPreferencesKey("speech_rate")
        val allowOnlineSpeech = booleanPreferencesKey("allow_online_speech")
        val humor = intPreferencesKey("humor")
        val sarcasm = intPreferencesKey("sarcasm")
        val formality = intPreferencesKey("formality")
        val proactivity = intPreferencesKey("proactivity")
        val useRemoteAi = booleanPreferencesKey("use_remote_ai")
        val memoryEnabled = booleanPreferencesKey("memory_enabled")
        val shareHistoryWithAi = booleanPreferencesKey("share_history_with_ai")
        val shareMemoryWithAi = booleanPreferencesKey("share_memory_with_ai")
        val showIntro = booleanPreferencesKey("show_intro")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val speechPitch = floatPreferencesKey("speech_pitch")
        val speechVoiceName = stringPreferencesKey("speech_voice_name")
        val speechVoiceEngine = stringPreferencesKey("speech_voice_engine")
    }
}
