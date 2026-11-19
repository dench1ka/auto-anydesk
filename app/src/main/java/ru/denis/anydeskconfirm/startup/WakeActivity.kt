package ru.denis.anydeskconfirm.startup

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.view.WindowManager
import android.widget.TextView
import ru.denis.anydeskconfirm.R

/** Visible, short, internal preparation window; never enters a credential or presses UI buttons. */
class WakeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val done = Runnable {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val user = getSystemService(USER_SERVICE) as UserManager
        StartupSettings(this).record(if (keyguard.isKeyguardLocked)
            "Android всё ещё сообщает блокировку; подготовка не подтверждена"
        else if (!user.isUserUnlocked) "Экран без блокировки, но данные пользователя ещё недоступны"
        else "Блокировка не обнаружена; подключение AnyDesk нужно проверить отдельно")
        finish()
    }
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!StartupCoordinator.apply(this)) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        setContentView(TextView(this).apply {
            text = getString(R.string.screen_preparing)
            textSize = 18f
            setTextColor(getColor(R.color.text_primary))
            setBackgroundColor(getColor(R.color.page_background))
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        })
        // No KEEP_SCREEN_ON, power WakeLock, lockNow(), HOME/BACK or repeating callback.
        handler.postDelayed(done, 800)
    }
    override fun onPause() {
        handler.removeCallbacks(done)
        super.onPause()
        if (!isFinishing) finish()
    }
    override fun onDestroy() { handler.removeCallbacks(done); super.onDestroy() }
}
