package ru.denis.anydeskconfirm

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
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
        configureWindowInsets()
        repository = SettingsRepository(this)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        findViewById<TextView>(R.id.app_version).text = getString(R.string.version_format, version)
        adaptSettingRow(R.id.automation_row, R.id.auto_confirm_label, R.id.auto_confirm)
        adaptSettingRow(R.id.remember_row, R.id.remember_label, R.id.remember)
        findViewById<Switch>(R.id.auto_confirm).apply {
            isChecked = repository.automationEnabled
            setOnCheckedChangeListener { _, checked -> repository.automationEnabled = checked; updateStatus() }
        }
        findViewById<CheckBox>(R.id.remember).apply {
            isChecked = repository.useRemember
            setOnCheckedChangeListener { _, checked -> repository.useRemember = checked }
        }
        findViewById<TextView>(R.id.auto_confirm_label).setOnClickListener { findViewById<Switch>(R.id.auto_confirm).performClick() }
        findViewById<TextView>(R.id.remember_label).setOnClickListener { findViewById<CheckBox>(R.id.remember).performClick() }
        findViewById<Button>(R.id.open_accessibility).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: RuntimeException) { Toast.makeText(this, R.string.settings_error, Toast.LENGTH_LONG).show() }
        }
        findViewById<Button>(R.id.refresh).setOnClickListener { updateStatus() }
        configureDetails(R.id.toggle_diagnostics, R.id.diagnostics_panel, R.string.show_diagnostics, R.string.hide_diagnostics,
            savedInstanceState?.getBoolean("diagnostics_expanded") == true)
        configureDetails(R.id.toggle_miui, R.id.miui_panel, R.string.show_miui, R.string.hide_miui,
            savedInstanceState?.getBoolean("miui_expanded") == true)
    }

    private fun adaptSettingRow(rowId: Int, labelId: Int, controlId: Int) {
        if (resources.configuration.fontScale < 1.3f) return
        val row = findViewById<LinearLayout>(rowId)
        row.orientation = LinearLayout.VERTICAL
        findViewById<View>(labelId).layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        findViewById<View>(controlId).layoutParams = LinearLayout.LayoutParams(
            (64 * resources.displayMetrics.density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
            topMargin = (8 * resources.displayMetrics.density).toInt()
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWindowInsets() {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = getColor(R.color.page_background)
        }
        window.decorView.systemUiVisibility = flags
        val page = findViewById<View>(R.id.page)
        page.setOnApplyWindowInsetsListener { view, insets ->
            // Absolute padding, not cumulative: rotations/repeated dispatch cannot move the UI.
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets.consumeSystemWindowInsets()
        }
        page.requestApplyInsets()
    }

    private fun configureDetails(buttonId: Int, panelId: Int, showText: Int, hideText: Int, expanded: Boolean) {
        val panel = findViewById<View>(panelId)
        val button = findViewById<Button>(buttonId)
        fun apply(open: Boolean) {
            panel.visibility = if (open) View.VISIBLE else View.GONE
            button.setText(if (open) hideText else showText)
        }
        apply(expanded)
        button.setOnClickListener { apply(panel.visibility != View.VISIBLE) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("diagnostics_expanded", findViewById<View>(R.id.diagnostics_panel).visibility == View.VISIBLE)
        outState.putBoolean("miui_expanded", findViewById<View>(R.id.miui_panel).visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
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
        findViewById<Switch>(R.id.auto_confirm).apply {
            if (isChecked != repository.automationEnabled) isChecked = repository.automationEnabled
        }
        findViewById<CheckBox>(R.id.remember).apply {
            if (isChecked != repository.useRemember) isChecked = repository.useRemember
        }
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, AnyDeskAccessibilityService::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val info = it.resolveInfo?.serviceInfo ?: return@any false
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
