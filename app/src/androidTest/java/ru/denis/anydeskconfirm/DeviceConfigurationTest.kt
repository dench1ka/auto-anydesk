package ru.denis.anydeskconfirm

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.denis.anydeskconfirm.accessibility.AnyDeskAccessibilityService
import ru.denis.anydeskconfirm.settings.SettingsRepository

@RunWith(AndroidJUnit4::class)
class DeviceConfigurationTest {
    @Test fun protectedServiceAndNoRuntimePermissions() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION") val info = ctx.packageManager.getServiceInfo(ComponentName(ctx, AnyDeskAccessibilityService::class.java), PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, info.permission)
        assertTrue(info.metaData.getInt("android.accessibilityservice") != 0)
        @Suppress("DEPRECATION") val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(pkg.requestedPermissions.isNullOrEmpty())
    }
    @Test fun preferencesPersistOnDevice() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = SettingsRepository(ctx)
        val oldAuto = repo.automationEnabled
        val oldRemember = repo.useRemember
        try {
            repo.automationEnabled = true; repo.useRemember = true
            // Commit flushes pending apply() writes; production uses nonblocking apply().
            assertTrue(repo.preferences.edit().commit())
            assertTrue(SettingsRepository(ctx).automationEnabled)
            assertTrue(SettingsRepository(ctx).useRemember)
        } finally { repo.automationEnabled = oldAuto; repo.useRemember = oldRemember }
    }
}
