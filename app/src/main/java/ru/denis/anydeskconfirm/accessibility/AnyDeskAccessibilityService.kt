package ru.denis.anydeskconfirm.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import ru.denis.anydeskconfirm.diagnostics.DiagnosticLogger
import ru.denis.anydeskconfirm.settings.SettingsRepository

class AnyDeskAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val limiter = AttemptLimiter()
    private lateinit var settings: SettingsRepository
    private lateinit var logger: DiagnosticLogger
    private var pending = false
    private var listening = false
    private var lastRejection: String? = null
    private var lastRejectionTime = 0L
    private val check = Runnable { pending = false; inspect() }
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsRepository.KEY_AUTOMATION) {
            if (settings.automationEnabled) schedule(120) else cancel()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        stopListening()
        settings = SettingsRepository(this)
        logger = DiagnosticLogger(settings.preferences)
        settings.preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        listening = true
        ServiceState.connected = true
        logger.lifecycle("Служба подключена Android")
        schedule(120) // One initial check; no polling loop.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ServiceState.connected || !::settings.isInitialized || event == null || !settings.automationEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = event.packageName?.toString()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            pkg != DialogDetector.SYSTEM_UI && pkg != DialogDetector.ANYDESK) return
        schedule(120)
    }

    private fun schedule(delay: Long) {
        if (!ServiceState.connected || pending || !::settings.isInitialized || !settings.automationEnabled) return
        pending = true
        handler.postDelayed(check, delay)
    }

    private fun cancel() { handler.removeCallbacks(check); pending = false }

    private fun stopListening() {
        if (listening) settings.preferences.unregisterOnSharedPreferenceChangeListener(preferencesListener)
        listening = false
    }

    @Suppress("DEPRECATION")
    private fun inspect() {
        if (!ServiceState.connected || !settings.automationEnabled) return
        try {
            val root = rootInActiveWindow ?: return // Null/locked roots are never proof of disappearance.
            if (root.packageName?.toString() != DialogDetector.SYSTEM_UI) { root.recycle(); return }
            WindowCapture.read(root).use { capture ->
                val detection = DialogDetector.detect(capture.window, ServiceState.anyDeskInstalled(this))
                val match = detection.match
                if (match == null) {
                    detection.rejection?.let { reject(it) }
                    return
                }
                val now = SystemClock.elapsedRealtime()
                if (limiter.observe(match.windowId, now)) logger.detected()
                if (!limiter.canAttempt(match.windowId, now, settings.automationEnabled)) {
                    if (limiter.retryAllowed(match.windowId, now)) schedule(AttemptLimiter.RETRY_MS)
                    return
                }
                if (settings.useRemember && limiter.needsRemember(match.windowId)) {
                    limiter.markRemember(match.windowId)
                    val result = DialogConfirmer.remember(match, capture::click)
                    result.error?.let { logger.error(it) }
                    if (result.accepted) {
                        schedule(180) // Always re-read and revalidate after changing the checkbox.
                        return
                    }
                }
                val result = DialogConfirmer.confirm(match, settings.automationEnabled, capture::click)
                limiter.record(match.windowId, now, result.accepted)
                logger.attempt(result.accepted, result.error)
                if (limiter.retryAllowed(match.windowId, now)) schedule(AttemptLimiter.RETRY_MS)
            }
        } catch (_: RuntimeException) {
            reject("Не удалось получить актуальное системное окно через Accessibility")
        }
    }

    private fun reject(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (reason != lastRejection || now - lastRejectionTime >= 30_000) {
            logger.error(reason)
            lastRejection = reason
            lastRejectionTime = now
        }
    }

    override fun onInterrupt() {
        cancel()
        if (::logger.isInitialized) logger.lifecycle("Android прервал обратную связь службы")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ServiceState.connected = false
        cancel()
        stopListening()
        if (::logger.isInitialized) logger.lifecycle("Служба отключена Android")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceState.connected = false
        cancel()
        stopListening()
        if (::logger.isInitialized) logger.lifecycle("Процесс службы завершает работу")
        super.onDestroy()
    }
}
