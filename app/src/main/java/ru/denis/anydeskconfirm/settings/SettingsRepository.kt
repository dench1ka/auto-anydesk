package ru.denis.anydeskconfirm.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    val preferences: SharedPreferences = context.applicationContext.getSharedPreferences("anydesk_confirm", Context.MODE_PRIVATE)
    var automationEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMATION, false)
        set(value) { preferences.edit().putBoolean(KEY_AUTOMATION, value).apply() }
    var useRemember: Boolean
        get() = preferences.getBoolean("remember", false)
        set(value) { preferences.edit().putBoolean("remember", value).apply() }
    companion object { const val KEY_AUTOMATION = "automation" }
}
