
-keep class com.lonnnnnng.flclash.models.** { *; }

-keep class com.lonnnnnng.flclash.service.models.** { *; }

# 作者 long：网络设置通过 Gson 持久化，发布版必须保留字段名和默认构造器，避免混淆后重启丢失已确认的路由选择。
-keep class com.lonnnnnng.flclash.service.NetworkSettings { *; }

# 作者 long：Android 没有 java.beans 运行时；SnakeYAML 的通用 JavaBean introspector
# 只在非安全构造器路径中引用这些类，KClash 使用 SafeConstructor，不把该不可达路径当成运行时依赖。
-dontwarn java.beans.**

-keep class com.lonnnnnng.flclash.service.GeneralSettings { *; }

# 作者 long：加密草稿的JSON字段跨进程/发布版读取，不能由R8重命名其快照字段。
-keep class com.lonnnnnng.flclash.ui.ProfileEditorDraftSnapshot { *; }
-keep class com.lonnnnnng.flclash.ui.ProfileRulesEditorUiState { *; }
-keep class com.lonnnnnng.flclash.ui.ProfileProxiesEditorUiState { *; }
-keep class com.lonnnnnng.flclash.ui.LocalProxyDraftSnapshot { *; }
-keep class com.lonnnnnng.flclash.ui.LocalProxyPendingWrite { *; }
-keep class com.lonnnnnng.flclash.ui.LocalProxiesUiState { *; }
-keep class com.lonnnnnng.flclash.ui.LocalProxyDraft { *; }
-keep class com.lonnnnnng.flclash.ui.LocalProxyCollectionDraft { *; }
-keep class com.lonnnnnng.flclash.ui.ProxyLinksDraft { *; }
-keep class com.lonnnnnng.flclash.data.LocalProxy { *; }
-keep class com.lonnnnnng.flclash.data.ProxyField { *; }
-keep enum com.lonnnnnng.flclash.data.ProxyFieldKind { *; }
-keep class kotlin.Pair { *; }
-keep class com.lonnnnnng.flclash.ui.ProfileProxyGroupsEditorUiState { *; }
-keep class com.lonnnnnng.flclash.ui.EditorSaveableState { *; }
-keep class com.lonnnnnng.flclash.ui.EditorSavedValue { *; }
# 作者 long：加密列表草稿按已核对的索引包装类型恢复；保留这个内部类，避免R8改名或折叠其索引字段。
-keep class androidx.compose.foundation.lazy.layout.DefaultLazyKey { *; }
-keep class com.lonnnnnng.flclash.data.CustomRuleCatalog { *; }
-keep class com.lonnnnnng.flclash.ui.ProfileEditorUiState { *; }
-keep class com.lonnnnnng.flclash.ui.RemoteProfileEditorUiState { *; }
-keep class com.lonnnnnng.flclash.ui.RemoteProfileDraft { *; }
-keep class com.lonnnnnng.flclash.ui.ProfileDocumentEditorState { *; }
-keep class com.lonnnnnng.flclash.service.YamlErrorLocation { *; }
-keep class com.lonnnnnng.flclash.ui.ProfileFileReplacementPreview { *; }
-keep class com.lonnnnnng.flclash.data.ProfileDocumentSnapshot { *; }
-keep class com.lonnnnnng.flclash.data.Profile { *; }
-keep enum com.lonnnnnng.flclash.data.ProfileType { *; }
-keep class kotlin.Triple { *; }
-keep class com.lonnnnnng.flclash.ui.ProviderPickerSnapshot { *; }
-keep class com.lonnnnnng.flclash.ui.ProviderDocumentDraft { *; }
