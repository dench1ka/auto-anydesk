package ru.denis.anydeskconfirm

import org.junit.Assert.*
import org.junit.Test
import ru.denis.anydeskconfirm.accessibility.*

class DialogSafetyTest {
    private fun window(message: String = "Приложение AnyDesk получит доступ к изображению на экране устройства.",
                       button: String = "НАЧАТЬ", pkg: String = DialogDetector.SYSTEM_UI): DialogWindow = DialogWindow(pkg, 9, listOf(
        DialogNode(0, pkg, 9, "android:id/message", message, "android.widget.TextView"),
        DialogNode(1, pkg, 9, DialogDetector.BUTTON_ID, button, "android.widget.Button", clickable = true),
        DialogNode(2, pkg, 9, DialogDetector.REMEMBER_ID, "Больше не показывать", "android.widget.CheckBox", clickable = true, checkable = true)
    ))
    private fun detect(window: DialogWindow) = DialogDetector.detect(window, true).match

    @Test fun recognizesRussianSystemDialog() { assertNotNull(detect(window())) }
    @Test fun recognizesEnglishAndWhitespace() {
        assertNotNull(detect(window("  ANYDESK\u00a0will start capturing everything that’s\n displayed on your screen.  ", "StArT NOW")))
    }
    @Test fun recognizesOnlyKnownRussianAlternative() {
        assertNotNull(detect(window("Приложение AnyDesk получит доступ ко всему, что отображается на экране.")))
    }
    @Test fun ignoresSameTextInAnyDeskWindow() { assertNull(detect(window(pkg = DialogDetector.ANYDESK))) }
    @Test fun ignoresOtherApplication() { assertNull(detect(window("Приложение OtherApp получит доступ к изображению на экране устройства."))) }
    @Test fun ignoresStartButtonAlone() { assertNull(detect(window("Начать обновление?"))) }
    @Test fun ignoresUnrelatedAnyDeskPrompt() { assertNull(detect(window("AnyDesk: начать установку обновления?"))) }
    @Test fun rejectsTextWithArbitraryPrefixOrSuffix() {
        assertNull(detect(window("Уведомление: Приложение AnyDesk получит доступ к изображению на экране устройства.")))
        assertNull(detect(window("Приложение AnyDesk получит доступ к изображению на экране устройства. Установить обновление?")))
    }
    @Test fun rejectsSimilarAppLabel() { assertNull(detect(window("Приложение FakeAnyDesk получит доступ к изображению на экране устройства."))) }
    @Test fun rejectsUninstalledAnyDesk() { assertNull(DialogDetector.detect(window(), false).match) }
    @Test fun rejectsUnknownLanguage() { assertNull(detect(window("AnyDesk va capturer votre écran"))) }
    @Test fun rejectsMissingButton() { val w = window(); assertNull(detect(w.copy(nodes = w.nodes.take(1)))) }
    @Test fun rejectsWrongButtonId() {
        val w = window(); assertNull(detect(w.copy(nodes = w.nodes.map { if (it.index == 1) it.copy(viewId = "other:id/start") else it })))
    }
    @Test fun rejectsWrongButtonText() { assertNull(detect(window(button = "УДАЛИТЬ"))) }
    @Test fun rejectsAmbiguousButton() { val w = window(); assertNull(detect(w.copy(nodes = w.nodes + w.nodes[1]))) }
    @Test fun rejectsInvisibleMessage() {
        val w = window(); assertNull(detect(w.copy(nodes = w.nodes.map { if (it.index == 0) it.copy(visible = false) else it })))
    }
    @Test fun rejectsMixedPackagesAndWindows() {
        val w = window()
        assertNull(detect(w.copy(nodes = w.nodes + DialogNode(3, "impostor", 9))))
        assertNull(detect(w.copy(nodes = w.nodes + DialogNode(3, DialogDetector.SYSTEM_UI, 10))))
    }
    @Test fun rejectsTruncatedTree() { assertNull(detect(window().copy(complete = false))) }
    @Test fun missingCheckboxStillAllowsConfirm() {
        val w = window(); val match = detect(w.copy(nodes = w.nodes.take(2)))!!
        assertNull(match.remember)
        assertTrue(DialogConfirmer.confirm(match, true) { true }.accepted)
    }
    @Test fun disabledAutomationNeverInvokesAction() {
        assertFalse(DialogConfirmer.confirm(detect(window())!!, false) { error("must not click") }.accepted)
    }
    @Test fun disabledOrUnclickableButtonNeverInvokesAction() {
        val m = detect(window())!!
        assertFalse(DialogConfirmer.confirm(m.copy(button = m.button.copy(enabled = false)), true) { error("must not click") }.accepted)
        assertFalse(DialogConfirmer.confirm(m.copy(button = m.button.copy(clickable = false)), true) { error("must not click") }.accepted)
    }
    @Test fun falseAndExceptionAreReported() {
        val m = detect(window())!!
        assertFalse(DialogConfirmer.confirm(m, true) { false }.accepted)
        val failure = DialogConfirmer.confirm(m, true) { throw IllegalStateException("private screen data") }
        assertFalse(failure.accepted)
        assertFalse(failure.error!!.contains("private"))
    }
    @Test fun checkedCheckboxNeverTogglesOff() {
        val m = detect(window())!!
        assertFalse(DialogConfirmer.remember(m.copy(remember = m.remember!!.copy(checked = true))) { error("must not click") }.accepted)
    }
    @Test fun unavailableCheckboxDoesNotThrow() {
        val m = detect(window())!!
        assertFalse(DialogConfirmer.remember(m.copy(remember = null)) { error("must not click") }.accepted)
        assertFalse(DialogConfirmer.remember(m.copy(remember = m.remember!!.copy(enabled = false))) { error("must not click") }.accepted)
        assertNotNull(DialogConfirmer.remember(m) { throw SecurityException("secret") }.error)
    }
}
