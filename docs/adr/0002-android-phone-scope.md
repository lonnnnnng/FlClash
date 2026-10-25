# Kotlin 主线的 Android 手机范围

状态：accepted

Kotlin 主线首发只面向 Android 手机，验收竖屏和横屏，并覆盖 Flutter Android 端已有的 VPN/TUN、代理服务、Profiles、订阅、代理组、连接、请求、DNS、日志、Quick Settings、Deep Link、按应用代理和电池优化能力。Android TV、平板、车机以及 Windows、macOS、Linux 的桌面能力暂不纳入首发范围。

这个边界让重写可以先保持手机用户的完整体验，同时避免把桌面窗口、托盘和其他设备形态的交互约束带入 Android 架构；未来扩展到其他 Android 形态时，需要单独评估布局和交互，而不是默认继承手机实现。
