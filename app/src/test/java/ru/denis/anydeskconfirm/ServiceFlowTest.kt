package ru.denis.anydeskconfirm

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.util.ReflectionHelpers
import ru.denis.anydeskconfirm.accessibility.*
import ru.denis.anydeskconfirm.settings.SettingsRepository
import java.time.Duration

@Implements(AccessibilityService::class)
class ActiveRootShadow : ShadowAccessibilityService() {
    companion object {
        var factory: (() -> AccessibilityNodeInfo?)? = null
        var reads = 0
    }
    @Implementation fun getRootInActiveWindow(): AccessibilityNodeInfo? {
        reads++
        return factory?.invoke()
    }
}

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25], shadows = [ActiveRootShadow::class])
class ServiceFlowTest {
    private lateinit var controller: ServiceController<AnyDeskAccessibilityService>
    private lateinit var service: AnyDeskAccessibilityService
    private lateinit var settings: SettingsRepository
    private var confirmationCalls = 0
    private var rememberCalls = 0
    private var acceptClick = true
    private var throwOnClick = false
    private var checked = false
    private var currentWindow = 44
    private var message = "Приложение AnyDesk получит доступ к изображению на экране устройства."

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        settings = SettingsRepository(context)
        settings.preferences.edit().clear().commit()
        val info = PackageInfo().apply {
            packageName = DialogDetector.ANYDESK
            applicationInfo = ApplicationInfo().apply { packageName = DialogDetector.ANYDESK; enabled = true }
        }
        shadowOf(context.packageManager).installPackage(info)
        ActiveRootShadow.reads = 0
        ActiveRootShadow.factory = { dialog() }
        controller = Robolectric.buildService(AnyDeskAccessibilityService::class.java).create()
        service = controller.get()
    }

    private fun connect(enabled: Boolean = true) {
        settings.automationEnabled = enabled
        ReflectionHelpers.callInstanceMethod<Void>(service, "onServiceConnected")
    }

    private fun advance(milliseconds: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds)) }

    private fun event() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.packageName = DialogDetector.SYSTEM_UI
        service.onAccessibilityEvent(event)
        event.recycle()
    }

    private fun dialog(): AccessibilityNodeInfo {
        val window = AccessibilityWindowInfo.obtain()
        shadowOf(window).setId(currentWindow)
        fun node(text: String = "", id: String = "", clazz: String = "android.widget.TextView") = AccessibilityNodeInfo.obtain().apply {
            packageName = DialogDetector.SYSTEM_UI
            this.text = text
            viewIdResourceName = id
            className = clazz
            isVisibleToUser = true; isEnabled = true
            shadowOf(this).setAccessibilityWindowInfo(window)
        }
        val root = node()
        val text = node(message)
        val button = node("НАЧАТЬ", DialogDetector.BUTTON_ID, "android.widget.Button").apply { isClickable = true }
        val remember = node("Больше не показывать", DialogDetector.REMEMBER_ID, "android.widget.CheckBox").apply {
            isClickable = true; isCheckable = true; isChecked = checked
        }
        shadowOf(button).setOnPerformActionListener { action, _ ->
            assertEquals(AccessibilityNodeInfo.ACTION_CLICK, action)
            confirmationCalls++
            if (throwOnClick) throw IllegalStateException("not logged")
            acceptClick
        }
        shadowOf(remember).setOnPerformActionListener { _, _ -> rememberCalls++; checked = true; true }
        shadowOf(root).addChild(text); shadowOf(root).addChild(button); shadowOf(root).addChild(remember)
        return root
    }

    @After fun cleanup() {
        controller.destroy()
        ActiveRootShadow.factory = null
    }

    @Test fun disabledServiceNeverReadsWindowOrClicks() {
        connect(false)
        repeat(100) { event() }
        advance(10_000)
        assertEquals(0, ActiveRootShadow.reads); assertEquals(0, confirmationCalls)
    }

    @Test fun eventStormProducesOneClickForTheWindow() {
        connect()
        repeat(1000) { event() }
        advance(120)
        assertEquals(1, ActiveRootShadow.reads)
        assertEquals(1, confirmationCalls)
        repeat(100) { event() }
        advance(60_000)
        assertEquals(1, confirmationCalls)
        assertEquals(1L, settings.preferences.getLong("successful_clicks", 0))
    }

    @Test fun failedActionHasThreeAttemptsAndStops() {
        acceptClick = false
        connect(); advance(60_000)
        assertEquals(3, confirmationCalls)
        assertEquals(3, ActiveRootShadow.reads)
        assertEquals(0L, settings.preferences.getLong("successful_clicks", 0))
        repeat(50) { event() }; advance(10_000)
        assertEquals(3, confirmationCalls)
    }

    @Test fun apiErrorsAreCaughtAndBounded() {
        throwOnClick = true
        connect(); advance(60_000)
        assertEquals(3, confirmationCalls)
        assertFalse(settings.preferences.getString("last_error", "")!!.contains("not logged"))
    }

    @Test fun changedDialogIsRevalidatedBeforeRetry() {
        acceptClick = false
        connect(); advance(120)
        message = "Начать обновление AnyDesk?"
        advance(10_000)
        assertEquals(1, confirmationCalls)
    }

    @Test fun checkboxIsFollowedByFreshCaptureBeforeConfirmation() {
        settings.useRemember = true
        connect(); advance(120)
        assertEquals(1, rememberCalls); assertEquals(0, confirmationCalls)
        advance(180)
        assertEquals(1, rememberCalls); assertEquals(1, confirmationCalls)
        assertEquals(2, ActiveRootShadow.reads)
    }

    @Test fun disablingAutomationCancelsPendingRetry() {
        acceptClick = false
        connect(); advance(120)
        settings.automationEnabled = false
        advance(60_000)
        assertEquals(1, confirmationCalls); assertEquals(1, ActiveRootShadow.reads)
    }

    @Test fun disconnectedServiceDoesNotReactToPreferenceChanges() {
        connect()
        service.onUnbind(Intent())
        settings.automationEnabled = false; settings.automationEnabled = true
        repeat(10) { event() }; advance(60_000)
        assertFalse(ServiceState.connected)
        assertEquals(0, ActiveRootShadow.reads)
    }

    @Test fun missingRootDoesNotClickOrStartPolling() {
        ActiveRootShadow.factory = { null }
        connect(); advance(60_000)
        assertEquals(1, ActiveRootShadow.reads); assertEquals(0, confirmationCalls)
    }

    @Test fun newWindowCanBeConfirmedWithoutReopeningActivity() {
        connect(); advance(120)
        currentWindow++
        event(); advance(1000)
        assertEquals(2, confirmationCalls)
        assertEquals(2L, settings.preferences.getLong("successful_clicks", 0))
    }
}
