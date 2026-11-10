package com.lonnnnnng.flclash.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lonnnnnng.flclash.ui.theme.LocalKClashDarkTheme

/** 作者 long：Requests、DNS 与 Logs 共用 Flutter 页面级搜索和关键词栏，页面动作由对应列表提供。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObservationListPage(
    title: String,
    searchLabel: String,
    searchQuery: String,
    searchExpanded: Boolean,
    keywords: List<String>,
    onDismiss: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onClear: (() -> Unit)? = null,
    clearEnabled: Boolean = true,
    onExport: (() -> Unit)? = null,
    pageOverlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val toolbarSurfaceColor = if (LocalKClashDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }
    val searchFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(enabled = searchExpanded) {
            onSearchQueryChange("")
            onSearchExpandedChange(false)
        }
        Surface(
            modifier = Modifier.fillMaxSize().imePadding().testTag("observation_list_page"),
            color = recordPageColor(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        navigationIcon = {
                            if (!searchExpanded) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .testTag("observation_list_back")
                                        ,
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                                }
                            }
                        },
                        title = {
                            AnimatedContent(searchExpanded, transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(80))
                            }, label = "observationSearchTitle") { searching ->
                            if (searching) {
                                LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp).height(44.dp)
                                        .testTag("observation_search_capsule"),
                                    shape = CircleShape,
                                    color = toolbarSurfaceColor,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(
                                            start = 12.dp,
                                            end = if (searchQuery.isEmpty()) 12.dp else 0.dp,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp).testTag("observation_search_icon"),
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        val searchStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 16.sp,
                                            letterSpacing = 0.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = onSearchQueryChange,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                                .focusRequester(searchFocusRequester)
                                                .testTag("observation_search_field"),
                                            singleLine = true,
                                            textStyle = searchStyle,
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            decorationBox = { innerTextField ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (searchQuery.isEmpty()) {
                                                        Text(
                                                            searchLabel,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = searchStyle.copy(
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            ),
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            },
                                        )
                                        if (searchQuery.isNotEmpty()) {
                                            Box(
                                                Modifier.width(49.dp).fillMaxHeight(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                IconButton(
                                                    onClick = { onSearchQueryChange("") },
                                                    modifier = Modifier.size(44.dp)
                                                        .testTag("observation_search_clear")
                                                        ,
                                                ) {
                                                    Icon(Icons.Outlined.Close, contentDescription = null)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            }
                        },
                        actions = {
                            if (searchExpanded) {
                                // 作者 long：搜索占用整条导航栏时，保留 Flutter 对应的独立取消入口，避免 DNS 清理动作挤占输入区域。
                                IconButton(
                                    onClick = {
                                        onSearchQueryChange("")
                                        onSearchExpandedChange(false)
                                    },
                                    modifier = Modifier
                                        .background(toolbarSurfaceColor, CircleShape)
                                        .testTag("observation_search_toggle")
                                        ,
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = null)
                                }
                            } else {
                                IconButton(
                                    onClick = { onSearchExpandedChange(true) },
                                    modifier = Modifier
                                        .background(toolbarSurfaceColor, CircleShape)
                                        .testTag("observation_search_toggle")
                                        ,
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = null)
                                }
                                if (onClear != null) {
                                    IconButton(
                                        onClick = onClear,
                                        enabled = clearEnabled,
                                        modifier = Modifier
                                            .background(toolbarSurfaceColor, CircleShape)
                                            .testTag("observation_clear")
                                            ,
                                    ) {
                                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                    }
                                }
                                if (onExport != null) {
                                    IconButton(
                                        onClick = onExport,
                                        modifier = Modifier
                                            .background(toolbarSurfaceColor, CircleShape)
                                            .testTag("observation_export")
                                            ,
                                    ) {
                                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = recordPageColor()),
                    )
                    if (keywords.isNotEmpty()) {
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(keywords, key = { it }) { keyword ->
                                    Surface(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .widthIn(max = maxWidth - 32.dp)
                                            .testTag("observation_keyword_$keyword")
                                            .clickable { onRemoveKeyword(keyword) },
                                        shape = CircleShape,
                                        color = if (LocalKClashDarkTheme.current) {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceBright
                                        },
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 16.dp, end = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                keyword,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 15.sp,
                                                    lineHeight = 20.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.sp,
                                                ),
                                            )
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    content()
                }
                // 作者 long：全屏 Dialog 会盖住主页面 Scaffold 的 Snackbar，因此列表结果提示必须挂在当前窗口。
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    pageOverlay()
                }
            }
        }
    }
}
