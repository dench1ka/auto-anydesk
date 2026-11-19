package ru.denis.anydeskconfirm.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        // No credential-protected preferences, timers, network or foreground service here.
        StartupCoordinator.requestWake(context)
    }
}
