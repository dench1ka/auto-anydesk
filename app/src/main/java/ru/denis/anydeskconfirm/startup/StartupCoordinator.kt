package ru.denis.anydeskconfirm.startup

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import ru.denis.anydeskconfirm.accessibility.AnyDeskAccessibilityService
import ru.denis.anydeskconfirm.accessibility.ServiceState

/** Public Android 7 APIs only. A KeyguardLock is not a CPU/screen WakeLock. */
@Suppress("DEPRECATION")
object StartupCoordinator {
    private var keyguardLock: KeyguardManager.KeyguardLock? = null
    private var lastWake = -5_000L

    fun blockedReason(context: Context): String? {
        if (!StartupSettings(context).enabled) return "Режим без присмотра выключен"
        if (Build.VERSION.SDK_INT != 25) return "Подготовка экрана поддерживается только на Android 7.1 (API 25)"
        return try {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (keyguard.isKeyguardSecure) return "Установлен PIN, пароль, графический ключ или PIN SIM: автоматическая разблокировка недоступна"
            val expected = ComponentName(context, AnyDeskAccessibilityService::class.java)
            val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == expected }
            when {
                !enabled -> "Для режима без присмотра нужно вручную включить службу Accessibility"
                !ServiceState.anyDeskInstalled(context) -> "Пакет AnyDesk не установлен или отключён"
                else -> null
            }
        } catch (_: RuntimeException) { "Android не предоставил состояние блокировки; разблокировка отменена" }
    }

    fun apply(context: Context): Boolean {
        val reason = blockedReason(context)
        if (reason != null) {
            release()
            if (StartupSettings(context).enabled) StartupSettings(context).record(reason)
            return false
        }
        return try {
            if (keyguardLock == null) {
                val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val lock = manager.newKeyguardLock("AnyDeskAutoConfirm.Swipe")
                keyguardLock = lock
                lock.disableKeyguard()
                StartupSettings(context).record("Android получил команду отключить блокировку свайпом; нужна проверка на телефоне")
            }
            true
        } catch (_: RuntimeException) {
            release()
            StartupSettings(context).record("Android отклонил отключение блокировки свайпом")
            false
        }
    }

    fun requestWake(context: Context) {
        if (!apply(context)) return
        try {
            val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val user = context.getSystemService(Context.USER_SERVICE) as UserManager
            if (power.isInteractive && !manager.isKeyguardLocked && user.isUserUnlocked) return
            val now = SystemClock.elapsedRealtime()
            if (now >= lastWake && now - lastWake < 5_000) return
            lastWake = now
            context.startActivity(Intent(context, WakeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            StartupSettings(context).record("Запрошена краткая подготовка экрана; результат соединения не проверен")
        } catch (_: RuntimeException) {
            StartupSettings(context).record("MIUI/Android не разрешил открыть экран подготовки; проверьте автозапуск и фоновые окна")
        }
    }

    fun release() {
        try { keyguardLock?.reenableKeyguard() } catch (_: RuntimeException) { /* No privileged fallback. */ }
        keyguardLock = null
    }
}
