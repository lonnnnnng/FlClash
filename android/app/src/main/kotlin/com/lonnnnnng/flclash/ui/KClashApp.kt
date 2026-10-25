package com.lonnnnnng.flclash.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.flclash.data.Profile
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.CoreRuntimeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KClashApp(
    runtimeViewModel: MainViewModel,
    profilesViewModel: ProfilesViewModel,
) {
    val runtimeState by runtimeViewModel.runtimeState.collectAsState()
    val profilesState by profilesViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var profilePendingDeletion by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(profilesViewModel) {
        profilesViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(runtimeViewModel) {
        runtimeViewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("KClash") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("添加配置") },
                icon = {},
                onClick = { showAddDialog = true },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RuntimeCard(runtimeState, runtimeViewModel::toggleRuntime) }
            item {
                Text(
                    text = "配置",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (profilesState.profiles.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("还没有配置")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "添加订阅或创建自定义配置后，配置会保存在 KClash 独立数据目录中。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { showAddDialog = true }) {
                                Text("添加配置")
                            }
                        }
                    }
                }
            } else {
                items(profilesState.profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        selected = profile.id == profilesState.currentProfileId,
                        busy = profilesState.isBusy,
                        onSelect = { profilesViewModel.select(profile.id) },
                        onUpdate = { profilesViewModel.update(profile) },
                        onDelete = { profilePendingDeletion = profile },
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            busy = profilesState.isBusy,
            onDismiss = { showAddDialog = false },
            onAddRemote = { label, url ->
                showAddDialog = false
                profilesViewModel.addRemote(url, label)
            },
            onAddCustom = { label ->
                showAddDialog = false
                profilesViewModel.addCustom(label)
            },
        )
    }

    profilePendingDeletion?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDeletion = null },
            title = { Text("删除配置") },
            text = { Text("确定删除“${profile.realLabel}”吗？本地配置文件也会一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        profilePendingDeletion = null
                        profilesViewModel.delete(profile)
                    },
                    enabled = !profilesState.isBusy,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDeletion = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RuntimeCard(state: CoreRuntimeStatus, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("核心状态", style = MaterialTheme.typography.labelLarge)
                Text(state.label, style = MaterialTheme.typography.titleMedium)
            }
            if (state == CoreRuntimeStatus.STARTING || state == CoreRuntimeStatus.STOPPING) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onToggle) {
                    Text(if (state.isRunning) "停止核心" else "启动核心")
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onSelect) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.realLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profileSubtitle(profile),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            profile.lastUpdateAt?.let { updatedAt ->
                Text(
                    text = "更新于 ${DateUtils.getRelativeTimeSpanString(updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (profile.type == ProfileType.URL) {
                    TextButton(onClick = onUpdate, enabled = !busy) { Text("同步") }
                }
                TextButton(onClick = onDelete, enabled = !busy) { Text("删除") }
            }
        }
    }
}

private fun profileSubtitle(profile: Profile): String {
    val info = profile.subscriptionInfo
    if (info?.usage != null) {
        val used = info.upload + info.download
        return "已用 ${formatBytes(used)} / ${formatBytes(info.total)}"
    }
    return when (profile.type) {
        ProfileType.URL -> "远程订阅"
        ProfileType.FILE -> "本地文件"
        ProfileType.CUSTOM -> "自定义配置"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "%.1f%s".format(value, units[index])
}

@Composable
private fun AddProfileDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onAddRemote: (label: String, url: String) -> Unit,
    onAddCustom: (label: String) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var customMode by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (customMode) "创建自定义配置" else "添加订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名称（可选）") },
                    singleLine = true,
                    enabled = !busy,
                )
                if (!customMode) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("订阅链接") },
                        singleLine = true,
                        enabled = !busy,
                    )
                    OutlinedButton(onClick = { customMode = true }, enabled = !busy) {
                        Text("创建自定义配置")
                    }
                } else {
                    TextButton(onClick = { customMode = false }, enabled = !busy) {
                        Text("改为添加订阅")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (customMode) onAddCustom(label) else onAddRemote(label, url)
                },
                enabled = !busy && (customMode || url.isNotBlank()),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
