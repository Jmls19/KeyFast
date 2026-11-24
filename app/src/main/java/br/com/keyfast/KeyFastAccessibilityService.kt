package br.com.keyfast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class KeyFastAccessibilityService : AccessibilityService() {
    enum class FillResult { FILLED, FOCUSED, NOT_FOUND }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun locateAndFillDocument(document: String): FillResult {
        val root = rootInActiveWindow ?: return FillResult.NOT_FOUND
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)

        val label = nodes.firstOrNull { node ->
            val value = (node.text?.toString() + " " + node.contentDescription?.toString())
                .lowercase()
            DOCUMENT_LABELS.any { value.contains(it) }
        } ?: return FillResult.NOT_FOUND

        val labelBounds = Rect().also { label.getBoundsInScreen(it) }
        val density = resources.displayMetrics.density
        val candidates = nodes.filter { node ->
            node.isEditable || node.className?.toString()?.contains("EditText", true) == true
        }
        if (candidates.isEmpty()) return FillResult.NOT_FOUND

        val direct = candidates.firstOrNull { node ->
            val value = (
                node.hintText?.toString() + " " +
                node.contentDescription?.toString() + " " +
                node.viewIdResourceName.orEmpty()
            ).lowercase()
            DOCUMENT_LABELS.any { value.contains(it) }
        }

        val nearby = candidates.mapNotNull { node ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val vertical = bounds.top - labelBounds.bottom
            val horizontalOverlap = bounds.left < labelBounds.right + (density * 180).toInt() &&
                bounds.right > labelBounds.left - (density * 180).toInt()
            if (vertical >= -(density * 12).toInt() &&
                vertical <= (density * 360).toInt() && horizontalOverlap) {
                node to (vertical.coerceAtLeast(0) + abs(bounds.left - labelBounds.left) / 3)
            } else null
        }.minByOrNull { it.second }?.first

        val target = direct ?: nearby ?: return FillResult.NOT_FOUND
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, document)
        }
        if (target.isEditable &&
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return FillResult.FILLED
        }

        val bounds = Rect().also { target.getBoundsInScreen(it) }
        if (bounds.isEmpty) return FillResult.NOT_FOUND
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
            .build()
        return if (dispatchGesture(gesture, null, null)) FillResult.FOCUSED
        else FillResult.NOT_FOUND
    }

    private fun collect(node: AccessibilityNodeInfo, output: MutableList<AccessibilityNodeInfo>) {
        output.add(node)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collect(it, output) }
        }
    }

    companion object {
        @Volatile var instance: KeyFastAccessibilityService? = null
            private set
        private val DOCUMENT_LABELS = listOf(
            "documento", "document", "cpf", "cnpj", "identidade", "rg"
        )
    }
}
