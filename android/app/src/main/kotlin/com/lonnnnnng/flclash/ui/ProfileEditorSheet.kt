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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

internal data class EmbeddedProfilePage(val root: Boolean, val scroll: ScrollState,
    val registerBack: (() -> Unit) -> Unit, val reportBodyHeight: (Int?) -> Unit)
internal val LocalEmbeddedProfilePage = staticCompositionLocalOf<EmbeddedProfilePage?> { null }

/** 作者 long：组表单及子页共享停靠与加密树，拖回预览不注册返回动作、不写草稿或参与正文测量。 */
@Composable
internal fun ProfileEditorSheet(page: String, scroll: ScrollState, closing: Boolean,
    onExit: () -> Unit, onBack: () -> Unit, onClosed: () -> Unit,
    isContentAtTop: () -> Boolean = { scroll.value == 0 },
    scrollForPage: (String) -> ScrollState = { scroll }, content: @Composable (String) -> Unit) {
    val registry = rememberEditorWindowRegistry()
    val pageStates = rememberSaveableStateHolder()
    val backHandlers = remember { mutableMapOf<String, () -> Unit>() }
    val heights = remember { mutableStateMapOf<String, Int?>() }
    val density = LocalDensity.current
    val chrome = with(density) { (68.dp + if (page in listOf("members", "icons")) 76.dp else 0.dp).roundToPx() } + WindowInsets.navigationBars.getBottom(density)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val scope = rememberCoroutineScope()
    val gesture = remember { SheetBackGesture(scope, projectedFormBack = true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val entrance = remember { Animatable(0f) }
    val currentPage by rememberUpdatedState(page)
    val completed = remember(page) { gesture.committedTransition?.takeIf { it.second == page } }
    var previousPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) {
        if (previousPage != page && previousPage != "root") pageStates.removeState(previousPage)
        previousPage = page
        gesture.reset(); gesture.forgetTransition(); entrance.snapTo(0f)
        entrance.animateTo(1f, tween(450))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) gesture.reset() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); gesture.reset() }
    }
    fun back() { backHandlers[page]?.invoke() ?: if (page != "root") onBack() else onExit() }
    ConnectionModalSheet(onDismiss = onClosed, contentScrollState = scroll, avoidIme = true,
        sheetTag = "proxy_group_editor_sheet", closeRequested = closing, isContentAtTop = isContentAtTop,
        naturalContentHeight = heights[page]?.let { it + chrome }, floatingForm = true,
        handleHeight = 20.dp, handleWidth = 28.dp,
        onBackRequest = { if (!gesture.active) back(); true }, dismissAllowed = { onExit(); false }) {
        ProvideEditorWindowRegistry(registry) {
            Box(Modifier.fillMaxSize().clipToBounds().sheetBackGesture(page, if (page != "root") "root" else null,
                direction, gesture, { page !in listOf("root", "yaml") && !closing && entrance.value == 1f && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }, {
                    if (currentPage == page && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) back()
                    // 作者 long：图库取消可异步拒绝返回；只有页面确实切换才保留完成状态，否则恢复可交互的原页。
                    scope.launch { kotlinx.coroutines.delay(500); if (currentPage == page) gesture.reset() }
                }, thresholdPx = with(density) { 30.dp.toPx() })) {
                if (gesture.active && page != "root") pageStates.SaveableStateProvider("root-preview") {
                    CompositionLocalProvider(LocalEmbeddedProfilePage provides EmbeddedProfilePage(true, scrollForPage("root"), {}, {})) {
                        Box(Modifier.fillMaxSize().background(dashboardPageColor()).graphicsLayer {
                            translationX = -size.width * direction * .25f * (1f - gesture.progress); alpha = gesture.progress
                        }.blockSheetInput(true)) { content("root") }
                    }
                }
                AnimatedContent(page, modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = if (gesture.active) size.width * direction * gesture.progress else 0f
                }, transitionSpec = {
                    if (completed == (initialState to targetState)) (EnterTransition.None togetherWith ExitTransition.None).using(null)
                    else {
                        val sign = if (targetState == "root") -direction else direction
                        ((slideInHorizontally(tween(450, easing = SheetPageEasing)) { it * sign / 4 } + fadeIn(tween(180, delayMillis = 68))) togetherWith
                            (slideOutHorizontally(tween(450, easing = SheetPageEasing)) { -it * sign / 4 } + fadeOut(tween(135)))).using(null)
                    }
                }, label = "groupEditorPage") { shown ->
                    pageStates.SaveableStateProvider(shown) {
                        val active = shown == page && !closing
                        CompositionLocalProvider(LocalEmbeddedProfilePage provides EmbeddedProfilePage(shown == "root", scrollForPage(shown),
                            { if (active) backHandlers[shown] = it }, { if (active) heights[shown] = it })) {
                            Box(Modifier.fillMaxSize().background(dashboardPageColor()).blockSheetInput(!active || gesture.settling)
                                .testTag("group_sheet_page_$shown")) { content(shown) }
                        }
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
            Row(Modifier.align(Alignment.CenterEnd).background(dashboardCardColor(), AppSuperellipseShape(44.dp)), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(contentPadding)) {
            Box(Modifier.fillMaxWidth().weight(1f)) { text() }
            if (bottomBar != null) Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)) { bottomBar() }
        }
    }
}
