package ru.denis.anydeskconfirm.startup

import android.view.accessibility.AccessibilityEvent
import ru.denis.anydeskconfirm.accessibility.DialogDetector

/** A wake hint only. Clicking still requires the complete strict dialog detector. */
object WakeSignal {
    fun matches(type: Int, packageName: String?, text: List<CharSequence>): Boolean =
        type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            (packageName == DialogDetector.ANYDESK ||
                (packageName == DialogDetector.SYSTEM_UI && text.any { DialogDetector.isProjectionMessage(it.toString()) }))
}
