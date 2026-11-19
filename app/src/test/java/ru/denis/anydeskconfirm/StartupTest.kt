package ru.denis.anydeskconfirm

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.view.WindowManager
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowKeyguardManager
import org.robolectric.util.ReflectionHelpers
import ru.denis.anydeskconfirm.accessibility.AnyDeskAccessibilityService
import ru.denis.anydeskconfirm.accessibility.DialogDetector
import ru.denis.anydeskconfirm.settings.SettingsRepository
import ru.denis.anydeskconfirm.startup.*
import java.time.Duration

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25])
class StartupTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository
    private lateinit var keyguard: KeyguardManager

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        StartupCoordinator.release()
        ReflectionHelpers.setStaticField(StartupCoordinator::class.java, "lastWake", -5_000L)
        context.getSharedPreferences("anydesk_confirm", Context.MODE_PRIVATE).edit().clear().commit()
        context.createDeviceProtectedStorageContext().getSharedPreferences("startup_access", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SettingsRepository(context)
        keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setIsKeyguardSecure(false)
        shadowOf(keyguard).setKeyguardLocked(true)
        shadowOf(context.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(false)
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsScreenOn(false)
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = DialogDetector.ANYDESK
            applicationInfo = ApplicationInfo().apply { packageName = DialogDetector.ANYDESK; enabled = true }
        })
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, AnyDeskAccessibilityService::class.java).flattenToString())
    }
    @After fun cleanup() { StartupCoordinator.release() }
    private fun enable() { repository.automationEnabled = true; repository.screenAccessEnabled = true }
    private fun nextActivity(): Intent? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity

    @Test fun modeIsOffByDefaultAndDoesNotWakeAtBoot() {
        assertFalse(repository.screenAccessEnabled)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertNull(nextActivity())
    }
    @Test fun enabledModeStillRequiresMainAutomation() {
        repository.screenAccessEnabled = true
        StartupCoordinator.requestWake(context)
        assertNull(nextActivity())
    }
    @Test fun pinOrSimLockIsNeverDismissed() {
        enable(); shadowOf(keyguard).setIsKeyguardSecure(true)
        assertFalse(StartupCoordinator.apply(context))
        StartupCoordinator.requestWake(context)
        assertNull(nextActivity())
        assertTrue(StartupSettings(context).lastMessage.contains("PIN"))
    }
    @Test fun manuallyDisabledAccessibilityPreventsBootUnlock() {
        enable()
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(nextActivity())
    }
    @Test fun missingAnyDeskPreventsPreparation() {
        enable()
        shadowOf(context.packageManager).removePackage(DialogDetector.ANYDESK)
        assertFalse(StartupCoordinator.apply(context))
        assertNull(nextActivity())
    }
    @Test fun publicApiDisablesOnlySwipeAndReleaseRestoresIt() {
        enable()
        assertTrue(StartupCoordinator.apply(context))
        val lock = ReflectionHelpers.getStaticField<KeyguardManager.KeyguardLock>(StartupCoordinator::class.java, "keyguardLock")
        val shadow = Shadow.extract<ShadowKeyguardManager.ShadowKeyguardLock>(lock)
        assertFalse(shadow.isEnabled)
        StartupCoordinator.release()
        assertTrue(shadow.isEnabled)
    }
    @Test fun lockedBootUsesDeviceStorageWithoutUserUnlock() {
        enable()
        assertTrue(StartupSettings(context.createDeviceProtectedStorageContext()).enabled)
        BootReceiver().onReceive(context.createDeviceProtectedStorageContext(), Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(WakeActivity::class.java.name, nextActivity()?.component?.className)
    }
    @Test fun duplicateBootEventsAreCoalesced() {
        enable()
        val receiver = BootReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull(nextActivity()); assertNull(nextActivity())
    }
    @Test fun alreadyInteractiveUnlockedPhoneDoesNotOpenPreparationWindow() {
        enable()
        shadowOf(keyguard).setKeyguardLocked(false)
        shadowOf(context.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsScreenOn(true)
        StartupCoordinator.requestWake(context)
        assertNull(nextActivity())
    }
    @Test fun unrelatedBroadcastDoesNotStartAnything() {
        enable(); BootReceiver().onReceive(context, Intent("arbitrary.action"))
        assertNull(nextActivity())
    }
    @Test fun preparationWindowHasBoundedLifetimeAndDoesNotKeepScreenOn() {
        enable()
        Robolectric.buildActivity(WakeActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON != 0)
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD != 0)
            assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
            assertTrue(activity.isFinishing)
        }
    }
    @Test fun disablingModePreventsFurtherWakeRequests() {
        enable(); assertTrue(StartupCoordinator.apply(context))
        val lock = ReflectionHelpers.getStaticField<KeyguardManager.KeyguardLock>(StartupCoordinator::class.java, "keyguardLock")
        repository.screenAccessEnabled = false
        assertTrue(Shadow.extract<ShadowKeyguardManager.ShadowKeyguardLock>(lock).isEnabled)
        assertFalse(StartupCoordinator.apply(context))
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(nextActivity())
    }
    @Test @Config(sdk = [35]) fun unsupportedAndroidVersionDoesNotManipulateKeyguard() {
        enable(); assertFalse(StartupCoordinator.apply(context))
        StartupCoordinator.requestWake(context)
        assertNull(nextActivity())
    }
}
