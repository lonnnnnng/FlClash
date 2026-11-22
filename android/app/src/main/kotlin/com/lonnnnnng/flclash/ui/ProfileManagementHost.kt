package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile

/** 作者 long：业务会话由控制器持有，Compose只保留选区、滚动和放弃确认；弹窗和整页都使用同一独立密文保存树。 */
@Composable
internal fun ProfileManagementHost(controller: ProfileManagementController, profiles: List<Profile>, users: Map<Long, List<String>>) {
    val current by controller.state.collectAsState()
    var retainedRename by remember { mutableStateOf<ProfileManagementState?>(null) }
    var exitedSession by remember { mutableStateOf<String?>(null) }
    // 作者 long：保存回执先关闭业务会话，最后一帧只保留只读外观淡出，不能延迟提交或重放旧会话事件。
    SideEffect {
        if (current.draft?.kind == "rename") retainedRename = current
        else if (current.draft != null || retainedRename?.draft?.session == exitedSession) retainedRename = null
    }
    val state = if (current.draft == null && retainedRename?.draft?.session != exitedSession) retainedRename ?: current else current
    val externallyClosing = current.draft == null && state.draft != null
    if (state.recoveryFailed) {
        AlertDialog(onDismissRequest = {}, title = { Text(tr("配置管理草稿")) }, text = { Text(tr(state.error.orEmpty())) },
            confirmButton = { TextButton(controller::retryRecovery, enabled = !state.busy) { Text(tr("重试恢复")) } })
        return
    }
    val draft = state.draft ?: return
    EncryptedEditorState(draft.session, draft.uiDraft, { controller.ui(draft.session, it) }, controller::uiFailure) { checkpoint ->
        val canSave = !state.busy && !state.blocked && !draft.committed
        fun submit() { if (checkpoint()) controller.save() }
        when (draft.kind) {
            "rename" -> {
                // 作者 long：保存树只留选区，文字唯一来自业务草稿；两次异步落盘先后不一致也不能显示旧名称却提交新名称。
                // 作者 long：独立保存树恢复普通MutableState，不能声明成需要专用运行时类型的MutableIntState。
                @Suppress("AutoboxingStateCreation")
                var start by rememberSaveable(draft.session) { mutableStateOf(0) }
                @Suppress("AutoboxingStateCreation")
                var end by rememberSaveable(draft.session) { mutableStateOf(0) }
                var composition by remember(draft.session) { mutableStateOf<TextRange?>(null) }
                val value = TextFieldValue(draft.label, TextRange(start.coerceIn(0, draft.label.length), end.coerceIn(0, draft.label.length)), composition)
                FloatingInputDialog(
                    title = tr("重命名配置"), tag = "profile_rename_dialog", busy = state.busy,
                    confirmEnabled = canSave && draft.label.trim().isNotEmpty() && draft.label.trim() != draft.originalLabel,
                    onDismiss = controller::close, onConfirm = ::submit,
                    confirmLabel = if (state.busy) tr("正在保存") else tr("保存"),
                    cancelLabel = if (draft.committed) tr("关闭") else tr("取消"),
                    error = state.error, errorTag = "profile_management_error", confirmTag = "profile_rename_save",
                    externallyClosing = externallyClosing, dismissAfterAcknowledgement = true,
                    onExitComplete = { exitedSession = draft.session },
                ) { enabled, confirm ->
                    FloatingInputField(value, {
                        start = it.selection.start; end = it.selection.end; composition = it.composition
                        controller.label(draft.session, it.text.take(64))
                    }, "profile_rename_label", enabled && !draft.committed, confirm, placeholder = tr("名称"), invalid = state.error != null)
                }
            }
            "delete" -> EditorAlertDialog(
                onDismissRequest = controller::close,
                title = { Text(tr("删除配置")) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("确定删除“{0}”吗？本地配置文件也会一并删除。", draft.originalLabel))
                        users[draft.profileId]?.takeIf { it.isNotEmpty() }?.let {
                            Text(tr("以下配置仍引用此代理资源：{0}。删除后引用将显示缺失，可重新选择资源。", it.joinToString("、")), color = MaterialTheme.colorScheme.error)
                        }
                        state.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile_management_error")) }
                    }
                },
                confirmButton = { TextButton(::submit, enabled = canSave, modifier = Modifier.testTag("profile_delete_save")) {
                    Text(if (state.busy) tr("正在删除") else tr("删除"))
                } },
                dismissButton = { TextButton(controller::close, enabled = !state.busy) { Text(if (draft.committed) tr("关闭") else tr("取消")) } },
            )
            "order" -> {
                val byId = profiles.associateBy(Profile::id)
                val ordered = draft.order.mapNotNull(byId::get)
                var discard by rememberSaveable { mutableStateOf(false) }
                fun exit() {
                    if (state.busy) return
                    if (!draft.committed && draft.order != profiles.map(Profile::id)) discard = true else controller.close()
                }
                ProfileEditorPage(::exit, { Text(tr("排序配置")) }, {
                    Column(Modifier.fillMaxSize().testTag("profiles_order_page"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("长按拖动调整顺序，保存后生效。"))
                        state.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profiles_order_error")) }
                        if (ordered.isEmpty()) Text(tr("暂无可排序配置"))
                        ResourceOrderList(ordered.map { ResourceOrderItem(it.id.toString(), it.realLabel, it.type.name) },
                            !canSave, "profiles", { ids -> controller.order(draft.session, ids.map(String::toLong)) }, Modifier.weight(1f))
                    }
                }, confirmButton = {
                    TextButton(::submit, enabled = canSave && ordered.isNotEmpty(), modifier = Modifier.testTag("profiles_order_save")) {
                        Text(if (state.busy) tr("正在保存") else tr("保存排序"))
                    }
                }, dismissButton = { TextButton(::exit, enabled = !state.busy) { Text(if (draft.committed) tr("关闭") else tr("取消")) } }, backTag = "profiles_order_back")
                if (discard) EditorAlertDialog(onDismissRequest = { discard = false }, title = { Text(tr("放弃排序修改？")) },
                    confirmButton = { TextButton(controller::close, enabled = !state.busy) { Text(tr("放弃")) } },
                    dismissButton = { TextButton({ discard = false }, enabled = !state.busy) { Text(tr("继续排序")) } })
            }
        }
    }
}
