package br.com.auron.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import br.com.auron.domain.Period
import br.com.auron.ui.theme.AuronColors
import br.com.auron.ui.theme.AuronTheme
import br.com.auron.voice.VoiceController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private data class Destination(val key: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("dashboard", "Comando", Icons.Outlined.Dashboard),
    Destination("chat", "Conversa", Icons.Outlined.Forum),
    Destination("routine", "Rotina", Icons.Outlined.Checklist),
    Destination("tasks", "Tarefas", Icons.Outlined.TaskAlt),
    Destination("history", "Histórico", Icons.Outlined.History),
    Destination("memory", "Memória", Icons.Outlined.Psychology),
    Destination("modules", "Módulos", Icons.Outlined.Apps),
    Destination("settings", "Configurações", Icons.Outlined.Settings),
)

@Composable
fun AuronApp(viewModel: AuronViewModel, voice: VoiceController, onExport: () -> Unit) {
    AuronTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val voiceState by voice.state.collectAsStateWithLifecycle()
        val authState by viewModel.auth.state.collectAsStateWithLifecycle()
        val connectionConfig by viewModel.auth.config.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val openingPreferences = remember(context) { OpeningPreferences(context) }
        val openingAppearance = remember { openingPreferences.read() }
        val scope = rememberCoroutineScope()
        val profileIdentity = if (!authState.ready) "" else if (authState.localMode) "local" else authState.session?.let { connectionConfig.supabaseUrl.trimEnd('/') + "|" + it.userId }.orEmpty()
        val profileMatches = profileIdentity.isNotEmpty() && state.profileId == profileIdentity
        var connectionsOpen by rememberSaveable { mutableStateOf(false) }
        var cloudOpen by rememberSaveable { mutableStateOf(false) }
        var introHandled by rememberSaveable { mutableStateOf(false) }
        var introDeadline by rememberSaveable { mutableLongStateOf(0L) }
        val animationsEnabled = rememberSystemAnimationsEnabled()
        var destination by rememberSaveable { mutableStateOf("dashboard") }
        var routinePeriod by rememberSaveable { mutableStateOf("MORNING") }
        var permissionError by rememberSaveable { mutableStateOf<String?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        fun voiceProfileIsCurrent(): Boolean {
            val status = viewModel.auth.state.value
            val identity = if (!status.ready) "" else if (status.localMode) "local" else status.session?.let {
                viewModel.auth.config.value.supabaseUrl.trimEnd('/') + "|" + it.userId
            }.orEmpty()
            return identity.isNotEmpty() && viewModel.state.value.ready && viewModel.state.value.profileId == identity
        }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionError = null
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && voiceProfileIsCurrent()) {
                    voice.start(viewModel.state.value.settings.allowOnlineSpeech)
                }
            } else {
                permissionError = "Microfone não autorizado. Você pode continuar usando texto ou permitir o microfone nas configurações do Android."
            }
        }
        val onVoice: () -> Unit = {
            permissionError = null
            if (voiceState.listening) voice.stopListening()
            else {
                voice.stopSpeaking()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    voice.start(state.settings.allowOnlineSpeech)
                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        val navigate: (String) -> Unit = { target ->
            when (target) {
                "routine/night" -> { routinePeriod = "NIGHT"; destination = "routine" }
                "routine/morning" -> { routinePeriod = "MORNING"; destination = "routine" }
                else -> if (destinations.any { it.key == target }) destination = target
            }
        }
        LaunchedEffect(viewModel, voice, lifecycle) {
            viewModel.events.collect { event ->
                when (event) {
                    is UiEvent.Navigate -> navigate(event.destination)
                    is UiEvent.Speak -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && voiceProfileIsCurrent() && viewModel.state.value.settings.speakReplies) {
                        val preferences = viewModel.state.value.settings
                        voice.speak(event.text, preferences.speechRate, preferences.speechPitch, preferences.speechVoiceName, preferences.speechVoiceEngine)
                    }
                    is UiEvent.OpenReading -> Unit // The ViewModel owns the persisted day and selected verses.
                    UiEvent.OpenAuth -> { voice.pause(); viewModel.auth.showLogin() }
                    UiEvent.OpenConnections -> { viewModel.auth.clearMessages(); connectionsOpen = true }
                }
            }
        }
        LaunchedEffect(profileIdentity) {
            voice.pause()
            snackbar.currentSnackbarData?.dismiss()
            permissionError = null
            destination = "dashboard"
            routinePeriod = "MORNING"
            cloudOpen = false
        }
        LaunchedEffect(Unit) {
            if (!introHandled) {
                introHandled = true
                if (openingAppearance.showIntro && !openingAppearance.reducedMotion && animationsEnabled) {
                    introDeadline = SystemClock.elapsedRealtime() + AURON_INTRO_DURATION_MS
                }
            }
        }
        LaunchedEffect(profileMatches, state.ready, state.settings.showIntro, state.settings.reducedMotion) {
            if (profileMatches && state.ready) {
                openingPreferences.save(state.settings.showIntro, state.settings.reducedMotion)
                if (!state.settings.showIntro || state.settings.reducedMotion) introDeadline = 0L
            }
        }
        LaunchedEffect(introDeadline) {
            if (introDeadline > 0L) {
                kotlinx.coroutines.delay((introDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                introDeadline = 0L
            }
        }
        LaunchedEffect(animationsEnabled, state.settings.reducedMotion) {
            if (!animationsEnabled || state.settings.reducedMotion) introDeadline = 0L
        }
        LaunchedEffect(state.error) {
            state.error?.let { snackbar.showSnackbar(it); viewModel.dismissError() }
        }
        LaunchedEffect(Unit) {
            while (true) {
                viewModel.refreshDay()
                kotlinx.coroutines.delay(30_000)
            }
        }
        if (!authState.ready) {
            ProfileLoading("Preparando acesso seguro…")
        } else if (!authState.localMode && authState.session == null) {
            key(connectionConfig.supabaseUrl) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    AuthScreen(authState, connectionConfig.supabaseUrl.isNotBlank() && connectionConfig.supabasePublishableKey.isNotBlank(),
                        onLogin = { email, password -> scope.launch { viewModel.auth.signIn(email, password) } },
                        onRegister = { email, password -> scope.launch { viewModel.auth.signUp(email, password, ownerPolicyConfirmed = true) } },
                        onReset = { email -> scope.launch { viewModel.auth.requestPasswordReset(email) } },
                        onLocal = { viewModel.auth.enterLocalMode() },
                        onConfigure = { viewModel.auth.clearMessages(); connectionsOpen = true })
                }
            }
        } else if (!profileMatches) {
            ProfileLoading("Abrindo os registros deste perfil…")
        } else key(profileIdentity) {
        BoxWithConstraints(Modifier.fillMaxSize().background(AuronColors.Background)) {
            val expanded = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                if (expanded) {
                    SideNavigation(destination, navigate, state.profileLabel, state.aiStatus)
                    VerticalDivider(color = AuronColors.Elevated)
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = AuronColors.Background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        Surface(color = AuronColors.Background) {
                            Column(Modifier.statusBarsPadding()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (expanded) destinations.first { it.key == destination }.label else "AURON", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("SISTEMA DE INTELIGÊNCIA PESSOAL", style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted)
                                    }
                                    if (!expanded) {
                                        IconButton(onClick = { navigate("history") }, modifier = Modifier.testTag("nav_history")) { Icon(Icons.Outlined.History, "Histórico") }
                                        IconButton(onClick = { navigate("settings") }, modifier = Modifier.testTag("nav_settings")) { Icon(Icons.Outlined.Settings, "Configurações") }
                                    } else {
                                        Text(state.aiStatus, color = AuronColors.Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(max = 220.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (voiceState.listening || voiceState.speaking) {
                                    Row(Modifier.fillMaxWidth().background(AuronColors.Surface).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (voiceState.listening) Icons.Outlined.Mic else Icons.Outlined.VolumeUp, null, tint = AuronColors.Gold, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (voiceState.listening) "Microfone ativo · ouvindo" else "AURON falando", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { voice.stopListening(); voice.stopSpeaking() }) { Text("Interromper") }
                                    }
                                }
                                (permissionError ?: voiceState.error)?.let { error ->
                                    Text(error, color = AuronColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(AuronColors.Surface).padding(horizontal = 20.dp, vertical = 10.dp))
                                }
                                HorizontalDivider(color = AuronColors.Elevated)
                            }
                        }
                    },
                    bottomBar = {
                        if (!expanded) NavigationBar(containerColor = AuronColors.Surface) {
                            destinations.filter { it.key in listOf("dashboard", "chat", "routine", "tasks", "modules") }.forEach { item ->
                                NavigationBarItem(
                                    selected = destination == item.key,
                                    onClick = { navigate(item.key) },
                                    icon = { Icon(item.icon, item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("nav_${item.key}"),
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = AuronColors.Elevated, selectedIconColor = AuronColors.Gold, selectedTextColor = AuronColors.Gold),
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        if (state.loadError != null) {
                            Column(Modifier.align(Alignment.Center).widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.ErrorOutline, null, tint = AuronColors.Danger, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Não foi possível carregar seus registros", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(requireNotNull(state.loadError), color = AuronColors.Muted, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(20.dp))
                                Button(onClick = viewModel::retryLoad, modifier = Modifier.testTag("load_retry")) { Text("Tentar novamente") }
                            }
                        } else if (!state.ready) {
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AuronColors.Gold)
                                Spacer(Modifier.height(16.dp))
                                Text("Preparando seu centro de comando…", color = AuronColors.Muted)
                            }
                        } else when (destination) {
                            "dashboard" -> DashboardScreen(state, navigate, onVoice, voiceState.listening, voiceState.speaking, voice::stopSpeaking, viewModel::toggleRoutine, viewModel::toggleTask) { text -> navigate("chat"); viewModel.send(text) }
                            "chat" -> ChatScreen(state, voiceState, onSend = viewModel::send, onVoice = onVoice, onStopSpeaking = voice::stopSpeaking)
                            "routine" -> RoutineScreen(state, Period.valueOf(routinePeriod), { routinePeriod = it.name }, viewModel::toggleRoutine, viewModel::saveRoutine, viewModel::addRoutine, viewModel::deleteRoutine)
                            "tasks" -> TasksScreen(state, viewModel::addTask, viewModel::updateTask, viewModel::toggleTask, viewModel::deleteTask)
                            "history" -> HistoryScreen(state, viewModel::toggleTask)
                            "memory" -> if (state.memoryError != null) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Memórias temporariamente indisponíveis", style = MaterialTheme.typography.titleLarge)
                                    Text(requireNotNull(state.memoryError), color = AuronColors.Danger)
                                    Button(onClick = viewModel::retryLoad) { Text("Recarregar dados") }
                                }
                            } else MemoryScreen(state, { viewModel.saveSettings(state.settings.copy(memoryEnabled = true)) }, viewModel::saveMemory, viewModel::deleteMemory) { navigate("settings") }
                            "modules" -> ModulesScreen(navigate)
                            "settings" -> SettingsScreen(state.settings, viewModel::saveSettings, viewModel::clearChat, { voice.pause(); viewModel.clearAll() }, onExport, authState, state.profileLabel, state.aiStatus,
                                login = { voice.pause(); viewModel.requestLogin() }, logout = { voice.pause(); viewModel.logout() }, importLegacy = viewModel::importLegacyData,
                                connections = { viewModel.auth.clearMessages(); connectionsOpen = true }, openMemory = { navigate("memory") },
                                voiceState = voiceState,
                                previewVoice = { preferences ->
                                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && voiceProfileIsCurrent()) {
                                        voice.speak("Olá, comandante. Sou o AURON. Vamos organizar o dia com calma, clareza e um pouco de bom humor.", preferences.speechRate, preferences.speechPitch, preferences.speechVoiceName, preferences.speechVoiceEngine)
                                    }
                                }, refreshVoices = voice::refreshVoices, stopVoice = voice::stopSpeaking,
                                openCloud = { cloudOpen = true })
                        }
                    }
                }
            }
        }
        }
        if (profileMatches && authState.ready && (authState.localMode || authState.session != null)) {
            state.readingItemId?.let { itemId ->
                BibleReadingSheet(state.readingDay, state.readingVerses,
                    completed = state.routineCompletions.any { it.itemId == itemId && it.day == state.readingDay },
                    onComplete = { viewModel.completeReading(itemId, state.readingDay) }, onDismiss = viewModel::dismissReading)
            }
        }
        AnimatedVisibility(
            visible = introDeadline > 0L && !connectionsOpen,
            enter = fadeIn(tween(160)), exit = fadeOut(tween(320)),
        ) { AuronIntro(deadlineElapsedMs = introDeadline) { introDeadline = 0L } }
        if (cloudOpen && profileMatches && !authState.localMode && authState.session != null) {
            key(profileIdentity) {
                Dialog(onDismissRequest = { cloudOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Surface(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f).fillMaxHeight(0.94f), color = AuronColors.Surface, shape = RoundedCornerShape(24.dp)) {
                        CloudBackupScreen(state.cloud, viewModel::inspectCloud, viewModel::uploadCloud, viewModel::deleteCloud) { cloudOpen = false }
                    }
                }
            }
        }
        if (connectionsOpen) {
            Dialog(onDismissRequest = { connectionsOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f).fillMaxHeight(0.94f).imePadding(), color = AuronColors.Surface, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Conexões do AURON", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { connectionsOpen = false }) { Icon(Icons.Outlined.Close, "Fechar conexões") }
                        }
                        authState.error?.let { Text(it, color = AuronColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 10.dp)) }
                        authState.notice?.let { Text(it, color = AuronColors.Success, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 10.dp)) }
                        Box(Modifier.weight(1f)) { ConnectionSettings(connectionConfig, authState.busy) { config -> scope.launch { viewModel.auth.saveConfig(config) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideNavigation(current: String, navigate: (String) -> Unit, profileLabel: String, aiStatus: String) {
    Column(Modifier.width(210.dp).fillMaxHeight().background(AuronColors.Surface).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(48.dp).border(1.dp, AuronColors.Gold, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = AuronColors.Gold)
        }
        Spacer(Modifier.height(18.dp))
        Text("AURON", style = MaterialTheme.typography.headlineMedium, color = AuronColors.Gold, fontWeight = FontWeight.Bold)
        Text("SEU CENTRO DE COMANDO", style = MaterialTheme.typography.labelSmall, color = AuronColors.Muted)
        Spacer(Modifier.height(36.dp))
        destinations.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(item.icon, null, modifier = Modifier.size(21.dp)) },
                selected = current == item.key,
                onClick = { navigate(item.key) },
                modifier = Modifier.padding(bottom = 6.dp).testTag("nav_${item.key}"),
                shape = RoundedCornerShape(14.dp),
                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = AuronColors.Elevated, selectedIconColor = AuronColors.Gold, selectedTextColor = AuronColors.Gold, unselectedContainerColor = AuronColors.Surface, unselectedIconColor = AuronColors.Muted, unselectedTextColor = AuronColors.Muted),
            )
        }
        Spacer(Modifier.height(40.dp))
        HorizontalDivider(color = AuronColors.Elevated)
        Spacer(Modifier.height(18.dp))
        Text("AURON • v0.2.1", style = MaterialTheme.typography.labelSmall, color = AuronColors.Gold)
        Spacer(Modifier.height(6.dp))
        Text("$profileLabel\n$aiStatus", style = MaterialTheme.typography.bodySmall, color = AuronColors.Muted)
    }
}

@Composable
private fun ProfileLoading(message: String) {
    Surface(color = AuronColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AuronColors.Gold)
            Spacer(Modifier.height(16.dp))
            Text(message, color = AuronColors.Muted)
        }
    }
}
