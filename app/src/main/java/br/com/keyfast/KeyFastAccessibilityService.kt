package br.com.keyfast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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

        // O formulário usa estes rótulos completos. A identificação exata evita
        // confundir o campo do nome com o campo do documento.
        val documentLabel = nodes.firstOrNull { node ->
            searchableText(node).contains(DOCUMENT_LABEL)
        } ?: return FillResult.NOT_FOUND

        val editable = nodes.filter { node ->
            (node.isEditable || node.className?.toString()?.contains("EditText", true) == true) &&
                node.isVisibleToUser && node.isEnabled
        }
        if (editable.isEmpty()) return FillResult.NOT_FOUND

        // Melhor caso: o próprio campo expõe "Documento do recebedor" como hint,
        // descrição ou ID acessível.
        val direct = editable.firstOrNull { node ->
            searchableText(node).contains(DOCUMENT_LABEL)
        }

        // Se o rótulo for um TextView separado, só considera campos abaixo dele.
        // O campo do nome, identificado pelo seu rótulo, fica fora dessa busca.
        val labelBounds = Rect().also { documentLabel.getBoundsInScreen(it) }
        val nameLabel = nodes.firstOrNull { searchableText(it).contains(NAME_LABEL) }
        val nameBounds = nameLabel?.let { Rect().also(it::getBoundsInScreen) }

        val belowDocumentLabel = editable.mapNotNull { node ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            if (bounds.isEmpty || bounds.top < labelBounds.bottom - dp(8)) return@mapNotNull null
            if (nameBounds != null && bounds.top <= nameBounds.bottom) return@mapNotNull null

            val horizontalDistance = when {
                bounds.left > labelBounds.right -> bounds.left - labelBounds.right
                labelBounds.left > bounds.right -> labelBounds.left - bounds.right
                else -> 0
            }
            if (horizontalDistance > dp(220) || bounds.top - labelBounds.bottom > dp(420)) {
                return@mapNotNull null
            }
            node to (bounds.top - labelBounds.bottom + horizontalDistance * 2)
        }.minByOrNull { it.second }?.first

        val target = direct ?: belowDocumentLabel ?: return FillResult.NOT_FOUND
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, document)
        }
        if (target.isEditable &&
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return FillResult.FILLED
        }

        // Último recurso: toca o centro do campo identificado, nunca uma posição fixa.
        val bounds = Rect().also { target.getBoundsInScreen(it) }
        if (bounds.isEmpty) return FillResult.NOT_FOUND
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 70))
            .build()
        return if (dispatchGesture(gesture, null, null)) FillResult.FOCUSED
        else FillResult.NOT_FOUND
    }

    private fun searchableText(node: AccessibilityNodeInfo): String =
        listOf(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString(),
            node.viewIdResourceName
        ).filterNotNull().joinToString(" ").lowercase().replace(Regex("\\s+"), " ").trim()

    private fun collect(node: AccessibilityNodeInfo, output: MutableList<AccessibilityNodeInfo>) {
        output.add(node)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collect(it, output) }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        @Volatile var instance: KeyFastAccessibilityService? = null
            private set
        private const val DOCUMENT_LABEL = "documento do recebedor"
        private const val NAME_LABEL = "nome do recebedor"
    }
}
