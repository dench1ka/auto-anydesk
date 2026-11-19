package ru.denis.anydeskconfirm

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an emulator/test device; verifies the real framework layout and settings panels. */
@RunWith(AndroidJUnit4::class)
class DeviceUiTest {
    @Test fun screenControlsAndDetailsAreReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val page = activity.findViewById<ScrollView>(R.id.page)
                val density = activity.resources.displayMetrics.density
                assertTrue(activity.findViewById<TextView>(R.id.title).text.isNotEmpty())
                assertTrue(activity.findViewById<TextView>(R.id.app_version).text.isNotEmpty())
                for (id in listOf(R.id.open_accessibility, R.id.auto_confirm, R.id.remember, R.id.screen_access, R.id.refresh)) {
                    val control = activity.findViewById<View>(id)
                    assertTrue(control.width >= 48 * density && control.height >= 48 * density)
                    val rect = Rect(0, 0, control.width, control.height)
                    page.offsetDescendantRectToMyCoords(control, rect)
                    page.scrollTo(0, maxOf(0, rect.top - page.paddingTop))
                    val visible = Rect()
                    assertTrue("Control cannot be reached by scrolling", control.getGlobalVisibleRect(visible))
                    assertTrue("Control is clipped", visible.width() >= 48 * density && visible.height() >= 48 * density)
                }
                activity.findViewById<View>(R.id.toggle_diagnostics).performClick()
                activity.findViewById<View>(R.id.toggle_miui).performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(activity.findViewById<View>(R.id.diagnostic_text).isShown)
                assertTrue(activity.findViewById<View>(R.id.miui_help).isShown)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
