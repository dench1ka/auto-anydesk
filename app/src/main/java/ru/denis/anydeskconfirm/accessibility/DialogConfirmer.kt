package ru.denis.anydeskconfirm.accessibility

/** Only a validated, freshly captured window may reach the action gateway. */
object DialogConfirmer {
    data class Result(val accepted: Boolean, val error: String? = null)

    fun confirm(match: DialogMatch, enabled: Boolean, click: (DialogNode) -> Boolean): Result {
        if (!enabled) return Result(false, "Автоматизация выключена")
        if (!match.button.enabled || !match.button.visible || !match.button.clickable)
            return Result(false, "Кнопка недоступна через Accessibility")
        return try {
            if (click(match.button)) Result(true)
            else Result(false, "ACTION_CLICK вернул false")
        } catch (_: RuntimeException) {
            Result(false, "Ошибка Accessibility API при ACTION_CLICK")
        }
    }

    /** Optional checkbox failure never blocks ordinary confirmation. */
    fun remember(match: DialogMatch, click: (DialogNode) -> Boolean): Result {
        val node = match.remember ?: return Result(false)
        if (node.checked || !node.enabled || !node.visible || !node.clickable || !node.checkable) return Result(false)
        return try { Result(click(node)) }
        catch (_: RuntimeException) { Result(false, "Ошибка Accessibility API при установке галочки") }
    }
}
