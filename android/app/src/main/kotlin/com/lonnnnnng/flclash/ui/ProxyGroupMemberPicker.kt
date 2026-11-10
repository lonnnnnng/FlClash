package com.lonnnnnng.flclash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*

/** 作者 long：按固定Flutter成员选择页分类；打开前已有项不再提供，本轮勾选仍留在原位以便撤回。 */
@Composable
internal fun ProxyGroupMemberPicker(initial: ProxyGroupDraft, groups: List<ProxyGroupDraft>, source: String,
    onDismiss: () -> Unit, onChange: (ProxyGroupDraft) -> Unit) {
    val originalJson by rememberSaveable(initial.key) { mutableStateOf(initial.json) }
    // 作者 long：重建后撤回本轮选择必须回到完整入场草稿，不能混入父层已更新的显示布局。
    val originalLayout by rememberSaveable(initial.key) { mutableStateOf(initial.memberLayout.orEmpty()) }
    val origin = remember(initial.key, originalJson, originalLayout) {
        initial.copy(json = originalJson, memberLayout = originalLayout.takeIf { it.isNotEmpty() })
    }
    val presets = LocalFilterPresets.current
    val sections = remember(origin, groups, source, presets) { ProxyGroupMembers.sections(origin, groups, source, presets) }
    val choices = remember(sections) { sections.flatMap { it.choices }.associateBy { it.member.id } }
    var selected by rememberSaveable(initial.key) { mutableStateOf(emptyList<String>()) }
    var expanded by rememberSaveable(initial.key) { mutableStateOf(emptyList<String>()) }
    var query by rememberSaveable(initial.key) { mutableStateOf("") }
    val searching = query.isNotBlank()
    val keywords = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val matching = sections.map { section -> section.copy(choices = section.choices.filter { choice ->
        keywords.all { choice.title.contains(it, true) || choice.subtitle.orEmpty().contains(it, true) }
    }) }.filter { it.choices.isNotEmpty() }
    fun toggle(choice: GroupMemberChoice) {
        selected = if (choice.member.id in selected) selected - choice.member.id else selected + choice.member.id
        onChange(ProxyGroupMembers.select(origin, selected.mapNotNull { choices[it]?.member }))
    }
    ProfileEditorPage(onDismissRequest = onDismiss, backTag = "group_member_picker_back", title = { Text("添加节点") },
        bottomBar = { DockedProfileSearch(query, { query = it }, "group_member_search") },
        confirmButton = { if (LocalEmbeddedProfilePage.current == null) TextButton(onDismiss, Modifier.testTag("group_member_picker_done")) { Text("完成") } }, text = {
            val scroll = LocalEmbeddedProfilePage.current?.scroll ?: rememberScrollState()
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).testTag("group_member_categories")) {
                if (matching.isEmpty()) Text(if (searching) "无搜索结果" else "暂无可添加的节点", Modifier.padding(24.dp).testTag("group_member_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                matching.forEach { section -> key(section.id) {
                    val opened = searching || section.id in expanded
                    val rotation by animateFloatAsState(if (opened) 90f else 0f, tween(300, easing = SheetPageEasing))
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("group_member_category_${section.id}")
                        .clickable(enabled = !searching) { expanded = if (section.id in expanded) expanded - section.id else expanded + section.id }
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(" (${section.choices.size})", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (!searching) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp).rotate(rotation))
                    }
                    AnimatedVisibility(opened, enter = expandVertically(tween(300, easing = SheetPageEasing), expandFrom = Alignment.Top),
                        exit = shrinkVertically(tween(300, easing = SheetPageEasing), shrinkTowards = Alignment.Top)) {
                        ProfileFormSection(contentPadding = PaddingValues(0.dp), spacing = 0.dp) {
                            section.choices.forEachIndexed { index, choice ->
                                val picked = choice.member.id in selected
                                val color by animateColorAsState(if (picked) MaterialTheme.colorScheme.secondaryContainer else dashboardCardColor(), tween(180))
                                val tag = if (choice.member.kind in listOf(GroupMemberKind.PROXY, GroupMemberKind.PROVIDER))
                                    "group_member_choice_${choice.member.kind == GroupMemberKind.PROVIDER}_${choice.member.name}"
                                    else "group_member_choice_${choice.member.kind.name}_${choice.member.name}"
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                                Row(Modifier.fillMaxWidth().heightIn(min = if (choice.subtitle == null) 56.dp else 64.dp).testTag(tag)
                                    .background(color).clickable(enabled = opened) { toggle(choice) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(choice.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        choice.subtitle?.let { Text(it, Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    if (picked) Icon(Icons.Outlined.Check, null, Modifier.padding(start = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } }
                Spacer(Modifier.height(16.dp))
            }
        })
}
