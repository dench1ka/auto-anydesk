package ru.denis.anydeskconfirm.settings

import android.content.Context
import android.content.SharedPreferences
import ru.denis.anydeskconfirm.startup.StartupSettings
import ru.denis.anydeskconfirm.startup.StartupCoordinator

class SettingsRepository(context: Context) {
    val preferences: SharedPreferences = context.applicationContext.getSharedPreferences("anydesk_confirm", Context.MODE_PRIVATE)
    private val startup = StartupSettings(context)
    init { startup.sync(automationEnabled, screenAccessEnabled) }
    var automationEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMATION, false)
        set(value) {
            startup.sync(value, screenAccessEnabled)
            if (!value) StartupCoordinator.release()
            preferences.edit().putBoolean(KEY_AUTOMATION, value).apply()
        }
    var screenAccessEnabled: Boolean
        get() = preferences.getBoolean(KEY_SCREEN_ACCESS, false)
        set(value) {
            startup.sync(automationEnabled, value)
            if (!value) StartupCoordinator.release()
            preferences.edit().putBoolean(KEY_SCREEN_ACCESS, value).apply()
        }
    var useRemember: Boolean
        get() = preferences.getBoolean("remember", false)
        set(value) { preferences.edit().putBoolean("remember", value).apply() }
    companion object {
        const val KEY_AUTOMATION = "automation"
        const val KEY_SCREEN_ACCESS = "screen_access"
    }
}
