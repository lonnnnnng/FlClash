package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection

// 作者 long：与 Flutter 手机 PageView 的 300ms easeOut 保持一致；跳过中间入口时仍只移动一屏，不构建沿途页面。
internal const val MAIN_PAGE_SLIDE_MILLIS = 300
internal val MainPageSlideEasing = CubicBezierEasing(0f, 0f, .58f, 1f)

@Composable
internal fun MainNavigationHost(
    destination: MainDestination,
    destinations: List<MainDestination>,
    onDestinationSelected: (MainDestination) -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (MainDestination) -> Unit,
) {
    // 作者 long：Dock 和消息属于整个应用，标题栏及正文属于各页；外层只占底栏，内页分别处理顶部安全区。
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = dashboardPageColor(),
        bottomBar = { MainNavigationDock(destination, destinations, onDestinationSelected) },
        snackbarHost = snackbarHost,
    ) { padding ->
        MainPageTransition(destination, Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), content)
    }
}

@Composable
internal fun MainPageTransition(
    destination: MainDestination,
    modifier: Modifier = Modifier,
    content: @Composable (MainDestination) -> Unit,
) {
    val savedPages = rememberSaveableStateHolder()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(
        targetState = destination,
        modifier = modifier.clipToBounds().testTag("main_page_transition"),
        transitionSpec = {
            val sign = if (targetState.ordinal > initialState.ordinal) direction else -direction
            (slideInHorizontally(tween(MAIN_PAGE_SLIDE_MILLIS, easing = MainPageSlideEasing)) { it * sign } togetherWith
                slideOutHorizontally(tween(MAIN_PAGE_SLIDE_MILLIS, easing = MainPageSlideEasing)) { -it * sign })
                .using(null)
        },
        label = "mainPage",
    ) { page ->
        val active = page == destination
        // 作者 long：退场页面只参与绘制，不能继续接收操作；返回时恢复该页的滚动、搜索和草稿视图。
        val interaction = if (active) Modifier else Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
        Box(Modifier.fillMaxSize().then(interaction)) {
            savedPages.SaveableStateProvider(page.name) { content(page) }
        }
    }
}
