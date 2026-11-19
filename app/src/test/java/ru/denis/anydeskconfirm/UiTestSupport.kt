package ru.denis.anydeskconfirm

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import ru.denis.anydeskconfirm.settings.SettingsRepository
import kotlin.math.max
import kotlin.math.pow

internal object UiTestSupport {
    val profiles = listOf(320 to 480, 360 to 640, 480 to 320, 411 to 731)
    val fontScales = listOf(1f, 1.3f, 2f)

    fun measure(page: View, width: Int, height: Int) {
        page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        page.layout(0, 0, width, height)
    }

    fun matrix(realTextMetrics: Boolean) {
        for ((width, height) in profiles) for (font in fontScales) {
            RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp-${if (width > height) "land" else "port"}-mdpi")
            RuntimeEnvironment.setFontScale(font)
            SettingsRepository(RuntimeEnvironment.getApplication()).preferences.edit().clear().commit()
            Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                val page = activity.findViewById<ScrollView>(R.id.page)
                for (expanded in listOf(false, true)) {
                    if (expanded) {
                        activity.findViewById<View>(R.id.toggle_diagnostics).performClick()
                        activity.findViewById<View>(R.id.toggle_miui).performClick()
                    }
                    measure(page, width, height)
                    val scenario = "SDK ${android.os.Build.VERSION.SDK_INT}, ${width}x${height}, font=$font, expanded=$expanded"
                    checkTree(page, scenario, realTextMetrics)
                    checkTargets(activity, scenario)
                    // The final action/text must remain reachable through scrolling.
                    val last = activity.findViewById<View>(if (expanded) R.id.miui_help else R.id.toggle_miui)
                    val body = page.getChildAt(0)
                    page.scrollTo(0, max(0, body.height - page.height + page.paddingTop + page.paddingBottom))
                    val bounds = Rect(0, 0, last.width, last.height)
                    page.offsetDescendantRectToMyCoords(last, bounds)
                    // offsetDescendantRectToMyCoords returns content coordinates; account for root scrolling.
                    bounds.offset(-page.scrollX, -page.scrollY)
                    assertTrue("Bottom unreachable: $scenario / $bounds", bounds.bottom <= height - page.paddingBottom + 1)
                    assertTrue("Last item outside viewport: $scenario / $bounds", bounds.bottom > page.paddingTop)
                    page.scrollTo(0, 0)
                }
                println("UI geometry passed: SDK ${android.os.Build.VERSION.SDK_INT}, ${width}x${height}, font=$font, closed/open panels")
            }
        }
    }

    fun checkTree(view: View, scenario: String, textMetrics: Boolean) {
        if (view.visibility != View.VISIBLE) return
        assertTrue("Zero-width view: $scenario id=${view.id}", view.width > 0)
        assertTrue("Zero-height view: $scenario id=${view.id}", view.height > 0)
        if (view is TextView && view.text.isNotEmpty()) {
            val textLayout = view.layout
            assertNotNull("No text layout: $scenario ${view.text}", textLayout)
            if (textMetrics) {
                val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
                for (line in 0 until textLayout.lineCount) {
                    assertEquals("Ellipsized text: $scenario ${view.text}", 0, textLayout.getEllipsisCount(line))
                    assertTrue("Text clipped horizontally: $scenario ${view.text} line=$line width=${textLayout.getLineMax(line)} available=$available",
                        textLayout.getLineMax(line) <= available + 1)
                }
                assertTrue("Text clipped vertically: $scenario ${view.text}",
                    textLayout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom + 1)
            }
            val background = view.context.getColor(if (view.id == R.id.open_accessibility) R.color.accent else R.color.page_background)
            assertTrue("Low text contrast: $scenario ${view.text}", contrast(view.currentTextColor, background) >= 4.5)
        }
        if (view is ViewGroup) {
            var previousEnd = view.paddingTop
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                if (child.visibility != View.VISIBLE) continue
                assertTrue("Outside left: $scenario", child.left >= view.paddingLeft)
                assertTrue("Outside right: $scenario", child.right <= view.width - view.paddingRight)
                if (view is LinearLayout && view.orientation == LinearLayout.VERTICAL) {
                    assertTrue("Overlapping rows: $scenario", child.top >= previousEnd)
                    previousEnd = child.bottom
                }
                checkTree(child, scenario, textMetrics)
            }
        }
    }

    private fun checkTargets(activity: MainActivity, scenario: String) {
        for (id in listOf(R.id.auto_confirm, R.id.remember, R.id.screen_access, R.id.open_accessibility, R.id.refresh, R.id.toggle_diagnostics, R.id.toggle_miui)) {
            val view = activity.findViewById<View>(id)
            assertTrue("Small touch target: $scenario id=$id", view.width >= 48 && view.height >= 48)
        }
        for ((labelId, controlId) in listOf(R.id.auto_confirm_label to R.id.auto_confirm, R.id.remember_label to R.id.remember,
            R.id.screen_access_label to R.id.screen_access)) {
            val label = activity.findViewById<View>(labelId)
            val control = activity.findViewById<View>(controlId)
            assertFalse("Label overlaps toggle: $scenario", Rect.intersects(
                Rect(label.left, label.top, label.right, label.bottom), Rect(control.left, control.top, control.right, control.bottom)))
        }
    }

    private fun contrast(first: Int, second: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 255) / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val a = luminance(first); val b = luminance(second)
        return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
    }
}
