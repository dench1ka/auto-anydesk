package ru.denis.anydeskconfirm.diagnostics

import android.content.SharedPreferences

/** Fixed local fields only; no event text, tree dumps, exception messages or logcat. */
class DiagnosticLogger(private val preferences: SharedPreferences) {
    fun detected() { preferences.edit().putLong("last_detection", System.currentTimeMillis()).apply() }
    fun attempt(result: Boolean, error: String?) {
        val editor = preferences.edit().putLong("last_attempt", System.currentTimeMillis())
            .putString("last_result", if (result) "ACTION_CLICK принят Android; соединение не проверено" else error ?: "Нажатие не принято")
        if (result) editor.putLong("successful_clicks", preferences.getLong("successful_clicks", 0) + 1)
        if (error != null) editor.putString("last_error", error)
        editor.apply()
    }
    fun error(code: String) { preferences.edit().putString("last_error", code).apply() }
    fun lifecycle(value: String) { preferences.edit().putString("lifecycle", value).apply() }
}
