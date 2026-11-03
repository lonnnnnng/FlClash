package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.ProxyProviderOverride
import com.lonnnnnng.flclash.service.YamlConfigCodec

/** 作者 long：资源页列实际引用；选项和绑定各自确认保存，避免父页关闭时误丢已经确认的编辑。 */
@Composable
internal fun ProfileProvidersPage(profile: Profile, proxySection: String, ruleSection: String, source: String,
    overrides: Map<String, ProxyProviderOverride>, profiles: List<Profile>, rules: List<RuleResource>,
    saving: Boolean, revision: Long, error: String?, onDismiss: () -> Unit,
    onSave: (String, String, Map<String, ProxyProviderOverride>) -> Unit, onManageRules: () -> Unit,
    validateFilters: suspend (List<String>) -> List<String>) {
    var editing by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var binding by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    var bindingProxy by rememberSaveable(profile.id) { mutableStateOf(true) }
    var quick by rememberSaveable(profile.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var submitted by rememberSaveable(profile.id) { mutableStateOf<Long?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val parsed = remember(profile, source, proxySection, ruleSection) { runCatching { ProviderCatalog.from(profile, source, proxySection, ruleSection) } }
    val catalog = parsed.getOrNull()
    val proxyDefs = remember(proxySection) { runCatching { ProfileProxyResources.definitions(proxySection) }.getOrNull() }
    val ruleDefs = remember(ruleSection) { runCatching { ProviderCatalog.ruleDefinitions(ruleSection) }.getOrNull() }
    fun submit(proxy: String, rule: String, fields: Map<String, ProxyProviderOverride>) {
        if (saving) return
        submitted = revision
        onSave(proxy, rule, fields)
    }
    LaunchedEffect(revision) {
        // 作者 long：只有仓库成功回读的新版本才能关闭子页；失败时保留当前选择和输入。
        if (submitted?.let { revision > it } == true) { editing = null; binding = null; quick = false; submitted = null }
    }
    ProfileEditorPage(onDismissRequest = { if (!saving) onDismiss() }, backTag = "profile_providers_back", title = { Text("资源") },
        confirmButton = { if (profile.type == ProfileType.CUSTOM) Box {
            IconButton(onClick = { menu = true }, enabled = !saving, modifier = Modifier.testTag("profile_providers_menu")) { Icon(Icons.Outlined.MoreVert, "资源操作") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("快捷 YAML") }, onClick = { menu = false; quick = true })
                DropdownMenuItem(text = { Text("管理规则资源") }, onClick = { menu = false; onManageRules() })
            }
        } }, text = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("profile_providers_page"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                (error?.takeIf { editing == null && binding == null && !quick }
                    ?: parsed.exceptionOrNull()?.let { "无法解析资源引用，请通过快捷 YAML 检查配置。" })?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (catalog != null && catalog.proxies.isEmpty() && catalog.rules.isEmpty()) Box(Modifier.heightIn(min = 240.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (profile.type == ProfileType.CUSTOM) "暂无引用的资源，请先在策略组或规则中选择资源。" else "这份配置没有 Provider。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!catalog?.proxies.isNullOrEmpty()) {
                    Text("代理", style = MaterialTheme.typography.titleSmall)
                    ProfileFormSection {
                        catalog!!.proxies.forEachIndexed { index, name ->
                            val definition = proxyDefs?.get(name)
                            val id = ProfileProxyResources.idOf(definition)
                            val resource = profiles.firstOrNull { it.id == id && it.type != ProfileType.CUSTOM }
                            val missing = definition?.isJsonObject != true || ProfileProxyResources.isReference(definition) && resource == null
                            ProviderResourceRow(name, if (missing) "资源缺失，请重新选择" else resource?.lastUpdateAt?.let { "更新于 " + DateUtils.getRelativeTimeSpanString(it) }?.toString(), missing,
                                "profile_provider_proxy_$name", onClick = { if (missing) info = "代理资源“$name”不可用，请重新选择订阅或文件配置。" else editing = name },
                                enabled = !saving, trailing = {
                                    if (missing) IconButton(onClick = { info = "代理资源“$name”不可用，请重新选择订阅或文件配置。" }) { Icon(Icons.Outlined.Info, "资源缺失", tint = MaterialTheme.colorScheme.error) }
                                    else IconButton(onClick = { editing = name }, enabled = !saving, modifier = Modifier.testTag("profile_provider_edit_$name")) { Icon(Icons.Outlined.Edit, "编辑代理资源选项") }
                                    if (profile.type == ProfileType.CUSTOM) IconButton(onClick = { bindingProxy = true; binding = name }, enabled = !saving,
                                        modifier = Modifier.testTag("profile_provider_bind_proxy_$name")) { Icon(Icons.Outlined.SwapHoriz, "选择代理资源") }
                                })
                            if (index < catalog.proxies.lastIndex) HorizontalDivider()
                        }
                    }
                }
                if (!catalog?.rules.isNullOrEmpty()) {
                    Text("规则", style = MaterialTheme.typography.titleSmall)
                    ProfileFormSection {
                        catalog!!.rules.forEachIndexed { index, name ->
                            val definition = ruleDefs?.get(name)
                            val id = RuleResources.idOf(definition)
                            val resource = rules.firstOrNull { it.id == id }
                            val missing = definition?.isJsonObject != true || id != null && resource == null
                            ProviderResourceRow(name, if (missing) "资源缺失，请重新选择" else resource?.updatedAt?.let { "更新于 " + DateUtils.getRelativeTimeSpanString(it) }?.toString(), missing,
                                "profile_provider_rule_$name", enabled = !saving,
                                onClick = if (missing) ({ info = "规则资源“$name”不可用，请重新选择规则资源。" }) else null, trailing = {
                                    if (missing) IconButton(onClick = { info = "规则资源“$name”不可用，请重新选择规则资源。" }) { Icon(Icons.Outlined.Info, "资源缺失", tint = MaterialTheme.colorScheme.error) }
                                    if (profile.type == ProfileType.CUSTOM) IconButton(onClick = { bindingProxy = false; binding = name }, enabled = !saving,
                                        modifier = Modifier.testTag("profile_provider_bind_rule_$name")) { Icon(Icons.Outlined.SwapHoriz, "选择规则资源") }
                                })
                            if (index < catalog.rules.lastIndex) HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        })
    editing?.let { name -> ProviderOptionsPage(name, overrides[name] ?: ProxyProviderOverride(), saving, error,
        onDismiss = { editing = null; submitted = null }, validateFilters = validateFilters,
        onSave = { next -> submit(proxySection, ruleSection, if (next.isEmpty) overrides - name else overrides + (name to next)) }) }
    binding?.let { alias -> ProviderBindingPicker(bindingProxy, profiles, rules, saving, error,
        onDismiss = { if (!saving) { binding = null; submitted = null } }, onProxy = { resource -> submit(ProfileProxyResources.bind(proxySection, resource, alias), ruleSection, overrides) },
        onRule = { resource -> submit(proxySection, RuleResources.bind(ruleSection, resource, alias), overrides) }) }
    if (quick) ProviderQuickYaml(proxySection, ruleSection, saving, error, onDismiss = { if (!saving) { quick = false; submitted = null } },
        onSave = { proxy, rule -> submit(proxy, rule, overrides.filterKeys(ProfileProxyResources.definitions(proxy).keySet()::contains)) })
    info?.let { message -> AlertDialog(onDismissRequest = { info = null }, title = { Text("资源不可用") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { info = null }) { Text("知道了") } }) }
}

@Composable
private fun ProviderResourceRow(name: String, subtitle: String?, missing: Boolean, tag: String, enabled: Boolean, onClick: (() -> Unit)?, trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).then(if (onClick != null) Modifier.clickable(enabled, onClick = onClick) else Modifier).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing()
    }
}

@Composable
private fun ProviderBindingPicker(proxy: Boolean, profiles: List<Profile>, rules: List<RuleResource>, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onProxy: (Profile) -> Unit, onRule: (RuleResource) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ProfileEditorPage(onDismissRequest = onDismiss, backTag = "provider_binding_back", title = { Text(if (proxy) "选择代理资源" else "选择规则资源") }, text = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(query, { query = it }, singleLine = true, enabled = !busy, label = { Text("搜索") }, modifier = Modifier.fillMaxWidth().testTag("provider_binding_search"))
            val proxyChoices = profiles.filter { it.type != ProfileType.CUSTOM && it.realLabel.contains(query, true) }
            val ruleChoices = rules.filter { it.label.contains(query, true) }
            if (if (proxy) proxyChoices.isEmpty() else ruleChoices.isEmpty()) Text(if (query.isNotEmpty()) "没有匹配的资源" else "暂无可选资源，请先在资源管理中添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f)) {
                if (proxy) items(proxyChoices, key = { it.id }) { resource -> ProfileSectionRow(resource.realLabel, if (resource.type == ProfileType.URL) "订阅配置" else "文件配置", Icons.Outlined.CloudQueue, !busy,
                    Modifier.testTag("provider_binding_proxy_${resource.id}")) { onProxy(resource) } }
                else items(ruleChoices, key = { it.id }) { resource -> ProfileSectionRow(resource.label, resource.behavior + " · " + resource.format, Icons.Outlined.Description, !busy,
                    Modifier.testTag("provider_binding_rule_${resource.id}")) { onRule(resource) } }
            }
        }
    })
}

@Composable
private fun ProviderQuickYaml(initialProxy: String, initialRule: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var proxy by rememberSaveable { mutableStateOf(initialProxy) }
    var rule by rememberSaveable { mutableStateOf(initialRule) }
    var failure by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val dirty = proxy != initialProxy || rule != initialRule
    val close = { if (!busy) { if (dirty) discard = true else onDismiss() } }
    ProfileEditorPage(onDismissRequest = close, backTag = "providers_yaml_back", title = { Text("快捷 YAML") }, confirmButton = {
        TextButton(onClick = {
            runCatching {
                fun definitions(key: String, text: String): com.google.gson.JsonObject {
                    val value = ProxyGroupDrafts.root("$key:\n" + text.lines().joinToString("\n") { "  $it" }).get(key)
                    require(value == null || value.isJsonNull && text.isBlank() || value.isJsonObject) { "Provider 分区必须是 map" }
                    return value?.takeIf { it.isJsonObject }?.asJsonObject ?: com.google.gson.JsonObject()
                }
                val proxies = definitions("proxy-providers", proxy)
                val rules = definitions("rule-providers", rule)
                require((proxies.entrySet() + rules.entrySet()).all { it.value.isJsonObject }) { "每个 Provider 必须是 map" }
                fun normalized(value: com.google.gson.JsonObject) = if (value.size() == 0) "" else YamlConfigCodec.toYaml(value.toString()).trimEnd()
                normalized(proxies) to normalized(rules)
            }.onSuccess { (p, r) -> failure = null; onSave(p, r) }.onFailure { failure = "Provider YAML 格式无效，请检查缩进和条目类型" }
        }, enabled = !busy, modifier = Modifier.testTag("providers_yaml_save")) { Text(if (busy) "保存中…" else "保存") }
    }, text = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (failure ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(proxy, { proxy = it; failure = null }, label = { Text("proxy-providers") }, minLines = 8, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("providers_yaml_proxy"))
            OutlinedTextField(rule, { rule = it; failure = null }, label = { Text("rule-providers") }, minLines = 8, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("providers_yaml_rule"))
        }
    })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃 YAML 修改？") }, confirmButton = { TextButton(onDismiss) { Text("放弃修改") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
