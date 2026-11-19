package ru.denis.anydeskconfirm

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.WindowInsets
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import ru.denis.anydeskconfirm.settings.SettingsRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 35])
class UiLayoutTest {
    @Before fun reset() {
        RuntimeEnvironment.setFontScale(1f)
        SettingsRepository(RuntimeEnvironment.getApplication()).preferences.edit().clear().commit()
    }

    @Test fun dimensionsFontsAndExpandedContentRemainReachable() { UiTestSupport.matrix(false) }

    @Test fun labelsToggleAndPreferencesSurviveActivityRecreation() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<TextView>(R.id.auto_confirm_label).performClick()
            activity.findViewById<TextView>(R.id.remember_label).performClick()
            assertTrue(SettingsRepository(activity).automationEnabled)
            assertTrue(SettingsRepository(activity).useRemember)
            activity.findViewById<View>(R.id.toggle_diagnostics).performClick()
            activity.findViewById<View>(R.id.toggle_miui).performClick()
            controller.recreate()
            val restored = controller.get()
            assertTrue(restored.findViewById<Switch>(R.id.auto_confirm).isChecked)
            assertTrue(restored.findViewById<CheckBox>(R.id.remember).isChecked)
            assertEquals(View.VISIBLE, restored.findViewById<View>(R.id.diagnostics_panel).visibility)
            assertEquals(View.VISIBLE, restored.findViewById<View>(R.id.miui_panel).visibility)
        }
    }

    @Test fun detailsOpenAndCloseWithoutHidingControls() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val button = activity.findViewById<View>(R.id.toggle_diagnostics)
            val panel = activity.findViewById<View>(R.id.diagnostics_panel)
            assertEquals(View.GONE, panel.visibility)
            button.performClick(); assertEquals(View.VISIBLE, panel.visibility)
            button.performClick(); assertEquals(View.GONE, panel.visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.auto_confirm).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.open_accessibility).visibility)
        }
    }

    @Suppress("DEPRECATION")
    @Test fun repeatedInsetsReserveBarsWithoutAccumulatingPadding() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val page = controller.get().findViewById<ScrollView>(R.id.page)
            repeat(3) {
                val insets = ReflectionHelpers.callConstructor(WindowInsets::class.java,
                    ClassParameter.from(Rect::class.java, Rect(8, 32, 12, 48)))
                page.dispatchApplyWindowInsets(insets)
            }
            assertEquals(8, page.paddingLeft); assertEquals(32, page.paddingTop)
            assertEquals(12, page.paddingRight); assertEquals(48, page.paddingBottom)
            assertTrue(page.clipToPadding)
            UiTestSupport.measure(page, 320, 480)
            assertTrue(page.getChildAt(0).top >= 32)
            assertTrue(page.getChildAt(0).right <= 308)
        }
    }

    @Test fun setupButtonOpensAccessibilitySettings() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<View>(R.id.open_accessibility).performClick()
            val intent: Intent = shadowOf(activity).nextStartedActivity
            assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        }
    }
    @Test fun preferencesChangedWhileAwayAreReflectedWhenReturning() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            controller.pause()
            SettingsRepository(controller.get()).apply { automationEnabled = true; useRemember = true }
            controller.resume()
            assertTrue(controller.get().findViewById<Switch>(R.id.auto_confirm).isChecked)
            assertTrue(controller.get().findViewById<CheckBox>(R.id.remember).isChecked)
        }
    }
}
