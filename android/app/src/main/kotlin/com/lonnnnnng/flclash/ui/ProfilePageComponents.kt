package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.lonnnnnng.flclash.R

/** 作者 long：配置编辑和扩展使用整页导航层级，让键盘、长表单和子编辑器共享完整手机视口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditorPage(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    backTag: String = "profile_editor_back",
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val entrance = remember { Animatable(0f) }
        LaunchedEffect(Unit) { entrance.animateTo(1f, tween(450, easing = LinearEasing)) }
        SideEffect { window?.setWindowAnimations(R.style.KClash_ProfilePageAnimation) }
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        DisposableEffect(window, lightBars) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val status = controller?.isAppearanceLightStatusBars
            val navigation = controller?.isAppearanceLightNavigationBars
            // 作者 long：整页 Dialog 有独立 Window，主题切换必须同步它自己的系统栏，否则浅色编辑页的白色状态图标不可读。
            controller?.isAppearanceLightStatusBars = lightBars
            controller?.isAppearanceLightNavigationBars = lightBars
            onDispose {
                if (status != null) controller.isAppearanceLightStatusBars = status
                if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // 作者 long：沿Flutter整页共享X轴的四分之一页宽进入，保留原页面状态；Compose时钟遵循系统动画缩放，不延迟返回确认或保存回调。
                translationX = size.width * .25f * (1f - FastOutSlowInEasing.transform(entrance.value))
                val fade = ((entrance.value - .15f) / .4f).coerceIn(0f, 1f)
                alpha = 1f - (1f - fade) * (1f - fade) * (1f - fade)
            }.testTag("profile_editor_page"),
            containerColor = dashboardPageColor(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = { IconButton(onClick = onDismissRequest, modifier = Modifier.testTag(backTag)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                    } },
                    actions = { confirmButton() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = dashboardPageColor()),
                )
            },
        ) { padding ->
            // 作者 long：Scaffold 已计入安全区；消费后再补键盘差值，避免输入时把可用高度扣两次。
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(contentPadding)) {
                Box(Modifier.weight(1f).fillMaxWidth()) { text() }
                if (dismissButton != null) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) { dismissButton() }
            }
        }
    }
}

@Composable
internal fun ProfileFormSection(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** 作者 long：结构化选项沿用Flutter同行标签/值，标签最多占半行，右侧输入留给真实内容；分组间距不混入行内。 */
@Composable
internal fun ProfileFormRow(title: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val titleLimit = maxWidth / 2
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Text(title, maxLines = 2, style = MaterialTheme.typography.bodyLarge,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = titleLimit))
        Spacer(Modifier.width(16.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, content = content)
    }
    }
}

/** 作者 long：添加来源跟随实际点击控件定位；空态按钮和顶栏按钮复用同一菜单，不覆盖整页。 */
@Composable
internal fun ProfileSourceMenu(
    anchor: IntRect?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onQr: () -> Unit,
    onFile: () -> Unit,
    onRemote: () -> Unit,
    onCustom: () -> Unit,
    source: AppMenuSource? = null,
) {
    AppDropdownMenu(expanded = true, onDismissRequest = onDismiss, anchor = anchor, source = source,
        modifier = Modifier.testTag("profile_add_source_menu")) {
        AppDropdownMenuItem(text = { Text("扫描二维码导入") }, leadingIcon = { AppGlyphIcon(AppGlyphs.qrCode, null) },
            onClick = onQr, enabled = !busy, modifier = Modifier.testTag("profile_add_source_qr"))
        AppDropdownMenuItem(text = { Text("从文件导入 YAML") }, leadingIcon = { AppGlyphIcon(AppGlyphs.profiles, null) },
            onClick = onFile, enabled = !busy, modifier = Modifier.testTag("profile_add_source_file"))
        AppDropdownMenuItem(text = { Text("添加订阅链接") }, leadingIcon = { AppGlyphIcon(AppGlyphs.link, null) },
            onClick = onRemote, enabled = !busy, modifier = Modifier.testTag("profile_add_source_remote"))
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f))
        AppDropdownMenuItem(text = { Text("创建自定义配置") }, leadingIcon = { AppGlyphIcon(AppGlyphs.edit, null) },
            onClick = onCustom, enabled = !busy, modifier = Modifier.testTag("profile_add_source_custom"))
    }
}

@Composable
internal fun ProfileSectionRow(
    title: String,
    detail: String? = null,
    icon: ImageVector,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 作者 long：独立列表宿主可只提供覆盖导航回调；正式入口通过 ProfileExtensionRoute 挂载持久化扩展页。 */
@Composable
internal fun ProfileExtensionPage(
    label: String,
    canMutate: Boolean,
    onDismiss: () -> Unit,
    onDns: () -> Unit,
    onProviders: () -> Unit,
    onScript: () -> Unit,
) {
    ProfileEditorPage(onDismissRequest = onDismiss, title = { Text("扩展") }, text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            Surface(shape = AppSuperellipseShape(24.dp), color = dashboardCardColor()) {
                Column {
                    ProfileSectionRow("DNS", "覆盖当前配置的 DNS 设置", Icons.Outlined.Dns, canMutate,
                        Modifier.testTag("profile_extension_dns"), onDns)
                    ProfileSectionRow("Provider", "代理和规则资源", Icons.Outlined.Cloud, canMutate,
                        Modifier.testTag("profile_extension_providers"), onProviders)
                    ProfileSectionRow("脚本", "选择绑定的 JavaScript", Icons.Outlined.Code, canMutate,
                        Modifier.testTag("profile_extension_script"), onScript)
                }
            }
        }
    })
}
