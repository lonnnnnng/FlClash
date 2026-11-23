# KClash 0.1.18

修复开启 VPN 后检查更新因代理出口的 GitHub API 额度耗尽而失败的问题。现在只通过 GitHub 官方 `releases/latest` 网页跳转识别版本，不再调用 REST API，也不拉取更新说明。

工具页点击“应用更新”直接显示等待与结果弹框：没有新版提示“已是最新正式版本”；有新版点击“前往 GitHub 下载”打开浏览器。保留取消、失败重试和当前代理路线，应用不下载或安装 APK。

适用于 Android 7.0（API 24）及以上的 ARM64 设备。安装包为 `KClash-0.1.18-android-arm64-v8a.apk`，versionCode 为 1018，沿用既有正式签名，可覆盖安装之前的 KClash 正式版。
