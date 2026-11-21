# KClash 0.1.13

- 网络切换时重置 Hysteria2、TUIC 与 WireGuard 的旧网络连接；VPN 建立前打开的核心 socket 会在 TUN 启动前交给系统保护。
- 资源页的新远程脚本导入与脚本改址统一使用 URL 输入浮框；远程图标集继续使用同行选择面板，并保留原始 URL 的复制、键盘提交和光标编辑语义。
- POCO F2 Pro / API 36 完成 Hysteria2 Wi-Fi 断开重连实测；WireGuard、TUIC 与蜂窝网络覆盖仍待补充。资源输入与自然转场定向验收通过，完整 Flutter 页面视觉和菜单性能归因仍在 roadmap 跟进。

适用于 Android 7.0（API 24）及以上的 ARM64 设备。安装包为 `KClash-0.1.13-android-arm64-v8a.apk`，versionCode 为 1013，沿用既有正式签名，可覆盖安装之前的 KClash 正式版。
