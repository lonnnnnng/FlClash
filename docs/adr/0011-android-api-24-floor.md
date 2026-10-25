# KClash 保持 Android API 24 支持下限

状态：accepted

KClash 的 `minSdk` 保持 API 24（Android 7.0），继续覆盖 Flutter Android 版本已经支持的手机范围；`targetSdk` 和 `compileSdk` 在根目录 Gradle 迁移时按已验证工具链和发布要求确定，不因单个 Compose API 轻易提高最低版本。

这样可以减少重写带来的设备范围回退，同时要求新 UI、VPN、前台服务和权限实现使用 AndroidX 或平台兼容分支处理 API 24 的差异。
