package ru.denis.anydeskconfirm

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.*
import org.junit.Test
import ru.denis.anydeskconfirm.accessibility.DialogDetector
import ru.denis.anydeskconfirm.startup.WakeSignal

class WakeSignalTest {
    private val state = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    private val message = "Приложение AnyDesk получит доступ к изображению на экране устройства."
    @Test fun anyDeskWindowCanRequestWake() { assertTrue(WakeSignal.matches(state, DialogDetector.ANYDESK, emptyList())) }
    @Test fun anyDeskContentEventsDoNotContinuouslyWake() {
        assertFalse(WakeSignal.matches(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, DialogDetector.ANYDESK, emptyList()))
    }
    @Test fun systemUiRequiresFullKnownProjectionMessage() { assertTrue(WakeSignal.matches(state, DialogDetector.SYSTEM_UI, listOf(message))) }
    @Test fun startOrAnyDeskNameAloneDoNotWake() {
        assertFalse(WakeSignal.matches(state, DialogDetector.SYSTEM_UI, listOf("НАЧАТЬ")))
        assertFalse(WakeSignal.matches(state, DialogDetector.SYSTEM_UI, listOf("Уведомление AnyDesk")))
    }
    @Test fun anotherApplicationCannotRequestWake() { assertFalse(WakeSignal.matches(state, "other.app", listOf(message))) }
    @Test fun genericWindowChangeDoesNotWake() {
        assertFalse(WakeSignal.matches(AccessibilityEvent.TYPE_WINDOWS_CHANGED, DialogDetector.ANYDESK, listOf(message)))
    }
}
