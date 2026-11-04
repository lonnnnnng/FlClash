
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
