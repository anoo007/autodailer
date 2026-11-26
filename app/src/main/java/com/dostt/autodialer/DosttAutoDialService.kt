package com.dostt.autodialer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * AccessibilityService strictly scoped to live.friend.dostt.
 *
 * Guarantees:
 * - Never runs in the background when switched OFF, when leaving Dostt, or when screen turns off.
 * - Immediately stops dialing and clears all timers once a call connects (via AudioManager mode,
 *   in-call UI indicators, or leaving the matching screen).
 * - Optional full OS-level shutoff via disableSelf() on call connect or when banking.
 */
class DosttAutoDialService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var config: DialerConfig
    private lateinit var session: DialerSessionState

    private var windowManager: WindowManager? = null
    private var audioManager: AudioManager? = null
    private var overlayView: LinearLayout? = null
    private var statusText: TextView? = null
    private var toggleButton: Button? = null

    private var lastActionTimeMs: Long = 0L
    private var pendingCheckRunnable: Runnable? = null
    private var pendingConnectConfirmRunnable: Runnable? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                stopDialing("Off (Screen locked)")
                hideOverlay()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        config = DialerConfig(this)
        // Always start disarmed on fresh bind so nothing dials unexpectedly.
        config.isArmed = false
        session = DialerSessionState(config.maxAttempts)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!config.isMasterEnabled) {
            stopDialing("Switched OFF")
            hideOverlay()
            return
        }

        val activePkg = rootInActiveWindow?.packageName?.toString()
            ?: event?.packageName?.toString()

        if (activePkg != DialerConfig.DOSTT_PACKAGE) {
            // Left Dostt (e.g. switched to home screen or banking app): stop dialing immediately.
            stopDialing("Paused (Left Dostt)")
            hideOverlay()
            return
        }

        if (isAudioCallActive()) {
            onCallConnectedDetected()
            return
        }

        ensureOverlayVisible()
        if (!config.isArmed) {
            updateOverlayUi("OFF (Ready)", isDialing = false)
            return
        }

        scheduleEvaluation(delayMs = 250L)
    }

    override fun onInterrupt() {
        stopDialing("Interrupted")
        hideOverlay()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {
        }
        stopDialing("Destroyed")
        hideOverlay()
        instance = null
        super.onDestroy()
    }

    fun startFromApp() {
        if (!config.isMasterEnabled) {
            config.isMasterEnabled = true
        }
        config.isArmed = true
        session = DialerSessionState(config.maxAttempts)
        updateOverlayUi("ON • 0/${config.maxAttempts}", isDialing = true)
        scheduleEvaluation(delayMs = 400L)
    }

    fun stopDialing(reason: String = "Switched OFF") {
        config.isArmed = false
        cancelPendingWork()
        session.reset()
        updateOverlayUi(reason, isDialing = false)
    }

    fun setMasterEnabled(enabled: Boolean) {
        config.isMasterEnabled = enabled
        if (!enabled) {
            stopDialing("Switched OFF")
            hideOverlay()
        }
    }

    /** Completely disables and unbinds this AccessibilityService from Android OS. */
    fun disableServiceCompletely() {
        stopDialing("Service Shut Off")
        config.isMasterEnabled = false
        hideOverlay()
        disableSelf()
    }

    private fun isAudioCallActive(): Boolean {
        val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        return mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_IN_CALL
    }

    private fun onCallConnectedDetected() {
        stopDialing("Connected • Stopped")
        if (config.disableServiceOnConnect) {
            disableServiceCompletely()
        }
    }

    private fun scheduleEvaluation(delayMs: Long) {
        pendingCheckRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { evaluateCurrentScreen() }
        pendingCheckRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun evaluateCurrentScreen() {
        if (!config.isMasterEnabled || !config.isArmed) {
            cancelPendingWork()
            return
        }
        if (isAudioCallActive()) {
            onCallConnectedDetected()
            return
        }

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != DialerConfig.DOSTT_PACKAGE) {
            stopDialing("Paused (Left Dostt)")
            hideOverlay()
            return
        }

        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        val screen = DosttScreenDetector.classifyScreen(allTexts, isAudioCallActive = false)

        if (screen != DosttScreen.CALL_OR_OTHER) {
            cancelConnectConfirmation()
        }

        val now = System.currentTimeMillis()
        val minCooldown = if (screen == DosttScreen.UNAVAILABLE) 800L else config.retryDelayMs

        when (screen) {
            DosttScreen.CALL_CONNECTED -> {
                onCallConnectedDetected()
            }

            DosttScreen.MATCHING -> {
                session.onScreenObserved(screen)
                updateOverlayUi(
                    "Matching (${session.attempts}/${config.maxAttempts})",
                    isDialing = true
                )
            }

            DosttScreen.UNAVAILABLE -> {
                if (now - lastActionTimeMs < minCooldown) {
                    scheduleEvaluation(minCooldown - (now - lastActionTimeMs))
                    return
                }
                session.onScreenObserved(screen)
                lastActionTimeMs = now
                updateOverlayUi(
                    "Go Home (${session.attempts}/${config.maxAttempts})",
                    isDialing = true
                )
                clickGoBackHome(root)
                scheduleEvaluation(config.retryDelayMs)
            }

            DosttScreen.HOME_COLLAPSED, DosttScreen.HOME_EXPANDED -> {
                if (now - lastActionTimeMs < config.retryDelayMs) {
                    scheduleEvaluation(config.retryDelayMs - (now - lastActionTimeMs))
                    return
                }
                val action = session.onScreenObserved(screen)
                if (action == DialerAction.STOP_MAX_ATTEMPTS_REACHED) {
                    stopDialing("Stopped: Max ${config.maxAttempts} tries")
                    return
                }
                lastActionTimeMs = now
                updateOverlayUi(
                    "Dialing (${session.attempts}/${config.maxAttempts})",
                    isDialing = true
                )
                if (action == DialerAction.TAP_AUDIO_COLLAPSED) {
                    clickAudioOnCollapsedBar(root)
                } else {
                    clickAudioOnExpandedFab(root)
                }
                scheduleEvaluation(1200L)
            }

            DosttScreen.CALL_OR_OTHER -> {
                if (session.sawMatchingScreen && pendingConnectConfirmRunnable == null) {
                    val confirm = Runnable {
                        pendingConnectConfirmRunnable = null
                        if (!config.isArmed) return@Runnable
                        if (isAudioCallActive()) {
                            onCallConnectedDetected()
                            return@Runnable
                        }
                        val currentRoot = rootInActiveWindow ?: return@Runnable
                        if (currentRoot.packageName?.toString() != DialerConfig.DOSTT_PACKAGE) {
                            stopDialing("Paused (Left Dostt)")
                            hideOverlay()
                            return@Runnable
                        }
                        val texts = mutableListOf<String>()
                        collectTexts(currentRoot, texts)
                        val confirmedScreen = DosttScreenDetector.classifyScreen(texts, isAudioCallActive())
                        if (confirmedScreen == DosttScreen.CALL_CONNECTED || confirmedScreen == DosttScreen.CALL_OR_OTHER) {
                            onCallConnectedDetected()
                        }
                    }
                    pendingConnectConfirmRunnable = confirm
                    handler.postDelayed(confirm, 1500L)
                }
            }
        }
    }

    private fun clickGoBackHome(root: AccessibilityNodeInfo) {
        if (!config.isArmed || isAudioCallActive()) return
        val node = findNodeByText(root, "go back home")
        if (node != null && clickNodeOrParent(node)) {
            return
        }
        if (config.useCoordinateFallback) {
            tapScreenPercent(config.goHomeXPercent, config.goHomeYPercent)
        }
    }

    private fun clickAudioOnCollapsedBar(root: AccessibilityNodeInfo) {
        if (!config.isArmed || isAudioCallActive()) return
        val anchor = findNodeByText(root, "find me the right dostt")
        if (anchor != null) {
            val rowParent = anchor.parent
            if (rowParent != null) {
                val clickables = mutableListOf<Pair<Int, AccessibilityNodeInfo>>()
                for (i in 0 until rowParent.childCount) {
                    val child = rowParent.getChild(i) ?: continue
                    if (child.isClickable && child != anchor) {
                        val rect = Rect()
                        child.getBoundsInScreen(rect)
                        if (rect.width() in 40..240 && rect.height() in 40..240) {
                            clickables.add(rect.left to child)
                        }
                    }
                }
                val audioCandidate = clickables.minByOrNull { it.first }?.second
                if (audioCandidate != null && audioCandidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return
                }
            }
        }
        if (config.useCoordinateFallback) {
            tapScreenPercent(config.collapsedAudioXPercent, config.collapsedAudioYPercent)
        }
    }

    private fun clickAudioOnExpandedFab(root: AccessibilityNodeInfo) {
        if (!config.isArmed || isAudioCallActive()) return
        val audioNode = findNodeByText(root, "audio")
        if (audioNode != null) {
            if (clickNodeOrParent(audioNode)) return
            val parent = audioNode.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i) ?: continue
                    if (sibling.isClickable && sibling.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return
                    }
                }
            }
        }
        if (config.useCoordinateFallback) {
            tapScreenPercent(config.expandedAudioXPercent, config.expandedAudioYPercent)
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTexts(child, out)
            }
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, queryLower: String): AccessibilityNodeInfo? {
        val t = node.text?.toString()?.lowercase().orEmpty()
        val d = node.contentDescription?.toString()?.lowercase().orEmpty()
        if (t.contains(queryLower) || d.contains(queryLower)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNodeByText(child, queryLower)
            if (match != null) return match
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun tapScreenPercent(xPercent: Float, yPercent: Float) {
        if (!config.isArmed || isAudioCallActive()) return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return

        val x = (metrics.widthPixels * (xPercent / 100f)).coerceIn(1f, metrics.widthPixels - 1f)
        val y = (metrics.heightPixels * (yPercent / 100f)).coerceIn(1f, metrics.heightPixels - 1f)

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun ensureOverlayVisible() {
        if (!config.isMasterEnabled) return
        if (overlayView != null) {
            overlayView?.visibility = View.VISIBLE
            return
        }
        val wm = windowManager ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 14, 24, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = 48f
                setStroke(2, Color.parseColor("#6366F1"))
            }
        }

        val status = TextView(this).apply {
            text = "OFF (Ready)"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 0, 18, 0)
        }

        val toggle = Button(this).apply {
            text = if (config.isArmed) "Switch OFF" else "Switch ON"
            setTextColor(Color.WHITE)
            textSize = 11f
            isAllCaps = false
            setPadding(24, 8, 24, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (config.isArmed) "#EF4444" else "#10B981"))
                cornerRadius = 32f
            }
            setOnClickListener {
                if (config.isArmed) {
                    stopDialing("Switched OFF")
                } else {
                    startFromApp()
                }
            }
        }

        val settingsBtn = Button(this).apply {
            text = "⚙"
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(16, 8, 16, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 32f
            }
            setOnClickListener {
                val intent = Intent(this@DosttAutoDialService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

        container.addView(status)
        container.addView(toggle)
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(10, 1)
        }
        container.addView(spacer)
        container.addView(settingsBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 140
        }

        wm.addView(container, params)
        overlayView = container
        statusText = status
        toggleButton = toggle
    }

    private fun updateOverlayUi(statusMsg: String, isDialing: Boolean) {
        handler.post {
            statusText?.text = statusMsg
            toggleButton?.apply {
                text = if (isDialing) "Switch OFF" else "Switch ON"
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isDialing) "#EF4444" else "#10B981"))
                    cornerRadius = 32f
                }
            }
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        overlayView = null
        statusText = null
        toggleButton = null
    }

    private fun cancelConnectConfirmation() {
        pendingConnectConfirmRunnable?.let { handler.removeCallbacks(it) }
        pendingConnectConfirmRunnable = null
    }

    private fun cancelPendingWork() {
        pendingCheckRunnable?.let { handler.removeCallbacks(it) }
        pendingCheckRunnable = null
        cancelConnectConfirmation()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        @Volatile
        var instance: DosttAutoDialService? = null
            private set
    }
}
