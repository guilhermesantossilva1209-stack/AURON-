package br.com.auron.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.auron.domain.AppSettings
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    @Test
    fun preferencesAndExplicitSpeechConsentSurviveStoreReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repository = DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )
            val defaults = repository.settings.first()
            assertEquals(AppSettings(), defaults)
            assertFalse(defaults.allowOnlineSpeech)
            assertFalse(defaults.speakReplies)
            assertFalse(defaults.useRemoteAi)
            assertFalse(defaults.memoryEnabled)
            assertFalse(defaults.shareHistoryWithAi)
            assertFalse(defaults.shareMemoryWithAi)
            val selected = AppSettings(
                userName = "Ana",
                speakReplies = true,
                speechRate = 1.25f,
                allowOnlineSpeech = true,
                humor = 70,
                sarcasm = 25,
                formality = 80,
                proactivity = 40,
                useRemoteAi = true,
                memoryEnabled = true,
                shareHistoryWithAi = true,
                shareMemoryWithAi = true,
                showIntro = false,
                reducedMotion = true,
                speechPitch = 0.9f,
                speechVoiceName = "pt-br-offline-test",
                speechVoiceEngine = "android.test.engine",
            )
            repository.update(selected)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )

            assertEquals(selected, repository.settings.first())
            repository.reset()
            assertEquals(AppSettings(), repository.settings.first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun invalidSettingsLeaveTheLastSavedPreferencesUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )
            val valid = AppSettings(userName = "Comandante")
            repository.update(valid)
            try {
                repository.update(valid.copy(speechRate = Float.NaN))
                fail("A velocidade inválida deveria ser rejeitada.")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure, before changing the preferences file.
            }
            assertEquals(valid, repository.settings.first())
            for (invalid in listOf(valid.copy(speechPitch = Float.NaN), valid.copy(speechPitch = 0.2f), valid.copy(speechVoiceName = "voice-without-engine"))) {
                try {
                    repository.update(invalid)
                    fail("A configuração de voz inválida deveria ser rejeitada.")
                } catch (_: IllegalArgumentException) {
                    // Invalid pitch and incomplete engine/voice identity never overwrite saved settings.
                }
                assertEquals(valid, repository.settings.first())
            }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun legacyPreferencesKeepTheirValuesAndNewConsentDefaultsRemainOff() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "legacy-settings-${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val oldStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            oldStore.edit {
                it[stringPreferencesKey("user_name")] = "Nome do perfil antigo"
                it[booleanPreferencesKey("speak_replies")] = true
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val repository = DataStoreSettingsRepository(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )
            assertEquals(AppSettings(userName = "Nome do perfil antigo", speakReplies = true), repository.settings.first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun accountsHaveSeparatePreferencesAndDoNotInheritConsent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = DataStoreSettingsRepository(context, "settings-profile-a-${UUID.randomUUID()}")
        val second = DataStoreSettingsRepository(context, "settings-profile-b-${UUID.randomUUID()}")
        try {
            first.update(AppSettings(userName = "Perfil A", useRemoteAi = true, shareHistoryWithAi = true,
                speechPitch = 0.9f, speechVoiceName = "pt-br-profile-a", speechVoiceEngine = "android.test.engine"))
            assertEquals("Perfil A", first.settings.first().userName)
            assertEquals(AppSettings(), second.settings.first())
            second.update(AppSettings(userName = "Perfil B", reducedMotion = true))
            assertEquals("Perfil A", first.settings.first().userName)
            assertEquals("Perfil B", second.settings.first().userName)
            assertEquals("pt-br-profile-a", first.settings.first().speechVoiceName)
            assertEquals("", second.settings.first().speechVoiceName)
        } finally {
            first.reset()
            second.reset()
        }
    }
}
