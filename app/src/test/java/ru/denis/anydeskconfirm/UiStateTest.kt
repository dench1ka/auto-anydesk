package ru.denis.anydeskconfirm

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import ru.denis.anydeskconfirm.accessibility.AnyDeskAccessibilityService
import ru.denis.anydeskconfirm.accessibility.DialogDetector
import ru.denis.anydeskconfirm.accessibility.ServiceState
import ru.denis.anydeskconfirm.settings.SettingsRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 35])
class UiStateTest {
    @Before fun prepare() {
        SettingsRepository(RuntimeEnvironment.getApplication()).preferences.edit().clear().commit()
        setConnected(false)
    }
    @After fun cleanup() { setConnected(false) }

    private fun setConnected(value: Boolean) { ReflectionHelpers.setStaticField(ServiceState::class.java, "connected", value) }

    private fun enableService() {
        val context = RuntimeEnvironment.getApplication()
        val resolve = ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply { packageName = context.packageName; name = AnyDeskAccessibilityService::class.java.name }
        }
        val info = AccessibilityServiceInfo()
        ReflectionHelpers.setField(info, "mResolveInfo", resolve)
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabledAccessibilityServiceList(listOf(info))
    }

    @Test fun allowedButDisconnectedExplainsRequiredSetup() {
        enableService()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertTrue(controller.get().findViewById<TextView>(R.id.status).text.contains("Требуется настройка"))
            assertTrue(controller.get().findViewById<TextView>(R.id.status_help).text.contains("не подключена"))
        }
    }
    @Test fun missingAnyDeskExplainsPackageRequirement() {
        enableService(); setConnected(true)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertTrue(controller.get().findViewById<TextView>(R.id.status_help).text.contains(DialogDetector.ANYDESK))
        }
    }
    @Test fun connectedServiceAndInstalledAnyDeskShowActiveState() {
        enableService(); setConnected(true)
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = DialogDetector.ANYDESK
            applicationInfo = ApplicationInfo().apply { packageName = DialogDetector.ANYDESK; enabled = true }
        })
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertTrue(activity.findViewById<TextView>(R.id.status).text.contains("Активна"))
            activity.findViewById<View>(R.id.auto_confirm).performClick()
            assertTrue(activity.findViewById<TextView>(R.id.status_help).text.contains("включена"))
        }
    }
    @Test fun incompleteOtherServiceEntryDoesNotCrashTheScreen() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            assertTrue(controller.get().findViewById<TextView>(R.id.status).text.isNotEmpty())
        }
    }
}
