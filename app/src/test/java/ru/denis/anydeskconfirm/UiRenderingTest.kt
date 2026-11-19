package ru.denis.anydeskconfirm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import ru.denis.anydeskconfirm.settings.SettingsRepository

/** Host rendering using Android native Skia, not a mock drawing of the layout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h480dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiRenderingTest {
    private fun capture(name: String, fontScale: Float, width: Int = 320, height: Int = 480, expanded: Boolean = false) {
        RuntimeEnvironment.setFontScale(fontScale)
        val prefs = SettingsRepository(RuntimeEnvironment.getApplication()).preferences
        prefs.edit().clear().commit()
        if (expanded) prefs.edit()
            .putString("last_result", "ACTION_CLICK принят Android; соединение не проверено")
            .putString("last_error", "Не удалось получить актуальное системное окно через Accessibility")
            .putLong("successful_clicks", 3L).commit()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val page = activity.findViewById<ScrollView>(R.id.page)
            if (expanded) {
                activity.findViewById<View>(R.id.toggle_diagnostics).performClick()
                activity.findViewById<View>(R.id.toggle_miui).performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            UiTestSupport.measure(page, width, height)
            UiTestSupport.checkTree(page, "Native capture: $name", true)
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            canvas.drawColor(Color.WHITE)
            page.draw(canvas)
            val directory = File(System.getProperty("ui.outputDir", "artifacts/ui") ?: "artifacts/ui")
            directory.mkdirs()
            val prefix = System.getProperty("ui.capturePrefix", "current")
            directory.resolve("$prefix-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val body = page.getChildAt(0)
            val full = Bitmap.createBitmap(width, body.height, Bitmap.Config.ARGB_8888)
            full.eraseColor(activity.getColor(R.color.page_background))
            body.draw(Canvas(full))
            directory.resolve("$prefix-$name-full.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
            for (id in listOf(R.id.auto_confirm, R.id.remember, R.id.open_accessibility, R.id.refresh)) {
                val view = activity.findViewById<TextView>(id)
                println("UI $name id=$id size=${view.width}x${view.height} textSize=${view.textSize} lines=${view.lineCount} maxLines=${view.maxLines} available=${view.width - view.compoundPaddingLeft - view.compoundPaddingRight}")
            }
            assertTrue(page.getChildAt(0).height > 0)
        }
    }
    @Test fun normalFont() { capture("320-font100", 1f) }
    @Test fun largeFont() { capture("320-font200", 2f) }
    @Test @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun landscape() { capture("480-landscape-font130", 1.3f, 480, 320) }
    @Test @Config(qualifiers = "w360dp-h640dp-night-mdpi")
    fun systemNightMode() { capture("360-night-font100", 1f, 360, 640) }
    @Test @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun expandedDiagnosticsWithLongValues() { capture("360-expanded-font130", 1.3f, 360, 640, true) }
}
