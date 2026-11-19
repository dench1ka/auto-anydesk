package ru.denis.anydeskconfirm.startup

import android.content.Context

/** Only opt-in flags and fixed diagnostic messages; available before credential storage unlock. */
class StartupSettings(context: Context) {
    private val preferences = context.applicationContext.createDeviceProtectedStorageContext()
        .getSharedPreferences("startup_access", Context.MODE_PRIVATE)
    val enabled: Boolean get() = preferences.getBoolean("automation", false) && preferences.getBoolean("screen_access", false)
    val lastMessage: String get() = preferences.getString("message", "Подготовка после загрузки ещё не выполнялась").orEmpty()
    fun sync(automation: Boolean, screenAccess: Boolean) {
        preferences.edit().putBoolean("automation", automation).putBoolean("screen_access", screenAccess).apply()
    }
    fun record(message: String) {
        if (lastMessage != message) preferences.edit().putString("message", message).apply()
    }
}
