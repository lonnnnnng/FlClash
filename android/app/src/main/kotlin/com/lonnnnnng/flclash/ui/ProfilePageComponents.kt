package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity

/** 作者 long：配置编辑和扩展使用整页导航层级，让键盘、长表单和子编辑器共享完整手机视口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditorPage(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("profile_editor_page"),
            containerColor = dashboardPageColor(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = title,
                    navigationIcon = { IconButton(onClick = onDismissRequest) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                    } },
                    actions = { confirmButton() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = dashboardPageColor()),
                )
            },
        ) { padding ->
            // 作者 long：Scaffold 已计入安全区；消费后再补键盘差值，避免输入时把可用高度扣两次。
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 16.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth()) { text() }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) { dismissButton() }
            }
        }
    }
}

@Composable
internal fun ProfileFormSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = dashboardCardColor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
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
) {
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(anchor, margin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val target = anchor ?: anchorBounds
            val x = (target.right - popupContentSize.width).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
            val below = target.bottom + margin
            val y = if (below + popupContentSize.height <= windowSize.height - margin) below
                else (target.top - popupContentSize.height - margin).coerceAtLeast(margin)
            return IntOffset(x, y)
        }
    } }
    Popup(popupPositionProvider = position, onDismissRequest = { if (!busy) onDismiss() },
        properties = PopupProperties(focusable = true)) {
        Surface(Modifier.widthIn(min = 248.dp, max = 320.dp).testTag("profile_add_source_menu"),
            shape = RoundedCornerShape(24.dp), color = dashboardCardColor(), tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Column(Modifier.padding(vertical = 8.dp)) {
                DropdownMenuItem(text = { Text("从二维码图片导入") }, leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, null) },
                    onClick = onQr, enabled = !busy, modifier = Modifier.testTag("profile_add_source_qr"))
                DropdownMenuItem(text = { Text("从文件导入 YAML") }, leadingIcon = { Icon(Icons.Outlined.Description, null) },
                    onClick = onFile, enabled = !busy, modifier = Modifier.testTag("profile_add_source_file"))
                DropdownMenuItem(text = { Text("添加订阅链接") }, leadingIcon = { Icon(Icons.Outlined.Link, null) },
                    onClick = onRemote, enabled = !busy, modifier = Modifier.testTag("profile_add_source_remote"))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(text = { Text("创建自定义配置") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = onCustom, enabled = !busy, modifier = Modifier.testTag("profile_add_source_custom"))
            }
        }
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

/** 作者 long：扩展入口只导航到已有的 Profile 覆盖编辑器，不把缺失的标准/脚本模式伪装成已实现选项。 */
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
            Surface(shape = RoundedCornerShape(24.dp), color = dashboardCardColor()) {
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
