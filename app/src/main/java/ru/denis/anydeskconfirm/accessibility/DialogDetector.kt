package ru.denis.anydeskconfirm.accessibility

import java.util.Locale

/** Transient, bounded snapshot. Text is never persisted or logged. */
data class DialogNode(
    val index: Int,
    val packageName: String,
    val windowId: Int,
    val viewId: String = "",
    val text: String = "",
    val className: String = "",
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val clickable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false
)

data class DialogWindow(val packageName: String, val windowId: Int, val nodes: List<DialogNode>, val complete: Boolean = true)
data class DialogMatch(val windowId: Int, val button: DialogNode, val remember: DialogNode?)
data class Detection(val match: DialogMatch? = null, val rejection: String? = null)

object DialogDetector {
    const val SYSTEM_UI = "com.android.systemui"
    const val ANYDESK = "com.anydesk.anydeskandroid"
    const val BUTTON_ID = "android:id/button1"
    const val REMEMBER_ID = "com.android.systemui:id/remember"

    fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('\u00a0', ' ').replace('\u202f', ' ').replace('’', '\'')
        .replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', ' ')

    // Full templates, rather than loose keywords. Unknown translations fail closed.
    private val messages = setOf(
        "приложение anydesk получит доступ к изображению на экране устройства",
        "приложение anydesk получит доступ ко всему, что отображается на экране",
        "anydesk will start capturing everything that's displayed on your screen"
    )
    private val buttons = setOf("начать", "start now", "start")

    fun detect(window: DialogWindow, anyDeskInstalled: Boolean): Detection {
        if (window.packageName != SYSTEM_UI) return Detection()
        val visible = window.nodes.filter { it.visible }
        val mentionsAnyDesk = visible.any { Regex("(?i)(?<![\\p{L}\\p{N}_])anydesk(?![\\p{L}\\p{N}_])").containsMatchIn(it.text) }
        if (!mentionsAnyDesk) return Detection()
        if (window.windowId < 0) return Detection(rejection = "Системное окно не имеет действительного ID")
        if (!window.complete) return Detection(rejection = "Неполное дерево системного окна")
        if (!anyDeskInstalled) return Detection(rejection = "Пакет AnyDesk не установлен или отключён")
        if (window.nodes.any { it.windowId != window.windowId || it.packageName != SYSTEM_UI })
            return Detection(rejection = "Неоднозначная принадлежность элементов окна")
        if (visible.none { normalize(it.text) in messages })
            return Detection(rejection = "Неизвестный текст запроса AnyDesk: нажатие запрещено")
        val candidates = visible.filter {
            it.viewId == BUTTON_ID && normalize(it.text) in buttons && it.className == "android.widget.Button"
        }
        if (candidates.size != 1) return Detection(rejection = "Кнопка подтверждения отсутствует или неоднозначна")
        val remember = visible.filter {
            it.viewId == REMEMBER_ID && it.className == "android.widget.CheckBox" && it.checkable
        }.singleOrNull()
        return Detection(DialogMatch(window.windowId, candidates.single(), remember))
    }
}
