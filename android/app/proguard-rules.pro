
-keep class com.lonnnnnng.flclash.models.** { *; }

-keep class com.lonnnnnng.flclash.service.models.** { *; }

# 作者 long：Android 没有 java.beans 运行时；SnakeYAML 的通用 JavaBean introspector
# 只在非安全构造器路径中引用这些类，KClash 使用 SafeConstructor，不把该不可达路径当成运行时依赖。
-dontwarn java.beans.**
