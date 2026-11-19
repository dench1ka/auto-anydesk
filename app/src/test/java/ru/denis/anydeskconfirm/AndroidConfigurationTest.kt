package ru.denis.anydeskconfirm

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.denis.anydeskconfirm.accessibility.AnyDeskAccessibilityService
import ru.denis.anydeskconfirm.diagnostics.DiagnosticLogger
import ru.denis.anydeskconfirm.settings.SettingsRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 35])
class AndroidConfigurationTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        SettingsRepository(context).preferences.edit().clear().commit()
    }
    @Test fun settingsAndDiagnosticsSurviveRepositoryRecreation() {
        val settings = SettingsRepository(context)
        assertFalse(settings.automationEnabled); assertFalse(settings.useRemember); assertFalse(settings.screenAccessEnabled)
        settings.automationEnabled = true; settings.useRemember = true; settings.screenAccessEnabled = true
        DiagnosticLogger(settings.preferences).apply { detected(); attempt(true, null); attempt(false, "Ошибка теста") }
        val reopened = SettingsRepository(context)
        assertTrue(reopened.automationEnabled); assertTrue(reopened.useRemember); assertTrue(reopened.screenAccessEnabled)
        assertEquals(1L, reopened.preferences.getLong("successful_clicks", 0))
        assertTrue(reopened.preferences.getLong("last_detection", 0) > 0)
        assertEquals("Ошибка теста", reopened.preferences.getString("last_error", null))
    }
    @Test fun manifestHasProtectedServiceAndOnlyTwoNormalPermissions() {
        val pm = context.packageManager
        @Suppress("DEPRECATION") val app = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertEquals(setOf(Manifest.permission.RECEIVE_BOOT_COMPLETED, Manifest.permission.DISABLE_KEYGUARD), app.requestedPermissions.orEmpty().toSet())
        @Suppress("DEPRECATION") val service = pm.getServiceInfo(ComponentName(context, AnyDeskAccessibilityService::class.java), PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, service.permission)
        assertTrue(service.exported)
        assertTrue(service.metaData.getInt("android.accessibilityservice") != 0)
    }
    @Test fun mainActivityInflatesAndShowsSafeDefaults() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertFalse(controller.get().findViewById<android.widget.Switch>(R.id.auto_confirm).isChecked)
            assertTrue(controller.get().findViewById<android.widget.TextView>(R.id.status).text.contains("Не включена"))
        }
    }
    @Test fun diagnosticAndMiuiInstructionsPreserveLineBreaks() {
        val diagnostic = context.getString(R.string.diagnostics_format, "A", "B", "C", 1, "D", "E")
        assertTrue(diagnostic.contains("\n\n"))
        assertTrue(context.getString(R.string.miui_help).contains("\n2."))
        assertTrue(context.getString(R.string.remember).contains('"'))
    }
}
