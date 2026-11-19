package ru.denis.anydeskconfirm

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import ru.denis.anydeskconfirm.accessibility.AnyDeskAccessibilityService
import ru.denis.anydeskconfirm.accessibility.ServiceState
import ru.denis.anydeskconfirm.settings.SettingsRepository
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var repository: SettingsRepository
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repository = SettingsRepository(this)
        findViewById<Switch>(R.id.auto_confirm).apply {
            isChecked = repository.automationEnabled
            setOnCheckedChangeListener { _, checked -> repository.automationEnabled = checked; updateStatus() }
        }
        findViewById<CheckBox>(R.id.remember).apply {
            isChecked = repository.useRemember
            setOnCheckedChangeListener { _, checked -> repository.useRemember = checked }
        }
        findViewById<Button>(R.id.open_accessibility).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: RuntimeException) { Toast.makeText(this, R.string.settings_error, Toast.LENGTH_LONG).show() }
        }
        findViewById<Button>(R.id.refresh).setOnClickListener { updateStatus() }
    }

    override fun onResume() {
        super.onResume()
        repository.preferences.registerOnSharedPreferenceChangeListener(listener)
        updateStatus()
    }

    override fun onPause() {
        repository.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun updateStatus() {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, AnyDeskAccessibilityService::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info = it.resolveInfo.serviceInfo
            ComponentName(info.packageName, info.name) == expected
        }
        val automation = getString(if (repository.automationEnabled) R.string.enabled_word else R.string.disabled_word)
        val (status, help) = when {
            !enabled -> R.string.status_off to getString(R.string.status_disabled_help, automation)
            !ServiceState.connected -> R.string.status_setup to getString(R.string.status_disconnected_help)
            !ServiceState.anyDeskInstalled(this) -> R.string.status_setup to getString(R.string.status_missing_help)
            else -> R.string.status_active to getString(R.string.status_ready_help, automation)
        }
        findViewById<TextView>(R.id.status).text = getString(R.string.status_format, getString(status))
        findViewById<TextView>(R.id.status_help).text = help
        val prefs = repository.preferences
        fun time(key: String): String = prefs.getLong(key, 0).let {
            if (it == 0L) getString(R.string.never) else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it))
        }
        findViewById<TextView>(R.id.diagnostic_text).text = getString(R.string.diagnostics_format,
            time("last_detection"), time("last_attempt"), prefs.getString("last_result", getString(R.string.never)),
            prefs.getLong("successful_clicks", 0), prefs.getString("last_error", getString(R.string.never)),
            prefs.getString("lifecycle", getString(R.string.never)))
    }
}
