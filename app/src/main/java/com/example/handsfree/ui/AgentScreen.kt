package com.example.handsfree.ui

import com.example.handsfree.ui.uiText
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.handsfree.BuildConfig
import com.example.handsfree.R
import com.example.handsfree.accessibility.ServiceConnection
import com.example.handsfree.agent.*
import com.example.handsfree.apps.AppDiscoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AgentScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { AgentController.get(context) }
    val voice = remember { VoiceController.get(context) }
    val service = ServiceConnection.service
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("conversation", 0) }
    fun selectLanguage(code: String) {
        if (controller.running || voice.language == code) return
        voice.endSession()
        controller.newConversation()
        AppLanguage.select(context as Activity, code)
    }
    var tab by rememberSaveable { mutableStateOf("Assistant") }
    var instruction by rememberSaveable { mutableStateOf(controller.goal) }
    var answer by rememberSaveable { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }
    var disclose by remember { mutableStateOf(false) }
    var voiceDisclosure by remember { mutableStateOf(false) }
    var voiceAction by remember { mutableStateOf("record") }
    var inspectQuery by rememberSaveable { mutableStateOf("") }
    var debugMessage by remember { mutableStateOf("") }
    LaunchedEffect(controller.resetVersion) { instruction = ""; answer = ""; tab = "Assistant" }
    BackHandler(enabled = controller.running || controller.conversation.isNotEmpty() || tab != "Assistant") {
        controller.newConversation(); tab = "Assistant"
    }
    fun continueAction() {
        if (voice.hasPermission() && voice.conversationEnabled && !voice.beginSession()) return
        when (voiceAction) {
            "start" -> controller.start(instruction)
            "reply" -> controller.listenAgain()
            // beginSession starts listening immediately; do not wait for a network-generated greeting.
            else -> Unit
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) continueAction()
        else { voice.conversationEnabled = false; if (voiceAction == "start") controller.start(instruction) }
    }
    fun requestVoice(action: String) {
        voice.conversationEnabled = true
        voiceAction = action
        if (!preferences.getBoolean("audio_disclosure", false)) voiceDisclosure = true
        else if (!voice.hasPermission()) microphone.launch(Manifest.permission.RECORD_AUDIO)
        else continueAction()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && controller.pending?.clarification == true) service?.hidePanel()
            if (event == Lifecycle.Event.ON_RESUME) controller.checkConnection()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { controller.checkConnection() }
    val status = when {
        voice.recording -> context.uiText(R.string.listening_to_you)
        voice.busy -> context.uiText(R.string.understanding_reply)
        voice.speaking -> context.uiText(R.string.speaking)
        controller.status == AgentStatus.WAITING_FOR_USER -> context.uiText(R.string.your_turn)
        controller.status == AgentStatus.COMPLETED -> context.uiText(R.string.all_done)
        controller.status == AgentStatus.FAILED -> context.uiText(R.string.try_again)
        controller.running -> context.uiText(R.string.working)
        service == null -> context.uiText(R.string.setup_step)
        !controller.connected -> context.uiText(R.string.connection_attention)
        else -> context.uiText(R.string.ready)
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.handsfree_logo), "HandsFree logo", Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Text("HandsFree", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { settings = true }) { Text(context.uiText(R.string.settings)) }
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { controller.newConversation(); tab = "Assistant" }) { Text(context.uiText(R.string.home)) }
            listOf("Assistant", "Your apps").forEach { title ->
                FilterChip(selected = tab == title, onClick = { tab = title }, label = { Text(context.uiText(if (title == "Assistant") R.string.assistant else R.string.your_apps)) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(context.uiText(R.string.language), style = MaterialTheme.typography.labelLarge)
            FilterChip(selected = voice.language == "en-IN", enabled = !controller.running,
                onClick = { selectLanguage("en-IN") }, label = { Text("English") })
            FilterChip(selected = voice.language == "kn-IN", enabled = !controller.running,
                onClick = { selectLanguage("kn-IN") }, label = { Text("ಕನ್ನಡ") })
        }
        if (controller.conversation.isNotEmpty()) TextButton(
            onClick = { controller.newConversation(); tab = "Assistant" }, modifier = Modifier.padding(horizontal = 24.dp)
        ) { Text(context.uiText(R.string.new_conversation)) }
        if (tab == "Your apps") AppBrowser(Modifier.weight(1f))
        else {
            val scroll = rememberScrollState()
            LaunchedEffect(controller.conversation.size, controller.resetVersion, voice.language) {
                if (controller.conversation.isEmpty()) scroll.scrollTo(0)
                else scroll.animateScrollTo(scroll.maxValue)
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(status, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                if (controller.conversation.isEmpty()) {
                    Text(context.uiText(R.string.hero), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(context.uiText(R.string.intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(context.uiText(R.string.food_title), style = MaterialTheme.typography.titleMedium)
                            Text(context.uiText(R.string.food_intro), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { instruction = context.uiText(R.string.food_help) }, contentPadding = PaddingValues(0.dp)) { Text(context.uiText(R.string.food_help_button)) }
                        }
                    }
                }
                if (service == null) Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(context.uiText(R.string.screen_title), style = MaterialTheme.typography.titleMedium)
                        Text(context.uiText(R.string.screen_intro), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { disclose = true }) { Text(context.uiText(R.string.screen_setup)) }
                    }
                }
                if (!controller.connected) TextButton(onClick = { controller.checkConnection() }) { Text(context.uiText(R.string.reconnect)) }
                controller.conversation.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (entry.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(Modifier.widthIn(max = 340.dp), shape = RoundedCornerShape(20.dp),
                            color = if (entry.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (entry.fromUser) context.uiText(R.string.you) else "HandsFree", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                if (controller.running && controller.pending == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(context.uiText(R.string.working_hint), style = MaterialTheme.typography.bodyMedium)
                }
                if (controller.status in setOf(AgentStatus.FAILED, AgentStatus.STOPPED, AgentStatus.COMPLETED)) {
                    Text(if (controller.status == AgentStatus.COMPLETED) controller.latestResponse else controller.message,
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (voice.message.isNotBlank()) Text(voice.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!controller.running) OutlinedTextField(instruction, onValueChange = { instruction = it.take(2000) },
                        placeholder = { Text(context.uiText(R.string.instruction)) }, shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (controller.running) {
                            if (!voice.sessionActive) OutlinedButton(onClick = { requestVoice(if (controller.pending != null) "reply" else "conversation") }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(context.uiText(R.string.start_conversation)) }
                            Button(onClick = { controller.stop() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(context.uiText(R.string.stop)) }
                        } else {
                            OutlinedButton(onClick = { if (voice.sessionActive) controller.stop() else requestVoice("conversation") },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(if (voice.sessionActive) context.uiText(R.string.end_conversation) else context.uiText(R.string.start_conversation)) }
                            Button(onClick = { if (voice.conversationEnabled) requestVoice("start") else controller.start(instruction) },
                                enabled = instruction.isNotBlank() && service != null,
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(context.uiText(R.string.send_task)) }
                        }
                    }
                    Text(if (voice.sessionActive) context.uiText(R.string.conversation_on, if (voice.language == "kn-IN") "ಕನ್ನಡ" else "English") else context.uiText(R.string.start_hint),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(context.uiText(R.string.silence_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text(context.uiText(R.string.personalize)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(context.uiText(R.string.voice_conversation), Modifier.weight(1f))
                Switch(checked = voice.conversationEnabled, onCheckedChange = { voice.conversationEnabled = it; if (!it) voice.endSession() })
            }
            Text(context.uiText(R.string.voice_help), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = voice.language == "en-IN", enabled = !controller.running, onClick = { selectLanguage("en-IN") }, label = { Text("English") })
                FilterChip(selected = voice.language == "kn-IN", enabled = !controller.running, onClick = { selectLanguage("kn-IN") }, label = { Text("ಕನ್ನಡ") })
            }
            if (controller.running) Text(context.uiText(R.string.language_locked), style = MaterialTheme.typography.bodySmall)
            Text(if (service != null) context.uiText(R.string.screen_on) else context.uiText(R.string.screen_needed))
            TextButton(onClick = { settings = false; disclose = true }) { Text(context.uiText(R.string.screen_settings)) }
            if (BuildConfig.DEBUG) TextButton(onClick = { settings = false; diagnostics = true }) { Text(context.uiText(R.string.developer_tools)) }
        }
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text(context.uiText(R.string.done)) } })
    if (disclose) AlertDialog(onDismissRequest = { disclose = false }, title = { Text(context.uiText(R.string.allow_screen)) },
        text = { Text(context.uiText(R.string.screen_disclosure)) },
        confirmButton = { TextButton(onClick = { disclose = false; context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text(context.uiText(R.string.open_settings)) } },
        dismissButton = { TextButton(onClick = { disclose = false }) { Text(context.uiText(R.string.not_now)) } })
    if (voiceDisclosure) AlertDialog(onDismissRequest = { voiceDisclosure = false }, title = { Text(context.uiText(R.string.voice_title)) },
        text = { Text(context.uiText(R.string.voice_disclosure)) },
        confirmButton = { TextButton(onClick = { voiceDisclosure = false; preferences.edit().putBoolean("audio_disclosure", true).apply(); microphone.launch(Manifest.permission.RECORD_AUDIO) }) { Text(context.uiText(R.string.enable_voice)) } },
        dismissButton = { TextButton(onClick = { voiceDisclosure = false; voice.conversationEnabled = false; if (voiceAction == "start") controller.start(instruction) }) { Text(context.uiText(R.string.use_typing)) } })
    controller.pending?.takeIf { it.clarification || service == null }?.let { pending ->
        LaunchedEffect(pending) {
            answer = ""
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) service?.hidePanel()
        }
        AlertDialog(onDismissRequest = { controller.newConversation() }, title = { Text(if (pending.apps.isEmpty()) context.uiText(R.string.your_turn) else context.uiText(R.string.choose_app)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pending.message)
                TextButton(onClick = { controller.newConversation() }) { Text(context.uiText(R.string.home_new)) }
                pending.apps.forEach { app ->
                    OutlinedButton(onClick = { controller.chooseApp(app) }, modifier = Modifier.fillMaxWidth()) {
                        Image(app.icon.asImageBitmap(), null, Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)); Text(app.name)
                    }
                }
                OutlinedTextField(answer, onValueChange = { answer = it.take(1000) }, label = { Text(context.uiText(R.string.your_reply)) }, shape = RoundedCornerShape(16.dp))
                TextButton(onClick = { requestVoice("reply") }) { Text(if (voice.recording) context.uiText(R.string.listening) else context.uiText(R.string.listen_again)) }
                if (voice.lastTranscript.isNotBlank()) Text(context.uiText(R.string.heard, voice.lastTranscript), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = answer.isNotBlank(), onClick = { controller.respond(answer) }) { Text(context.uiText(R.string.continue_button)) } },
            dismissButton = { TextButton(onClick = { controller.stop() }) { Text(context.uiText(R.string.stop)) } })
    }
    if (diagnostics) AlertDialog(onDismissRequest = { diagnostics = false }, title = { Text(context.uiText(R.string.developer_tools)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(controller.connection)
            OutlinedTextField(inspectQuery, onValueChange = { inspectQuery = it }, label = { Text("App name to inspect") })
            TextButton(enabled = service != null && !controller.running, onClick = { scope.launch {
                val manager = AppDiscoveryManager(context)
                val apps = withContext(Dispatchers.IO) { manager.discover() }
                val app = apps.singleOrNull { it.name.equals(inspectQuery.trim(), true) || it.packageName == inspectQuery.trim() }
                if (app == null) debugMessage = "Choose an exact installed app name."
                else { service!!.approvedPackages = setOf(app.packageName); manager.open(app.packageName); delay(1500); service.observe(); service.approvedPackages = emptySet() }
            } }) { Text("Inspect app") }
            Text(debugMessage)
            ServiceConnection.observation?.let { observed ->
                Text("${observed.packageName}\n${observed.id}\n${observed.blockedReason.orEmpty()}")
                observed.elements.forEach { Text("${it.id}: ${it.text} ${it.description} · ${it.actions}", style = MaterialTheme.typography.bodySmall) }
            }
            Text(controller.lastAction)
            controller.history.takeLast(12).forEach { Text("${it.action}: ${it.result}", style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { diagnostics = false }) { Text(context.uiText(R.string.close)) } })
}
