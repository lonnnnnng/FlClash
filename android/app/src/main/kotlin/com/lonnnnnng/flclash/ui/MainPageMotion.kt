package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics

// 作者 long：Flutter 当前默认选项卡效果是220ms淡入淡出，easeInOut过程中页面不横移。
internal const val MAIN_PAGE_FADE_MILLIS = 220
internal val MainPageFadeEasing = CubicBezierEasing(.42f, 0f, .58f, 1f)
// 作者 long：代理组内部仍沿用300ms easeOut横向分页；不要与底部主导航的默认淡入混为一套参数。
internal const val MAIN_PAGE_SLIDE_MILLIS = 300
internal val MainPageSlideEasing = CubicBezierEasing(0f, 0f, .58f, 1f)
internal val LocalMainDockInset = staticCompositionLocalOf { 0.dp }

@Composable
internal fun MainNavigationHost(
    destination: MainDestination,
    destinations: List<MainDestination>,
    onDestinationSelected: (MainDestination) -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (MainDestination) -> Unit,
) {
    val savedPages = rememberSaveableStateHolder()
    val glass = LocalAppMenuGlass.current
    val inset = (62 + 10f * (androidx.compose.ui.platform.LocalDensity.current.fontScale - 1f)).dp +
        maxOf(21.dp, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()) + 8.dp
    val padding = PaddingValues(bottom = if (glass) 0.dp else inset)
    // 作者 long：两种材质共用同一页面组合；分支切换时新registry早于旧页dispose保存，单独共享holder仍会丢草稿。
    AppLiveMaterialHost(Modifier.fillMaxSize().background(dashboardPageColor()), background = {
        CompositionLocalProvider(LocalMainDockInset provides if (glass) inset else 0.dp) {
            MainPageTransition(destination, Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), savedPages, content)
        }
    }, foreground = {
        Box(Modifier.align(Alignment.BottomCenter)) { MainNavigationDock(destination, destinations, onDestinationSelected) }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = inset)) { snackbarHost() }
    })
}

@Composable
internal fun MainPageTransition(
    destination: MainDestination,
    modifier: Modifier = Modifier,
    savedPages: androidx.compose.runtime.saveable.SaveableStateHolder = rememberSaveableStateHolder(),
    content: @Composable (MainDestination) -> Unit,
) {
    AnimatedContent(
        targetState = destination,
        modifier = modifier.clipToBounds().testTag("main_page_transition"),
        transitionSpec = {
            (fadeIn(tween(MAIN_PAGE_FADE_MILLIS, easing = MainPageFadeEasing)) togetherWith
                fadeOut(tween(MAIN_PAGE_FADE_MILLIS, easing = MainPageFadeEasing)))
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
