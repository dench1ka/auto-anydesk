package ru.denis.anydeskconfirm

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.WindowInsets
import android.widget.ScrollView
import android.widget.FrameLayout
import android.view.ViewGroup
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiNativeLayoutTest {
    @Test fun realFontMetricsDoNotClipAcrossTheScreenMatrix() { UiTestSupport.matrix(true) }
    @Test fun scrollingDoesNotDrawTextInsideSystemBarInsets() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val page = activity.findViewById<ScrollView>(R.id.page)
            val insets = ReflectionHelpers.callConstructor(WindowInsets::class.java,
                ClassParameter.from(Rect::class.java, Rect(8, 32, 12, 48)))
            page.dispatchApplyWindowInsets(insets)
            // Draw via a parent: ViewGroup applies the child's scroll transform before drawing.
            (page.parent as ViewGroup).removeView(page)
            val host = FrameLayout(activity)
            host.setBackgroundColor(activity.getColor(R.color.page_background))
            host.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            UiTestSupport.measure(host, 320, 480)
            page.scrollTo(0, 100)
            fun render(): Bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888).also { host.draw(Canvas(it)) }
            val bitmap = render()
            val background = activity.getColor(R.color.page_background)
            for (x in 16 until 304) {
                for (y in 2 until 30) assertEquals("Text entered status-bar area", background, bitmap.getPixel(x, y))
                for (y in 434 until 478) assertEquals("Text entered navigation-bar area", background, bitmap.getPixel(x, y))
            }
            page.clipToPadding = false
            val unclipped = render()
            assertTrue("Negative control must expose scrolling content inside the reserved area",
                (16 until 304).any { x -> (2 until 30).any { y -> unclipped.getPixel(x, y) != background } })
        }
    }
}
