package com.lonnnnnng.flclash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreDnsQuery
import com.lonnnnnng.flclash.core.CoreEvent
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme
import com.lonnnnnng.flclash.ui.theme.recordWarningContainerColor
import com.lonnnnnng.flclash.ui.theme.recordOnWarningContainerColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 作者 long：四类记录共用 Flutter RecordListItem 的层级，避免列表和筛选弹窗逐渐产生不同的字体与间距。
private object RecordStyle {
    val shape = RoundedCornerShape(24.dp)
    val labelShape = RoundedCornerShape(8.dp)
    val primary = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
    val title = primary.copy(fontWeight = FontWeight.SemiBold)
    val secondary = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum")
}

@Composable
internal fun recordPageColor(): Color = if (LocalKClashDarkTheme.current) {
    MaterialTheme.colorScheme.surface
} else {
    MaterialTheme.colorScheme.surfaceContainer
}

@Composable
internal fun RecordSection(
    hasRecords: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 作者 long：非空列表让每条记录直接浮在页面上；停止/空态仍使用原来的提示卡，避免卡片重复套层。
    if (hasRecords) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    } else {
        Card(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun RecordCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    header: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().clip(RecordStyle.shape)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RecordStyle.shape,
        color = if (LocalKClashDarkTheme.current) colors.surfaceContainer else colors.surfaceBright,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            header()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = body)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordHeader(
    timestamp: String,
    trailing: String? = null,
    label: @Composable () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (timestamp.isNotBlank()) Text(timestamp, style = RecordStyle.caption, color = color, maxLines = 1)
            label()
        }
        if (trailing != null) Text(trailing, style = RecordStyle.caption, color = color)
    }
}

private enum class RecordTone { NEUTRAL, WARNING, ERROR }

@Composable
private fun RecordLabel(label: String, tone: RecordTone = RecordTone.NEUTRAL) {
    val colors = MaterialTheme.colorScheme
    val background = when (tone) {
        RecordTone.NEUTRAL -> if (LocalKClashDarkTheme.current) colors.surfaceContainerHigh else colors.surfaceContainer
        RecordTone.WARNING -> recordWarningContainerColor()
        RecordTone.ERROR -> colors.errorContainer
    }
    val foreground = when (tone) {
        RecordTone.NEUTRAL -> colors.onSurfaceVariant
        RecordTone.WARNING -> recordOnWarningContainerColor()
        RecordTone.ERROR -> colors.onErrorContainer
    }
    Surface(shape = RecordStyle.labelShape, color = background, contentColor = foreground) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = RecordStyle.caption.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordKeywordLabel(
    label: String,
    tone: RecordTone = RecordTone.NEUTRAL,
    onClick: (() -> Unit)?,
    testTag: String? = null,
) {
    if (onClick == null) {
        RecordLabel(label, tone)
    } else {
        val colors = MaterialTheme.colorScheme
        val background = when (tone) {
            RecordTone.NEUTRAL -> if (LocalKClashDarkTheme.current) colors.surfaceContainerHigh else colors.surfaceContainer
            RecordTone.WARNING -> recordWarningContainerColor()
            RecordTone.ERROR -> colors.errorContainer
        }
        val foreground = when (tone) {
            RecordTone.NEUTRAL -> colors.onSurfaceVariant
            RecordTone.WARNING -> recordOnWarningContainerColor()
            RecordTone.ERROR -> colors.onErrorContainer
        }
        Surface(
            modifier = Modifier
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
                .clip(RecordStyle.labelShape)
                .clickable(onClick = onClick),
            shape = RecordStyle.labelShape,
            color = background,
            contentColor = foreground,
        ) {
            Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = RecordStyle.caption.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConnectionRow(
    connection: CoreConnection,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickKeyword: ((String) -> Unit)? = null,
) {
    val metadata = connection.metadata
    val title = when {
        metadata.host.isNotBlank() -> connectionEndpoint(metadata.host, metadata.destinationPort)
        metadata.destinationIp.isNotBlank() -> connectionEndpoint(metadata.destinationIp, metadata.destinationPort)
        else -> metadata.remoteDestination.ifBlank { "未知目标" }
    }
    val colors = MaterialTheme.colorScheme
    val startedAt = remember(connection.start) { formatConnectionStart(connection.start) }
    RecordCard(
        modifier = modifier,
        onClick = onClick,
        header = {
            // 作者 long：Bridge 尚未提供单条连接的实时速率；头部只显示真实时间和网络，累计流量在正文明确标注。
            RecordHeader(startedAt) {
                if (metadata.network.isNotBlank()) {
                    Text(metadata.network.uppercase(Locale.ROOT), style = RecordStyle.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurfaceVariant)
                }
            }
        },
    ) {
        Text(
            buildAnnotatedString {
                append(title)
                if (metadata.host.isNotBlank() && metadata.destinationIp.isNotBlank()) {
                    withStyle(SpanStyle(color = colors.onSurfaceVariant, fontWeight = FontWeight.Normal, fontSize = 13.sp)) {
                        append("  ${metadata.destinationIp}")
                    }
                }
            },
            style = RecordStyle.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val rule = connectionRuleText(connection)
        if (rule.isNotBlank() || connection.chains.isNotEmpty()) {
            // 作者 long：反序代理链与 Flutter 相同；箭头和后继节点留在同一组，换行时不留下孤立箭头。
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                if (rule.isNotBlank()) Text(rule, style = RecordStyle.secondary, color = colors.onSurfaceVariant)
                connection.chains.asReversed().forEachIndexed { index, chain ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0 || rule.isNotBlank()) Text("→", style = RecordStyle.secondary, color = colors.onSurfaceVariant)
                        Box(Modifier.weight(1f, fill = false)) {
                            RecordKeywordLabel(chain, onClick = onClickKeyword?.let { callback -> ({ callback(chain) }) })
                        }
                    }
                }
            }
        }
        val source = listOf(
            connectionProcessText(metadata.process, metadata.uid),
            connectionEndpoint(metadata.sourceIp, metadata.sourcePort),
        ).filter { it.isNotBlank() }.joinToString("  ·  ")
        if (source.isNotBlank()) {
            Text(source, style = RecordStyle.secondary, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "累计 ↑ ${formatBytes(connection.upload)} · ↓ ${formatBytes(connection.download)}",
            style = RecordStyle.caption,
            color = colors.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DnsQueryRow(
    query: CoreDnsQuery,
    onClick: (() -> Unit)? = null,
    onClickKeyword: ((String) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val timestamp = remember(query.time) { formatConnectionStart(query.time) }
    val failureCode = query.rcode.isNotBlank() && !query.rcode.equals("NOERROR", ignoreCase = true)
    val failed = query.error.isNotBlank() || failureCode
    RecordCard(onClick = onClick, header = {
        RecordHeader(timestamp, trailing = "${query.delay} ms") {
            if (query.type.isNotBlank()) {
                RecordKeywordLabel(query.type, onClick = onClickKeyword?.let { callback -> ({ callback(query.type) }) })
            }
        }
    }) {
        Text(query.domain.ifBlank { "未知域名" }, style = RecordStyle.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val summary = query.error.ifBlank { query.answers.joinToString(", ") }
        if (summary.isNotBlank()) {
            Text(summary, style = RecordStyle.secondary, color = if (failed) colors.error else colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            if (query.initiator.isNotBlank()) {
                RecordKeywordLabel(query.initiator, onClick = onClickKeyword?.let { callback -> ({ callback(query.initiator) }) })
            }
            if (query.cached) RecordKeywordLabel("缓存", onClick = onClickKeyword?.let { callback -> ({ callback("缓存") }) })
            if (failureCode) RecordKeywordLabel(query.rcode, RecordTone.ERROR, onClickKeyword?.let { callback -> ({ callback(query.rcode) }) })
            if (query.error.isNotBlank() && !failureCode) RecordLabel("失败", RecordTone.ERROR)
            if (query.upstream.isNotBlank()) Text(query.upstream, style = RecordStyle.secondary, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LogRow(entry: CoreEvent.Log, onClickKeyword: ((String) -> Unit)? = null) {
    val timestamp = remember(entry.timestampMillis) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(entry.timestampMillis))
    }
    val level = entry.level.ifBlank { "info" }.lowercase(Locale.ROOT)
    val tone = when (level) {
        "warning", "warn" -> RecordTone.WARNING
        "error" -> RecordTone.ERROR
        else -> RecordTone.NEUTRAL
    }
    RecordCard(header = {
        RecordHeader(timestamp) {
            // 作者 long：Flutter 把日志级别作为关键词入口；仅可筛选的完整列表标签需要稳定测试标识。
            RecordKeywordLabel(
                label = level,
                tone = tone,
                onClick = onClickKeyword?.let { callback -> ({ callback(level) }) },
                testTag = onClickKeyword?.let { "log_level_$level" },
            )
        }
    }) {
        Text(entry.payload.ifBlank { "（空日志）" }, style = RecordStyle.primary, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
