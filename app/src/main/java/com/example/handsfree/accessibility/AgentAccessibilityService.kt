package com.example.handsfree.accessibility

import com.example.handsfree.ui.uiText
import com.example.handsfree.R
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.handsfree.agent.*
import com.example.handsfree.apps.AppDiscoveryManager
import com.example.handsfree.apps.LaunchResult
import java.util.UUID

object ServiceConnection {
    var service by mutableStateOf<AgentAccessibilityService?>(null)
    var observation by mutableStateOf<ScreenObservation?>(null)
}

class AgentAccessibilityService : AccessibilityService() {
    private val nodeHandles = mutableMapOf<String, AccessibilityNodeInfo>()
    private var lastFingerprint = ""
    private var snapshot: ScreenObservation? = null
    private var panel: LinearLayout? = null
    private var statusText: TextView? = null
    var onStop: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var approvedPackages: Set<String> = emptySet()
    private val policy = SafetyPolicyEngine()
    private val privacyHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var privacyCheckPending = false

    override fun onServiceConnected() { ServiceConnection.service = this }
    // During an explicitly started voice session, inspect privacy flags locally only.
    // No screen values are stored or transmitted by this guard.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!VoiceController.get(this).sessionActive || privacyCheckPending) return
        if (event?.eventType !in setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) return
        privacyCheckPending = true
        privacyHandler.postDelayed({
            privacyCheckPending = false
            if (VoiceController.get(this).sessionActive && privateInputVisible())
                AgentController.get(this).privateInputDetected()
        }, 150)
    }
    fun privateInputVisible(): Boolean {
        val root = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root ?: rootInActiveWindow ?: return false
        var count = 0
        fun privateNode(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (depth > 35 || ++count > 600) return true
            if (!node.isVisibleToUser) return false
            if (node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive)) return true
            val hint = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString().orEmpty() else ""
            if (SafetyPolicyEngine.isAuthentication("${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()} $hint")) return true
            return (0 until node.childCount).any { node.getChild(it)?.let { child -> privateNode(child, depth + 1) } == true }
        }
        return privateNode(root, 0)
    }
    // Android requests feedback interruption here; explicit Stop owns task cancellation.
    override fun onInterrupt() { VoiceController.get(this).stopPlayback() }
    override fun onDestroy() {
        privacyHandler.removeCallbacksAndMessages(null)
        onDisconnected?.invoke()
        hidePanel()
        ServiceConnection.service = null
        ServiceConnection.observation = null
        nodeHandles.clear()
        super.onDestroy()
    }

    fun observe(): ScreenObservation {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        nodeHandles.clear()
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val appWindow = appWindows.firstOrNull { it.isActive } ?: appWindows.firstOrNull { it.isFocused }
            ?: appWindows.maxByOrNull { it.layer }
        val root = appWindow?.root ?: rootInActiveWindow
        val packageName = root?.packageName?.toString().orEmpty()
        val elements = mutableListOf<ScreenElement>()
        var blocked: String? = null
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (blocked != null) return
            if (++visited > 600 || depth > 35 || elements.size >= 300) {
                blocked = "Screen exceeds safe observation limits. Continue manually."
                return
            }
            if (!node.isVisibleToUser) return
            if (node.isPassword || (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive)) {
                blocked = "Protected authentication information is on screen. Continue manually."
                return
            }
            val fullText = node.text?.toString().orEmpty()
            val fullDescription = node.contentDescription?.toString().orEmpty()
            val hint = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString().orEmpty() else ""
            val text = fullText.take(500)
            val description = fullDescription.take(500)
            // Authentication labels make the whole observation private.
            if (SafetyPolicyEngine.isAuthentication("$fullText $fullDescription $hint")) {
                blocked = "Protected authentication or payment screen. Continue manually."
                return
            }
            val supported = node.actionList.map { it.id }.toSet()
            val actions = buildList {
                if (node.isEnabled && AccessibilityNodeInfo.ACTION_CLICK in supported) add("click")
                if (node.isEnabled && node.isEditable && AccessibilityNodeInfo.ACTION_SET_TEXT in supported) add("type_text")
                if (node.isEnabled && (AccessibilityNodeInfo.ACTION_SCROLL_FORWARD in supported ||
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD in supported)) add("scroll")
            }
            if (text.isNotBlank() || description.isNotBlank() || actions.isNotEmpty()) {
                val id = "e${elements.size}"
                val bounds = Rect().also(node::getBoundsInScreen)
                elements += ScreenElement(id, text, description, node.className?.toString().orEmpty().take(120),
                    actions, listOf(bounds.left, bounds.top, bounds.right, bounds.bottom), node.isEditable)
                nodeHandles[id] = node
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, depth + 1) }
        }
        if (root == null) blocked = "No accessible screen. Unlock the phone and open an app."
        else if (packageName != this.packageName && packageName in approvedPackages) visit(root, 0)
        if (blocked != null) { elements.clear(); nodeHandles.clear() }
        val fingerprint = "$packageName|${root?.windowId}|$blocked|$elements"
        val id = if (fingerprint == lastFingerprint) snapshot?.id ?: UUID.randomUUID().toString()
                 else UUID.randomUUID().toString()
        lastFingerprint = fingerprint
        if (com.example.handsfree.BuildConfig.DEBUG)
            android.util.Log.d("HandsFreeAgent", "observe package=$packageName nodes=${elements.size} blocked=${blocked != null} id=$id")
        return ScreenObservation(id, packageName, elements, blocked).also {
            snapshot = it
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                ServiceConnection.observation = it
        }
    }

    fun execute(action: ProposedAction, approvedApps: Set<String>, confirmed: Boolean = false): ActionOutcome {
        val current = observe() // Always re-read immediately before execution.
        val decision = policy.evaluate(action, current, approvedApps)
        if (decision.disposition == Disposition.DENY || (decision.disposition == Disposition.CONFIRM && !confirmed))
            return ActionOutcome(false, decision.message.ifBlank { "Approval is required" })
        val node = nodeHandles[action.targetId]
        return try {
            val success = when (action.action) {
                "open_app" -> AppDiscoveryManager(this).open(action.arguments.packageName!!) == LaunchResult.REQUESTED
                "read_screen" -> true
                "click" -> node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                "type_text" -> node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.arguments.text)
                }) == true
                "scroll" -> node?.performAction(if (action.arguments.direction == "forward")
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
                "go_back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "open_play_store" -> {
                    val uri = Uri.parse("https://play.google.com/store/apps/details").buildUpon()
                        .appendQueryParameter("id", action.arguments.packageName).build()
                    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(Intent(intent).setPackage("com.android.vending")) }
                    catch (_: android.content.ActivityNotFoundException) { startActivity(intent) }
                    true
                }
                else -> return ActionOutcome(false, "Action belongs to the task controller")
            }
            ActionOutcome(success, if (success) "Android accepted ${action.action}; observing the result next."
                else "Android could not perform ${action.action}. Observe again.")
        } catch (_: SecurityException) { ActionOutcome(false, "Android blocked the action") }
          catch (_: android.content.ActivityNotFoundException) { ActionOutcome(false, "Application is unavailable") }
    }

    fun showPanel(message: String, approval: (() -> Unit)? = null, reject: (() -> Unit)? = null,
                  replyAction: (() -> Unit)? = null, choices: List<Pair<String, () -> Unit>> = emptyList(),
                  listen: (() -> Unit)? = null) {
        hidePanel()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 10, 18, 10)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(255, 242, 240)); cornerRadius = 28f
                setStroke(2, Color.rgb(221, 183, 187))
            }
        }
        val toolbar = LinearLayout(this)
        toolbar.addView(TextView(this).apply { text = "HandsFree"; setTextColor(Color.rgb(72, 38, 49)); textSize = 16f },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(Button(this).apply {
            text = this@AgentAccessibilityService.uiText(R.string.home); contentDescription = this@AgentAccessibilityService.uiText(R.string.home_description)
            setOnClickListener { AgentController.get(this@AgentAccessibilityService).newConversation(openHome = true) }
        })
        toolbar.addView(Button(this).apply {
            text = this@AgentAccessibilityService.uiText(R.string.stop); contentDescription = this@AgentAccessibilityService.uiText(R.string.stop_description)
            setOnClickListener { onStop?.invoke(); hidePanel() }
        })
        layout.addView(toolbar)
        statusText = TextView(this).apply { text = message; setTextColor(Color.rgb(72, 38, 49)); textSize = 15f; maxLines = 8 }
        layout.addView(statusText)
        val controls = LinearLayout(this)
        if (approval != null) controls.addView(Button(this).apply { text = this@AgentAccessibilityService.uiText(R.string.approve); setOnClickListener { approval() } })
        if (reject != null) controls.addView(Button(this).apply { text = this@AgentAccessibilityService.uiText(R.string.decline); setOnClickListener { reject() } })
        if (replyAction != null) controls.addView(Button(this).apply { text = this@AgentAccessibilityService.uiText(R.string.type_reply); setOnClickListener { replyAction() } })
        layout.addView(controls)
        choices.forEach { (label, choose) -> layout.addView(Button(this).apply { text = label; setOnClickListener { choose() } }) }
        if (listen != null) layout.addView(Button(this).apply { text = this@AgentAccessibilityService.uiText(R.string.listen_again); setOnClickListener { listen() } })
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * .85).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 90 }
        getSystemService(WindowManager::class.java).addView(layout, params)
        panel = layout
    }
    fun updatePanel(message: String) { statusText?.text = message }
    fun hidePanel() {
        panel?.let { getSystemService(WindowManager::class.java).removeView(it) }
        panel = null; statusText = null
    }
}
