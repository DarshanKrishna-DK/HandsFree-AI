package com.example.handsfree.agent

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import com.example.handsfree.MainActivity
import com.example.handsfree.accessibility.ServiceConnection
import com.example.handsfree.accessibility.AgentAccessibilityService
import com.example.handsfree.apps.AppDiscoveryManager
import com.example.handsfree.apps.LaunchableApp
import kotlinx.coroutines.*
import java.util.UUID

data class PendingConsent(val message: String, val clarification: Boolean = false,
                          val apps: List<LaunchableApp> = emptyList())
data class ConversationMessage(val text: String, val fromUser: Boolean)

class AgentController(private val context: Context) {
    companion object {
        private var instance: AgentController? = null
        fun get(context: Context): AgentController = instance ?: AgentController(context.applicationContext).also { instance = it }
        val storeChoices = mapOf("com.google.android.calculator" to "Google Calculator",
            "in.swiggy.android" to "Swiggy", "com.application.zomato" to "Zomato")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backend = BackendClient()
    private val discovery = AppDiscoveryManager(context)
    private val policy = SafetyPolicyEngine()
    private var job: Job? = null
    private var generation = 0L
    var voiceRevision = 0L; private set
    var resetVersion by mutableStateOf(0L); private set
    private var reply: CompletableDeferred<String?>? = null
    private val approvedApps = mutableSetOf<String>()
    private var constraints = mutableListOf<String>()
    var status by mutableStateOf(AgentStatus.IDLE); private set
    var goal by mutableStateOf(""); private set
    var message by mutableStateOf("Enter a task to begin."); private set
    var history by mutableStateOf<List<ActionRecord>>(emptyList()); private set
    var pending by mutableStateOf<PendingConsent?>(null); private set
    var connection by mutableStateOf("Not checked"); private set
    var voiceConfigured by mutableStateOf(false); private set
    var latestResponse by mutableStateOf(""); private set
    var lastAction by mutableStateOf(""); private set
    var conversation by mutableStateOf<List<ConversationMessage>>(emptyList()); private set
    var connected by mutableStateOf(false); private set
    private fun chat(text: String, fromUser: Boolean = false) {
        conversation = (conversation + ConversationMessage(text, fromUser)).takeLast(30)
    }
    val running get() = status.isRunning

    fun checkConnection() { scope.launch {
        connection = "Checking…"
        try {
            val health = backend.health()
            voiceConfigured = health.optBoolean("voice_configured")
            connected = true
            connection = "Laptop connected · ${health.optString("model")}"
        } catch (error: Exception) { connected = false; connection = error.message ?: "Connection failed" }
    } }
    private fun update(state: AgentStatus, text: String) {
        status = state; message = text
        if (com.example.handsfree.BuildConfig.DEBUG) android.util.Log.d("HandsFreeAgent", "state=${state.name}")
        ServiceConnection.service?.updatePanel("HandsFree AI · ${state.name.lowercase()}\n$text")
    }
    private fun record(action: String, result: String) { history = (history + ActionRecord(action, result.take(500))).takeLast(40) }
    fun respond(answer: String?) {
        if (reply?.isCompleted == false) {
            voiceRevision++
            if (!answer.isNullOrBlank()) chat(answer, true)
            VoiceController.get(context).cancelTurn()
            reply?.complete(answer)
        }
    }
    fun listenAgain() { pending?.let { prompt ->
        VoiceController.get(context).ask(prompt.message + if (!prompt.clarification) " Say approve or decline." else "")
    } }
    fun receiveVoice(text: String, capturedRevision: Long) {
        if (text.isBlank()) return
        when (ConversationCommand.parse(text)) {
            ConversationCommand.STOP -> { stop(); return }
            ConversationCommand.NEW_TASK -> { newConversation(); return }
            ConversationCommand.HOME -> { newConversation(openHome = true); return }
            ConversationCommand.PHONE_HOME -> {
                newConversation()
                ServiceConnection.service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                return
            }
            ConversationCommand.NONE -> Unit
        }
        if (!ConversationCommand.acceptsReply(capturedRevision, voiceRevision)) return
        pending?.let { handleVoiceReply(text, it); return }
        if (!running) { start(text); return }
        // New instructions invalidate an in-flight plan; they never authorize its action.
        if (VoiceReply.parse(text, false) in setOf(VoiceReply.APPROVE, VoiceReply.REJECT)) return
        constraints += "User added: ${text.take(1000)}"
        voiceRevision++
        chat(text, true)
        record("user_update", text)
        VoiceController.get(context).speak("I've noted that. I'll check the next step with your updated request.")
    }
    private fun handleVoiceReply(text: String, prompt: PendingConsent) {
        if (pending !== prompt) return
        when (VoiceReply.parse(text, prompt.clarification)) {
            VoiceReply.STOP -> stop()
            VoiceReply.APPROVE -> respond("approved")
            VoiceReply.REJECT -> respond(null)
            VoiceReply.ANSWER -> respond(text)
            VoiceReply.UNCLEAR -> {
                val guidance = "Please say approve, decline, or stop."
                ServiceConnection.service?.updatePanel(guidance)
                VoiceController.get(context).ask(guidance)
            }
        }
    }
    fun chooseApp(app: LaunchableApp) {
        approvedApps += app.packageName
        ServiceConnection.service?.approvedPackages = approvedApps.toSet()
        respond(app.name)
    }
    private fun cancelTask() {
        generation++; voiceRevision++
        job?.cancel(); job = null; reply?.cancel(); reply = null; pending = null
        approvedApps.clear()
        ServiceConnection.service?.approvedPackages = emptySet()
        ServiceConnection.service?.hidePanel()
    }
    fun newConversation(openHome: Boolean = false) {
        cancelTask()
        VoiceController.get(context).cancelTurn()
        goal = ""; history = emptyList(); constraints.clear(); conversation = emptyList()
        latestResponse = ""; lastAction = ""; resetVersion++
        update(AgentStatus.IDLE, "What would you like to do?")
        if (openHome) context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        VoiceController.get(context).ask("What would you like to do next?")
    }
    fun privateInputDetected() {
        cancelTask()
        VoiceController.get(context).endSession()
        update(AgentStatus.FAILED, "Private input is on screen. Conversation ended. Complete that step yourself, then return Home to start again.")
    }
    fun stop() {
        if (com.example.handsfree.BuildConfig.DEBUG) android.util.Log.d("HandsFreeAgent", "Stop requested", Throwable("Cancellation origin"))
        cancelTask()
        VoiceController.get(context).endSession()
        ServiceConnection.service?.approvedPackages = emptySet()
        ServiceConnection.service?.hidePanel()
        update(AgentStatus.STOPPED, "Stopped. No further actions will run.")
        record("stop_task", "User stopped the task")
    }

    private suspend fun awaitUser(text: String, clarification: Boolean = false, apps: List<LaunchableApp> = emptyList()): String? {
        val deferred = CompletableDeferred<String?>()
        voiceRevision++
        reply = deferred; pending = PendingConsent(text, clarification, apps)
        val prompt = pending!!
        VoiceController.get(context).onListeningChanged = { listening ->
            if (pending === prompt) ServiceConnection.service?.updatePanel(text + if (listening) "\nListening to your reply…" else "")
        }
        chat(text)
        update(AgentStatus.WAITING_FOR_USER, text)
        ServiceConnection.service?.showPanel(text,
            approval = if (!clarification) ({ respond("approved") }) else null,
            reject = { respond(null) },
            replyAction = if (clarification) ({
                VoiceController.get(context).cancelTurn()
                ServiceConnection.service?.hidePanel()
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) else null,
            choices = apps.take(6).map { app -> app.name to { chooseApp(app) } },
            listen = { listenAgain() })
        VoiceController.get(context).ask(text + if (!clarification) " Say approve or decline." else "")
        return try { withTimeout(10 * 60_000L) { deferred.await() } }
        finally {
            if (reply === deferred) {
                reply = null; pending = null
                voiceRevision++
                VoiceController.get(context).cancelTurn()
                VoiceController.get(context).onListeningChanged = null
                if (currentCoroutineContext().isActive) ServiceConnection.service?.showPanel("Preparing the next step")
            }
        }
    }

    private suspend fun prepareFood(apps: List<LaunchableApp>, service: AgentAccessibilityService): Boolean {
        val intake = FoodIntake(goal)
        val candidates = apps.filter { it.packageName in FoodIntake.foodPackages }
        if (candidates.isEmpty()) {
            awaitUser("I couldn't find a supported food delivery app. Install a food app first, then try again. Which app would you prefer?", true)
            error("Install your preferred food app, then start again.")
        }
        val automatic = FoodIntake.automaticPackage(goal, candidates.map { it.packageName })
        var selected = candidates.singleOrNull { it.packageName == automatic }
        var attempts = 0
        while (selected == null && attempts++ < 3) {
            val response = awaitUser("Which app would you like to use? ${candidates.joinToString { it.name }}.", true, candidates)
                ?: return false
            selected = candidates.singleOrNull { response.contains(it.packageName, true) ||
                Regex("(?i)(?<!\\w)${Regex.escape(it.name)}(?!\\w)").containsMatchIn(response) }
        }
        val chosen = selected ?: error("I couldn't match that app. Please choose an app and try again.")
        approvedApps += chosen.packageName
        service.approvedPackages = approvedApps.toSet()
        constraints += "User-selected food app: ${chosen.name}, package ${chosen.packageName}. Stay in this app."
        update(AgentStatus.EXECUTING, "Opening ${chosen.name}")
        chat("Let's use ${chosen.name}.")
        val observation = service.observe()
        val outcome = service.execute(ProposedAction("open_app", observation.id,
            arguments = ActionArguments(packageName = chosen.packageName)), approvedApps, confirmed = true)
        check(outcome.accepted) { "${chosen.name} couldn't open. Please try again." }
        record("open_app", "Selected installed food app: ${chosen.packageName}")
        var observed = service.observe()
        var checks = 0
        while (observed.packageName != chosen.packageName && checks++ < 12) { delay(400); observed = service.observe() }
        check(observed.packageName == chosen.packageName) { "Please open ${chosen.name}, then try again." }
        while (true) {
            val question = intake.next() ?: break
            val answer = awaitUser(question.text, true) ?: return false
            if (!intake.answer(question.key, answer)) continue
        }
        constraints += intake.constraints()
        constraints += "Food cart completion: after the user selects observed items, add the approved quantities and open the cart. Read actual item/quantity evidence and the labeled final payable total. Populate cart_item_ids and cart_total_ids for finish_task. Never checkout or place an order."
        chat("Thanks. I'll look for options that match what you've told me, then ask you to choose.")
        return true
    }

    fun start(instruction: String) {
        if (running) return
        if (instruction.isBlank() || instruction.length > 2000) { message = "Enter a task of 1–2000 characters."; return }
        val service = ServiceConnection.service ?: run { message = "Enable HandsFree AI in Accessibility settings first."; return }
        job?.cancel(); VoiceController.get(context).cancelTurn(); voiceRevision++
        goal = instruction.trim(); history = emptyList(); constraints = mutableListOf(); approvedApps.clear()
        service.approvedPackages = emptySet()
        service.onStop = ::stop
        service.onDisconnected = { stop(); update(AgentStatus.FAILED, "Accessibility service disconnected. Enable it and start again.") }
        latestResponse = ""
        lastAction = ""
        conversation = listOf(ConversationMessage(goal, true))
        val runGeneration = ++generation
        job = scope.launch {
            val taskId = UUID.randomUUID().toString()
            val guard = LoopGuard()
            var noChanges = 0
            var modelFailures = 0
            var cartHandoff = false
            try {
                update(AgentStatus.UNDERSTANDING, "Getting ready to help")
                service.showPanel("HandsFree AI\nPreparing your task")
                backend.health()
                var apps = withContext(Dispatchers.IO) { discovery.discover() }
                if (FoodIntake.applies(goal) && !prepareFood(apps, service)) { stop(); return@launch }
                repeat(60) {
                    ensureActive()
                    update(AgentStatus.OBSERVING, "Reading the current approved screen")
                    service.approvedPackages = approvedApps.toSet()
                    val before = service.observe()
                    if (before.blockedReason != null) {
                        VoiceController.get(context).endSession()
                        error(before.blockedReason)
                    }
                    val openedApp = apps.find { it.packageName == before.packageName && it.packageName in approvedApps }
                    if (openedApp != null && AppGoalVerifier.isLaunchOnly(goal, openedApp.name, openedApp.packageName)) {
                        latestResponse = "Opened ${openedApp.name}. The active application was verified."
                        record("finish_task", "Verified foreground package: ${before.packageName}")
                        VoiceController.get(context).speak(latestResponse)
                        update(AgentStatus.COMPLETED, latestResponse)
                        return@launch
                    }
                    update(AgentStatus.PLANNING, "Working out the next step…")
                    val plannedRevision = voiceRevision
                    val action = try { backend.nextAction(taskId, goal, before, apps, history, constraints) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        record("planning_failed", error.message ?: "Invalid model response")
                        if (++modelFailures >= 2) throw error
                        delay(1_000); return@repeat
                    }
                    modelFailures = 0
                    if (plannedRevision != voiceRevision) { record("replan", "User changed the request during planning"); return@repeat }
                    lastAction = "${action.action.replace('_', ' ')} · ${action.reason}"
                    if (com.example.handsfree.BuildConfig.DEBUG) android.util.Log.d("HandsFreeAgent", "proposal=${action.action} target=${action.targetId}")
                    ensureActive()
                    val current = service.observe()
                    guard.checkFreshness(current.id == before.id)?.let { error(it) }
                    if (current.id != before.id) { record("stale_plan", "Screen changed during reasoning; replanning."); return@repeat }
                    guard.check("${action.action}:${action.targetId}:${action.arguments}", current.id)?.let { error(it) }
                    val decision = policy.evaluate(action, current, approvedApps)
                    if (decision.disposition == Disposition.DENY) {
                        record("rejected", decision.message)
                        if (action.action == "finish_task") error(decision.message)
                        return@repeat
                    }
                    var confirmed = false
                    if (decision.disposition == Disposition.CONFIRM) {
                        val packageName = action.arguments.packageName
                        if (action.action == "open_play_store" && packageName !in storeChoices) {
                            error("No verified Store choice for this package. Install the app manually and try again.")
                        }
                        val appName = apps.find { it.packageName == packageName }?.name ?: storeChoices[packageName]
                        val prompt = when(action.action) {
                            "open_app" -> "Open ${appName ?: packageName} for this task?"
                            "open_play_store" -> "Open the official Play Store listing for $appName? You decide whether to install."
                            else -> "${decision.message}\n${action.reason}"
                        }
                        if (awaitUser(prompt) == null) { stop(); return@launch }
                        confirmed = true
                        if (action.action == "open_app") { approvedApps += packageName!!; service.approvedPackages = approvedApps.toSet() }
                    }
                    ensureActive()
                    when (action.action) {
                        "stop_task" -> { stop(); return@launch }
                        "ask_user" -> {
                            val response = awaitUser(action.arguments.message!!, true)
                            if (response.isNullOrBlank()) { stop(); return@launch }
                            constraints += response.take(1000)
                            record("ask_user", "User replied: ${response.take(400)}")
                        }
                        "speak" -> {
                            latestResponse = "Observed information: " + current.elements
                                .filter { it.id in action.arguments.evidenceIds }
                                .joinToString("; ") { it.text.ifBlank { it.description } }
                            VoiceController.get(context).speak(latestResponse)
                            record("speak", latestResponse)
                        }
                        "finish_task" -> {
                            val fresh = service.observe()
                            if (FoodIntake.applies(goal)) {
                                val cart = CartReview.from(fresh, action.arguments)
                                if (fresh.id != action.observationId || cart == null) {
                                    record("cart_incomplete", "Cart items and an unambiguous labeled payable total are required. Open cart or bill details; never use subtotal or menu prices.")
                                    return@repeat
                                }
                                latestResponse = cart.spokenReview
                                if (awaitUser(cart.spokenReview) == null) { stop(); return@launch }
                                val reviewed = service.observe()
                                val checked = CartReview.from(reviewed, action.arguments)
                                check(reviewed.id == fresh.id && checked == cart) { "The cart changed while you reviewed it. Please start again to check the new total." }
                                latestResponse = cart.handoff
                                chat(latestResponse)
                                record("finish_task", "User confirmed current cart items and displayed payable total ${cart.amount}")
                                VoiceController.get(context).speak(latestResponse)
                                cartHandoff = true
                                update(AgentStatus.COMPLETED, latestResponse)
                                return@launch
                            }
                            check(fresh.id == action.observationId && CompletionVerifier.verified(goal, fresh, action.arguments)) {
                                "Completion could not be verified on the current screen."
                            }
                            // Arithmetic gets an independent check; other goals require human review.
                            if (CompletionVerifier.expectedCalculation(goal) == null || fresh.packageName !in SafetyPolicyEngine.calculatorPackages) {
                                if (awaitUser("Observed: ${action.arguments.expectedText}\nDoes this complete your task?") == null) { stop(); return@launch }
                            }
                            latestResponse = action.arguments.message!!
                            VoiceController.get(context).speak(latestResponse)
                            record("finish_task", "Verified visible evidence: ${action.arguments.expectedText}")
                            update(AgentStatus.COMPLETED, latestResponse)
                            return@launch
                        }
                        else -> {
                            update(AgentStatus.EXECUTING, action.reason)
                            val outcome = service.execute(action, approvedApps, confirmed)
                            val target = current.elements.find { it.id == action.targetId }
                            val detail = target?.let { " Target ${it.id}: ${it.text.ifBlank { it.description }.take(100)}." }
                                ?: action.arguments.packageName?.let { " Package: $it." }.orEmpty()
                            record(action.action, outcome.message + detail)
                            update(AgentStatus.VERIFYING, "Checking the screen after ${action.action}")
                            delay(700)
                            ensureActive()
                            var after = service.observe()
                            // Allow a cold app launch to settle without asking Gemma about our own UI.
                            if (outcome.accepted && action.action == "open_app") {
                                var launchChecks = 0
                                while (after.packageName != action.arguments.packageName && launchChecks++ < 8) {
                                    delay(400)
                                    ensureActive()
                                    after = service.observe()
                                }
                            }
                            val launched = apps.find { it.packageName == action.arguments.packageName }
                            if (outcome.accepted && action.action == "open_app" && launched != null &&
                                after.packageName == launched.packageName &&
                                AppGoalVerifier.isLaunchOnly(goal, launched.name, launched.packageName)) {
                                latestResponse = "Opened ${launched.name}. The active application was verified."
                                record("finish_task", "Verified foreground package: ${after.packageName}")
                                VoiceController.get(context).speak(latestResponse)
                                update(AgentStatus.COMPLETED, latestResponse)
                                return@launch
                            }
                            if (!outcome.accepted || (after.id == current.id && action.action !in setOf("read_screen", "open_play_store"))) noChanges++ else noChanges = 0
                            check(noChanges < 3) { "The screen is not responding. Please review the app before retrying." }
                            record("observation", if (after.id != current.id) "Screen changed; active app ${after.packageName}" else "Screen unchanged")
                            if (action.action == "open_play_store" && outcome.accepted) {
                                if (awaitUser("Install the app yourself in Play Store, then tap Approve to check and resume.") == null) { stop(); return@launch }
                                apps = withContext(Dispatchers.IO) { discovery.discover() }
                                check(apps.any { it.packageName == action.arguments.packageName }) { "App is not installed yet. Finish installation and start again." }
                                constraints += "User installed ${action.arguments.packageName} and approved resuming."
                                approvedApps += action.arguments.packageName!!
                                service.approvedPackages = approvedApps.toSet()
                            }
                        }
                    }
                }
                error("Planning limit reached. Review the task and try again.")
            } catch (cancelled: CancellationException) {
                if (runGeneration == generation) update(AgentStatus.STOPPED, "Task cancelled.")
            } catch (error: Exception) {
                if (runGeneration == generation) {
                    update(AgentStatus.FAILED, error.message ?: "Task failed safely. Please retry.")
                    record("failed", message)
                }
            } finally {
                if (runGeneration == generation) {
                    pending = null; reply = null; service.approvedPackages = emptySet()
                    val voice = VoiceController.get(context)
                    if (currentCoroutineContext().isActive && status == AgentStatus.COMPLETED) {
                        withTimeoutOrNull(65_000) { while (voice.speaking) delay(100) }
                    }
                    if (runGeneration == generation) {
                        voiceRevision++
                        job = null
                        service.hidePanel()
                        if (voice.sessionActive && status in setOf(AgentStatus.COMPLETED, AgentStatus.FAILED))
                            if (cartHandoff) voice.endSession()
                            else voice.speak((if (status == AgentStatus.FAILED) "$message " else "") + "You can tell me another task, say home, or say stop conversation.")
                    }
                }
            }
        }
    }
}
