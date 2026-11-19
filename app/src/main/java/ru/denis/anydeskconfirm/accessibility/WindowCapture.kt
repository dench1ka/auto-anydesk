package ru.denis.anydeskconfirm.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/** Owns root and children. Never retain across callbacks or retries. */
@Suppress("DEPRECATION")
class WindowCapture private constructor(val window: DialogWindow, private val handles: List<AccessibilityNodeInfo>) : AutoCloseable {
    fun click(node: DialogNode): Boolean {
        val live = handles[node.index]
        // Refresh the actual target immediately before acting; no parent/coordinate fallback.
        if (!live.refresh() || live.packageName?.toString() != DialogDetector.SYSTEM_UI ||
            live.windowId != window.windowId || live.viewIdResourceName != node.viewId ||
            DialogDetector.normalize(live.text?.toString().orEmpty()) != DialogDetector.normalize(node.text) ||
            !live.isVisibleToUser || !live.isEnabled || !live.isClickable) return false
        if (node.viewId == DialogDetector.REMEMBER_ID && (!live.isCheckable || live.isChecked)) return false
        return live.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun close() { handles.forEach { it.recycle() } }

    companion object {
        private const val MAX_NODES = 256
        private const val MAX_DEPTH = 32
        fun read(root: AccessibilityNodeInfo): WindowCapture {
            val handles = mutableListOf<AccessibilityNodeInfo>()
            val nodes = mutableListOf<DialogNode>()
            var complete = true
            try {
                fun visit(node: AccessibilityNodeInfo, depth: Int) {
                    handles.add(node)
                    nodes.add(DialogNode(handles.lastIndex, node.packageName?.toString().orEmpty(), node.windowId,
                        node.viewIdResourceName.orEmpty(), node.text?.toString().orEmpty(), node.className?.toString().orEmpty(),
                        node.isVisibleToUser, node.isEnabled, node.isClickable, node.isCheckable, node.isChecked))
                    if (depth >= MAX_DEPTH || handles.size >= MAX_NODES) {
                        if (node.childCount > 0) complete = false
                        return
                    }
                    for (i in 0 until node.childCount) {
                        if (handles.size >= MAX_NODES) { complete = false; break }
                        val child = node.getChild(i)
                        if (child == null) complete = false else visit(child, depth + 1)
                    }
                }
                visit(root, 0)
                return WindowCapture(DialogWindow(root.packageName?.toString().orEmpty(), root.windowId, nodes, complete), handles)
            } catch (error: RuntimeException) {
                handles.forEach { it.recycle() }
                throw error
            }
        }
    }
}
