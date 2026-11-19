package ru.denis.anydeskconfirm

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.denis.anydeskconfirm.accessibility.*

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25])
class WindowCaptureTest {
    private fun node(textValue: String, viewId: String = "", button: Boolean = false): AccessibilityNodeInfo {
        return AccessibilityNodeInfo.obtain().apply {
            packageName = DialogDetector.SYSTEM_UI
            text = textValue
            viewIdResourceName = viewId
            className = if (button) "android.widget.Button" else "android.widget.TextView"
            isVisibleToUser = true; isEnabled = true; isClickable = button
        }
    }
    private fun tree(): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo> {
        val root = node("")
        val message = node("Приложение AnyDesk получит доступ к изображению на экране устройства.")
        val button = node("НАЧАТЬ", DialogDetector.BUTTON_ID, true)
        shadowOf(root).addChild(message); shadowOf(root).addChild(button)
        return root to button
    }
    @Test fun capturesTreeAndInvokesOnlyActionClick() {
        val (root, button) = tree()
        WindowCapture.read(root).use { capture ->
            val match = DialogDetector.detect(capture.window, true).match!!
            assertTrue(DialogConfirmer.confirm(match, true, capture::click).accepted)
            assertEquals(listOf(AccessibilityNodeInfo.ACTION_CLICK), shadowOf(button).performedActions)
        }
    }
    @Test fun staleTargetIsNotClicked() {
        val (root, button) = tree()
        // Robolectric getChild() copies the node; configure refresh before the copy is made.
        shadowOf(button).setRefreshReturnValue(false)
        WindowCapture.read(root).use { capture ->
            val match = DialogDetector.detect(capture.window, true).match!!
            assertFalse(DialogConfirmer.confirm(match, true, capture::click).accepted)
            assertTrue(shadowOf(button).performedActions.isEmpty())
        }
    }
    @Test fun snapshotTextMustMatchRefreshedTarget() {
        val (root, button) = tree()
        WindowCapture.read(root).use { capture ->
            val match = DialogDetector.detect(capture.window, true).match!!
            val changed = match.copy(button = match.button.copy(text = "УДАЛИТЬ"))
            assertFalse(DialogConfirmer.confirm(changed, true, capture::click).accepted)
            assertTrue(shadowOf(button).performedActions.isEmpty())
        }
    }
    @Test fun oversizedTreeFailsClosed() {
        val (root, _) = tree()
        repeat(260) { shadowOf(root).addChild(node("")) }
        WindowCapture.read(root).use { capture ->
            assertFalse(capture.window.complete)
            assertTrue(capture.window.nodes.size <= 256)
            assertNull(DialogDetector.detect(capture.window, true).match)
        }
    }
}
