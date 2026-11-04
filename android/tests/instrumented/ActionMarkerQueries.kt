package com.lonnnnnng.flclash.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*

/** 作者 long：图标只保留测试标识；操作定位到最近的真实点击容器，不依赖平台朗读描述。 */
private fun hasActionMarker(label: String) = SemanticsMatcher("操作标识 $label") { node ->
    fun ownsMarker(candidate: SemanticsNode): Boolean {
        if (candidate.config.getOrNull(SemanticsProperties.TestTag) == "action:$label") return true
        return candidate.children.any { child ->
            !child.config.contains(SemanticsActions.OnClick) && ownsMarker(child)
        }
    }
    if (node.config.contains(SemanticsActions.OnClick)) ownsMarker(node)
    else node.config.getOrNull(SemanticsProperties.TestTag) == "action:$label" &&
        generateSequence(node.parent) { it.parent }.none { it.config.contains(SemanticsActions.OnClick) }
}

internal fun SemanticsNodeInteractionsProvider.onActionMarker(label: String) = onNode(hasActionMarker(label), useUnmergedTree = true)
internal fun SemanticsNodeInteractionsProvider.onAllActionMarkers(label: String) = onAllNodes(hasActionMarker(label), useUnmergedTree = true)

internal fun SemanticsNodeInteraction.assertProxyInUse(provider: SemanticsNodeInteractionsProvider): SemanticsNodeInteraction {
    val tag = fetchSemanticsNode().config[SemanticsProperties.TestTag]
    provider.onNodeWithTag("${tag}_in_use", useUnmergedTree = true).assertIsDisplayed()
    return this
}

internal fun SemanticsNodeInteraction.assertProxyNotInUse(provider: SemanticsNodeInteractionsProvider): SemanticsNodeInteraction {
    val tag = fetchSemanticsNode().config[SemanticsProperties.TestTag]
    provider.onNodeWithTag("${tag}_in_use", useUnmergedTree = true).assertDoesNotExist()
    return this
}
