# KClash 使用独立 Android 应用身份

状态：accepted

Kotlin 主线发布为显示名称 `KClash`，使用新的 `applicationId` 和 namespace `com.lonnnnnng.flclash`，不再使用 Flutter FlClash 的 `com.follow.clash`。Deep Link、Quick Settings、权限名、Service 组件和 gomobile 生成包名都以新身份为准。

独立包名让 KClash 可以与 Flutter FlClash 并行安装，避免 Android 将新应用误判为旧应用升级并带入旧数据或权限状态；代价是需要重新配置签名、系统入口和后续商店发布身份。
