package com.lonnnnnng.flclash.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class EmbeddedProfilePage(val root: Boolean, val scroll: ScrollState, val registerBack: (() -> Unit) -> Unit)
internal val LocalEmbeddedProfilePage = staticCompositionLocalOf<EmbeddedProfilePage?> { null }

/** 作者 long：组表单及其子页留在同一停靠窗口；子页切换不重启Sheet入场，也不丢加密草稿注册树。 */
@Composable
internal fun ProfileEditorSheet(page: String, scroll: ScrollState, closing: Boolean,
    onExit: () -> Unit, onBack: () -> Unit, onClosed: () -> Unit,
    isContentAtTop: () -> Boolean = { scroll.value == 0 },
    scrollForPage: (String) -> ScrollState = { scroll }, content: @Composable (String) -> Unit) {
    val registry = rememberEditorWindowRegistry()
    val pageStates = rememberSaveableStateHolder()
    val backHandlers = remember { mutableMapOf<String, () -> Unit>() }
    var previousPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) {
        // 作者 long：返回后重开选择器须以最新父草稿为起点；只保留当前子页的重建状态，不复活已退出的勾选会话。
        if (previousPage != page && previousPage != "root") pageStates.removeState(previousPage)
        previousPage = page
    }
    ConnectionModalSheet(onDismiss = onClosed, contentScrollState = scroll, avoidIme = true,
        sheetTag = "proxy_group_editor_sheet", closeRequested = closing, isContentAtTop = isContentAtTop,
        handleHeight = 20.dp, handleWidth = 28.dp,
        sheetShape = AppSuperellipseShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        onBackRequest = { backHandlers[page]?.invoke() ?: if (page != "root") onBack() else onExit(); true },
        dismissAllowed = { onExit(); false }) {
        ProvideEditorWindowRegistry(registry) {
            AnimatedContent(page, modifier = Modifier.fillMaxSize().clipToBounds(), transitionSpec = {
                val sign = if (targetState == "root") -1 else 1
                ((slideInHorizontally(tween(450, easing = SheetPageEasing)) { it * sign / 4 } +
                    fadeIn(tween(180, delayMillis = 68))) togetherWith
                    (slideOutHorizontally(tween(450, easing = SheetPageEasing)) { -it * sign / 4 } +
                        fadeOut(tween(135)))).using(null)
            }, label = "groupEditorPage") { shown ->
              pageStates.SaveableStateProvider(shown) {
                CompositionLocalProvider(LocalEmbeddedProfilePage provides EmbeddedProfilePage(shown == "root", scrollForPage(shown)) { backHandlers[shown] = it }) {
                    Box(Modifier.fillMaxSize().background(dashboardPageColor()).blockSheetInput(shown != page || closing)
                        .testTag("group_sheet_page_$shown")) { content(shown) }
                }
              }
            }
        }
    }
}

@Composable
internal fun EmbeddedProfileEditorPage(page: EmbeddedProfilePage, onDismiss: () -> Unit,
    title: @Composable () -> Unit, text: @Composable () -> Unit, actions: @Composable () -> Unit,
    backTag: String, contentPadding: PaddingValues, bottomBar: (@Composable () -> Unit)?) {
    // 作者 long：系统返回也走当前子页校验，快捷YAML的错误/放弃流程不能被外层路由直接绕过。
    SideEffect { page.registerBack(onDismiss) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp)) {
            IconButton(onDismiss, Modifier.align(Alignment.CenterStart).size(44.dp)
                .background(dashboardCardColor(), AppSuperellipseShape(44.dp)).testTag(backTag)) {
                Icon(if (page.root) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, null)
            }
            Box(Modifier.align(Alignment.Center).padding(horizontal = 88.dp)) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), title)
            }
            Row(Modifier.align(Alignment.CenterEnd).background(dashboardCardColor(), AppSuperellipseShape(44.dp)),
                verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(contentPadding)) {
            Box(Modifier.fillMaxWidth().weight(1f)) { text() }
            if (bottomBar != null) Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)) { bottomBar() }
        }
    }
}
