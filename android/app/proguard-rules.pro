
-keep class com.lonnnnnng.flclash.models.** { *; }

-keep class com.lonnnnnng.flclash.service.models.** { *; }

# 作者 long：网络设置通过 Gson 持久化，发布版必须保留字段名和默认构造器，避免混淆后重启丢失已确认的路由选择。
-keep class com.lonnnnnng.flclash.service.NetworkSettings { *; }

# 作者 long：Android 没有 java.beans 运行时；SnakeYAML 的通用 JavaBean introspector
# 只在非安全构造器路径中引用这些类，KClash 使用 SafeConstructor，不把该不可达路径当成运行时依赖。
-dontwarn java.beans.**

-keep class com.lonnnnnng.flclash.service.GeneralSettings { *; }
