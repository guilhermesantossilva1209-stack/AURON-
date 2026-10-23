package br.com.auron.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A foreground-only adapter for Android's speech services.
 *
 * Recognition starts only through [start]. Without explicit online consent it uses
 * Android's on-device recognizer exclusively. Synthesized speech uses an installed
 * pt-BR voice which the selected TTS engine declares does not require a network.
 * No microphone service, hotword listener or cloud provider is started by this class.
 */
class VoiceController(context: Context, private val onTranscript: (String) -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = mutableState.asStateFlow()

    private var closed = false
    private var recognizer: SpeechRecognizer? = null
    private var recognitionSession = 0L
    private var captureRequested = false
    private var recognitionTimeout: Runnable? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsGeneration = 0L
    private var ttsTimeout: Runnable? = null
    private var pendingSpeech: SpeechRequest? = null
    private var utteranceCounter = 0L
    private var currentUtterance: String? = null
    private var utteranceTimeout: Runnable? = null
    private var utteranceCompletionTimeoutMs = 0L
    private var installedTtsVoices = emptyMap<String, android.speech.tts.Voice>()

    /** Permission must be requested by the UI after an explicit microphone tap. */
    fun start(allowOnline: Boolean) = onMain {
        if (closed) return@onMain
        cancelRecognition()
        stopSpeechInternal()
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            failRecognition("Permita o uso do microfone para iniciar a escuta.", unavailable = true)
            return@onMain
        }

        val useOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
        if (!useOnDevice && !allowOnline) {
            failRecognition(
                "Reconhecimento local indisponível. Use texto ou autorize o serviço de voz nas configurações; ele pode enviar áudio ao provedor do Android.",
                unavailable = true,
            )
            return@onMain
        }
        if (!useOnDevice && !runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)) {
            failRecognition("Nenhum serviço de reconhecimento de fala está disponível neste dispositivo.", unavailable = true)
            return@onMain
        }

        val session = ++recognitionSession
        try {
            val instance = if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            recognizer = instance
            captureRequested = true
            instance.setRecognitionListener(listenerFor(session))
            mutableState.value = mutableState.value.copy(listening = true, available = true, error = null, level = 0f)
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // EXTRA_PREFER_OFFLINE is only a hint. The actual privacy guarantee
                // above is createOnDeviceSpeechRecognizer(), never this extra alone.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice)
            }
            instance.startListening(request)
            scheduleRecognitionTimeout(session, 45_000L, "A escuta atingiu o limite de tempo. Toque no microfone para tentar novamente.")
        } catch (_: SecurityException) {
            failRecognition("O serviço de voz não recebeu permissão para usar o microfone.", unavailable = true)
        } catch (_: RuntimeException) {
            failRecognition("Não foi possível iniciar o serviço de voz. Verifique os serviços de fala instalados.", unavailable = true)
        }
    }

    /** Stop capture, allowing the recognizer to deliver the words already spoken. */
    fun stopListening() = onMain {
        if (closed || recognizer == null || !mutableState.value.listening) return@onMain
        captureRequested = false
        try {
            recognizer?.stopListening()
            mutableState.value = mutableState.value.copy(listening = false, level = 0f)
            scheduleRecognitionTimeout(recognitionSession, 12_000L, "O serviço de voz não retornou uma transcrição. Tente novamente.")
        } catch (_: RuntimeException) {
            failRecognition("Não foi possível concluir a transcrição. Tente novamente.")
        }
    }

    /** Only the latest request is retained while the local TTS engine initializes. */
    fun speak(text: String, rate: Float, pitch: Float = 1f, voiceName: String = "", voiceEngine: String = "") = onMain {
        if (closed) return@onMain
        val content = text.trim()
        if (content.isEmpty()) return@onMain
        cancelRecognition()
        stopSpeechInternal()
        if (content.length > TextToSpeech.getMaxSpeechInputLength()) {
            mutableState.value = mutableState.value.copy(error = "A resposta excede o limite da voz do Android. Leia o texto no histórico.")
            return@onMain
        }
        pendingSpeech = SpeechRequest(
            content,
            if (rate.isFinite()) rate.coerceIn(0.5f, 2f) else 1f,
            if (pitch.isFinite()) pitch.coerceIn(0.7f, 1.3f) else 1f,
            voiceName.take(256), voiceEngine.take(256),
        )
        mutableState.value = mutableState.value.copy(error = null)
        if (ttsReady) {
            playPendingSpeech()
        } else if (tts == null) {
            initializeTts()
        }
    }

    fun stopSpeaking() = onMain { stopSpeechInternal() }

    /** Catalog only: never starts the microphone or speaks without a separate request. */
    fun refreshVoices() = onMain {
        if (closed) return@onMain
        stopSpeechInternal()
        releaseTts()
        initializeTts()
    }

    /** Called from Activity.onStop: no delayed result or queued speech survives it. */
    fun pause() = onMain {
        cancelRecognition()
        stopSpeechInternal()
    }

    /** Called from Activity.onDestroy. Idempotent, including during engine startup. */
    fun close() = onMain {
        if (closed) return@onMain
        closed = true
        cancelRecognition()
        stopSpeechInternal()
        releaseTts()
    }

    private fun listenerFor(session: Long) = object : RecognitionListener {
        private fun current(action: () -> Unit) = onMain {
            if (!closed && session == recognitionSession && recognizer != null) action()
        }

        override fun onReadyForSpeech(params: Bundle?) = current {
            if (captureRequested) mutableState.value = mutableState.value.copy(listening = true, error = null)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = current {
            if (mutableState.value.listening) {
                val level = if (rmsdB.isFinite()) ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) else 0f
                mutableState.value = mutableState.value.copy(level = level)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = current {
            captureRequested = false
            mutableState.value = mutableState.value.copy(listening = false, level = 0f)
            scheduleRecognitionTimeout(session, 12_000L, "O serviço de voz não retornou uma transcrição. Tente novamente.")
        }

        override fun onError(error: Int) = current {
            val unavailable = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            failRecognition(recognitionError(error), unavailable)
        }

        override fun onResults(results: Bundle?) = current {
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }?.trim()
            cancelRecognition()
            if (transcript == null) {
                mutableState.value = mutableState.value.copy(error = "Não entendi a fala. Tente novamente ou use texto.")
            } else {
                mutableState.value = mutableState.value.copy(error = null)
                onTranscript(transcript)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun scheduleRecognitionTimeout(session: Long, delay: Long, message: String) {
        recognitionTimeout?.let(main::removeCallbacks)
        recognitionTimeout = Runnable {
            if (!closed && session == recognitionSession && recognizer != null) failRecognition(message)
        }.also { main.postDelayed(it, delay) }
    }

    private fun cancelRecognition() {
        ++recognitionSession // Invalidate callbacks before cancel() can emit ERROR_CLIENT.
        captureRequested = false
        recognitionTimeout?.let(main::removeCallbacks)
        recognitionTimeout = null
        val oldRecognizer = recognizer
        recognizer = null
        runCatching { oldRecognizer?.cancel() }
        runCatching { oldRecognizer?.destroy() }
        mutableState.value = mutableState.value.copy(listening = false, level = 0f)
    }

    private fun failRecognition(message: String, unavailable: Boolean = false) {
        cancelRecognition()
        mutableState.value = mutableState.value.copy(available = !unavailable, error = message)
    }

    private fun initializeTts() {
        val generation = ++ttsGeneration
        mutableState.value = mutableState.value.copy(loadingVoices = true, ttsAvailable = null, voiceNotice = null, error = null)
        try {
            tts = TextToSpeech(appContext) { status ->
                // Always post: some engines call the initialization listener before
                // the constructor has returned and the tts field has been assigned.
                main.post {
                    if (closed || generation != ttsGeneration) return@post
                    ttsTimeout?.let(main::removeCallbacks)
                    ttsTimeout = null
                    val engine = tts
                    if (status != TextToSpeech.SUCCESS || engine == null) {
                        failTts("Não foi possível iniciar a síntese de voz. Verifique o mecanismo de texto para fala do Android.")
                        return@post
                    }
                    try {
                        loadVoiceCatalog(engine)
                        if (mutableState.value.voiceOptions.isEmpty()) {
                            failTts("Instale uma voz offline de português do Brasil nas configurações de texto para fala do Android para ouvir respostas.")
                            return@post
                        }
                        if (engine.setOnUtteranceProgressListener(utteranceListener()) != TextToSpeech.SUCCESS) {
                            failTts("O mecanismo de voz não disponibilizou acompanhamento de reprodução. Verifique suas configurações.")
                            return@post
                        }
                        ttsReady = true
                        mutableState.value = mutableState.value.copy(loadingVoices = false, ttsAvailable = true)
                        playPendingSpeech()
                    } catch (_: RuntimeException) {
                        failTts("O mecanismo de voz não disponibilizou uma voz offline pt-BR. Verifique suas configurações.")
                    }
                }
            }
            ttsTimeout = Runnable {
                if (!closed && generation == ttsGeneration && !ttsReady) {
                    failTts("O mecanismo de voz demorou para iniciar. Tente novamente.")
                }
            }.also { main.postDelayed(it, 12_000L) }
        } catch (_: RuntimeException) {
            failTts("Não foi possível acessar o mecanismo de texto para fala do Android.")
        }
    }

    private fun utteranceListener() = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = onMain {
            if (!closed && utteranceId != null && utteranceId == currentUtterance) {
                mutableState.value = mutableState.value.copy(speaking = true, error = null)
                scheduleUtteranceTimeout(utteranceId, utteranceCompletionTimeoutMs,
                    "A reprodução ultrapassou o tempo esperado e foi interrompida. O texto permanece disponível.")
            }
        }

        override fun onDone(utteranceId: String?) = finishUtterance(utteranceId)

        @Deprecated("Android engines may still call this callback")
        override fun onError(utteranceId: String?) = finishUtterance(utteranceId, failed = true)

        override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance(utteranceId, failed = true)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finishUtterance(utteranceId)
    }

    private fun finishUtterance(id: String?, failed: Boolean = false) = onMain {
        if (closed || id == null || id != currentUtterance) return@onMain
        utteranceTimeout?.let(main::removeCallbacks)
        utteranceTimeout = null
        currentUtterance = null
        mutableState.value = mutableState.value.copy(
            speaking = false,
            error = if (failed) "A voz do Android não conseguiu falar esta resposta. O texto permanece no histórico." else mutableState.value.error,
        )
    }

    private fun playPendingSpeech() {
        val request = pendingSpeech ?: return
        val engine = tts ?: return
        if (!ttsReady || closed) return
        pendingSpeech = null
        val id = "auron-${++utteranceCounter}"
        currentUtterance = id
        try {
            // Recheck the engine: a voice pack may have been removed since the settings screen opened.
            loadVoiceCatalog(engine)
            val choice = VoiceSelection.choose(mutableState.value.voiceOptions, request.voiceName, request.voiceEngine)
            val selected = choice.voice?.let { installedTtsVoices[it.name] }
            if (selected == null || engine.setVoice(selected) != TextToSpeech.SUCCESS) {
                failTts("A voz offline pt-BR não está disponível. Atualize a lista ou instale uma voz nas configurações do Android.")
                return
            }
            mutableState.value = mutableState.value.copy(
                voiceNotice = if (choice.usedFallback) "A voz salva não está disponível neste mecanismo. Foi usada outra voz offline pt-BR; sua preferência foi preservada." else null,
            )
            if (engine.setSpeechRate(request.rate) != TextToSpeech.SUCCESS ||
                engine.setPitch(request.pitch) != TextToSpeech.SUCCESS) {
                finishUtterance(id, failed = true)
                return
            }
            // Broken engines can return SUCCESS and never deliver onStart/onDone.
            // These guards bound the request without blocking the UI or retaining it forever.
            utteranceCompletionTimeoutMs = (request.text.length * 120L / request.rate + 30_000L)
                .toLong().coerceIn(30_000L, 900_000L)
            scheduleUtteranceTimeout(id, 12_000L,
                "O mecanismo de voz não iniciou a reprodução. Tente novamente ou confira as configurações de voz do Android.")
            if (engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) != TextToSpeech.SUCCESS) {
                finishUtterance(id, failed = true)
            }
        } catch (_: RuntimeException) {
            finishUtterance(id, failed = true)
        }
    }

    private fun stopSpeechInternal() {
        pendingSpeech = null
        currentUtterance = null // Ignore delayed onDone/onStop for the interrupted item.
        utteranceTimeout?.let(main::removeCallbacks)
        utteranceTimeout = null
        runCatching { tts?.stop() }
        mutableState.value = mutableState.value.copy(speaking = false)
    }

    private fun failTts(message: String) {
        stopSpeechInternal()
        releaseTts()
        mutableState.value = mutableState.value.copy(error = message)
    }

    private fun releaseTts() {
        ++ttsGeneration
        ttsTimeout?.let(main::removeCallbacks)
        ttsTimeout = null
        ttsReady = false
        installedTtsVoices = emptyMap()
        mutableState.value = mutableState.value.copy(loadingVoices = false, ttsAvailable = false, voiceOptions = emptyList(), voiceEngine = "")
        val oldTts = tts
        tts = null
        runCatching { oldTts?.shutdown() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private fun scheduleUtteranceTimeout(id: String, delayMillis: Long, message: String) {
        utteranceTimeout?.let(main::removeCallbacks)
        utteranceTimeout = Runnable {
            if (!closed && id == currentUtterance) failTts(message)
        }.also { main.postDelayed(it, delayMillis) }
    }

    private fun loadVoiceCatalog(engine: TextToSpeech) {
        val engineId = engine.defaultEngine.orEmpty()
        val nativeVoices = engine.voices.orEmpty()
        val options = VoiceSelection.offlineBrazilian(nativeVoices.map {
            InstalledVoice(
                name = it.name,
                engine = engineId,
                language = it.locale.language,
                country = it.locale.country,
                networkRequired = it.isNetworkConnectionRequired,
                installed = !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
                quality = it.quality,
                latency = it.latency,
            )
        })
        val allowedNames = options.mapTo(mutableSetOf()) { it.name }
        installedTtsVoices = nativeVoices.filter { it.name in allowedNames }.associateBy { it.name }
        mutableState.value = mutableState.value.copy(voiceOptions = options, voiceEngine = engineId)
    }

    private data class SpeechRequest(val text: String, val rate: Float, val pitch: Float, val voiceName: String, val voiceEngine: String)

    private fun recognitionError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "O microfone não pôde ser usado. Verifique se outro aplicativo está usando o áudio."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "O serviço de voz não tem permissão para usar o microfone."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O serviço de voz informou uma falha de rede. Tente novamente ou use texto."
        SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi a fala. Tente novamente ou use texto."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma fala foi detectada. Toque no microfone para tentar novamente."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O serviço de voz está ocupado. Aguarde um momento e tente novamente."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "O serviço de voz não suporta português do Brasil. Use texto ou configure outro serviço no Android."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "O modelo de português do Brasil não está instalado. Instale-o nas configurações de fala do Android."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "O serviço de voz limitou as tentativas. Aguarde um momento e tente novamente."
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "O serviço de voz está indisponível. Tente novamente ou use texto."
        else -> "A escuta foi interrompida pelo serviço de voz (código $error). Tente novamente."
    }
}

data class VoiceState(
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val available: Boolean = true,
    val error: String? = null,
    val level: Float = 0f,
    val voiceOptions: List<InstalledVoice> = emptyList(),
    val loadingVoices: Boolean = false,
    val ttsAvailable: Boolean? = null,
    val voiceEngine: String = "",
    val voiceNotice: String? = null,
)
