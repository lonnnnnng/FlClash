# KClash 0.1.20

此版本减少配置来源菜单打开订阅表单时的重复标题组合与测量开销，并保留原有标题宽度、表单布局和键盘交互。

POCO F2 Pro / Android 16 的两份 Perfetto trace 中，首次表单测量切片由 31.838ms 降至 25.331ms 和 27.429ms。该结果仅代表这条表单路径的定向优化，不代表所有弹框或全局掉帧均已解决。

适用于 Android 7.0（API 24）及以上的 ARM64 设备。安装包为 `KClash-0.1.20-android-arm64-v8a.apk`，versionCode 为 1020，沿用既有正式签名，可覆盖安装之前的 KClash 正式版。
