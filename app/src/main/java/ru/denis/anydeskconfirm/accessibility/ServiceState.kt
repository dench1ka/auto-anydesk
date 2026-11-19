package ru.denis.anydeskconfirm.accessibility

import android.content.Context
import android.content.pm.PackageManager

object ServiceState {
    @Volatile var connected = false
        internal set

    fun anyDeskInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(DialogDetector.ANYDESK, 0)
        info.enabled
    } catch (_: PackageManager.NameNotFoundException) { false }
    catch (_: RuntimeException) { false }
}
