# KClash 0.1.0

首个 Kotlin Android 正式发布版本。应用采用 Kotlin / Jetpack Compose，复用 Mihomo 内核，面向 Android 手机；FlClash 继续作为功能与 UI/UX 参考。

## 下载与安装

- 下载 `KClash-0.1.0-android-arm64-v8a.apk`：Android 7.0（API 24）及以上、仅 ARM64。
- 应用包名 `com.lonnnnnng.flclash`，版本 `0.1.0`，versionCode `1000`。
- APK 使用独立 KClash 发布密钥签名；`SHA256SUMS` 用于校验下载文件。
- 可以与 Flutter 原版 `com.follow.clash` 并存，不自动迁移 Flutter 数据。
- 此前的 KClash Debug 包与正式包签名不同，不能直接覆盖安装。请先导出需要保留的配置，再卸载 Debug 版并安装正式版；卸载会清除其应用数据。后续正式版本沿用同一签名更新。

## 本版功能

- VPN/TUN 与本地代理模式，规则/全局/直连切换、策略组、节点选择及测速。
- 订阅、文件和自定义配置管理，YAML / 规则 / 代理 / 策略组 / Provider 编辑，脚本和资源管理。
- 编辑草稿恢复、提交防重放、系统文件选择器恢复，以及运行态配置与 Provider 应用。
- 首页速度与历史、配置快切、连接 / 请求 / DNS / 日志详情、IP 与服务检测。
- Android 快捷设置、通知操作、按应用代理及显式自动化 Action。
- CUSTOM 配置卡组数芯片及菜单阴影、动态轮廓的局部性能优化。
- 稳定网络/出口下保留国家代码与 IP，取消成功结果定时重查，无关路由变化先核验实际出口。
- 网速后台采样不再插入加载圈；缩小累计流量下方留白与趋势图高度，保持卡片尺寸稳定。

## 已知边界

- 这次发布不等于完整 Flutter 视觉与性能验收；菜单首开/关闭慢帧及部分子页仍在持续完善。玻璃效果默认关闭。
- API24/25、31/32、Wi-Fi↔蜂窝及其余 OEM 行为仍有待验项目。Tasker/MacroDroid 已有 Action 契约，第三方应用的完整调用仍待实测。
- 部分 IP/质量来源和真实加密在线订阅受网络、授权资源限制；备份/恢复、语言和主题的进一步完善暂缓。
- 不提供桌面版、Android TV 版或其他 ABI，也不内置代理节点或订阅服务。

## 发布验证

854 项 JVM 结果零失败、错误和跳过（含新增出口稳定性/路由核验及后台采样回归，部分任务复用已通过结果）；Debug、签名 Release/R8 及双 Lint 通过。Lint 均为 0 错误，现有警告仍保留；Go 父模块与 Mihomo route 包 race 检查通过。

发布前 Redmi 正式包回归发现并修复 SnakeYAML 被 R8 移入默认包导致的首次 YAML 导入崩溃（KREL-001，已保留溯源）。修复包通过真实系统选择器导入原始授权文件、首次 VPN 授权、双前台服务/tun0/双向流量、浏览器 HTTPS 页面、停止与冷进程配置保持，安装回拉字节一致。只代表本轮真机基本流程，未覆盖跨版本升级、全部规则/节点、蜂窝和所有 OEM。

完整进度与历史验收范围见 [roadmap](https://github.com/lonnnnnng/FlClash/blob/kclash-v0.1.0/docs/roadmap.md)。

## 签名与来源

证书 SHA-256：`3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`。

应用源码固定在 `kclash-v0.1.0` 标签；Mihomo 子模块固定在 [`7038231e026b710088b9c369899b3a8b7f281739`](https://github.com/lonnnnnng/Clash.Meta/tree/7038231e026b710088b9c369899b3a8b7f281739)。核心后续仅从 `chen08209/Clash.Meta:FlClash` 同步，保留本地缺陷修复。遵循 GPL-3.0。

2026-11-10 内核 fork 更名迁移，原发布标签保持不变；从旧标签构建时，先按[迁移说明](https://github.com/lonnnnnng/FlClash/blob/kotlin/docs/mihomo-fork-migration.md#historical-checkout)覆盖子模块 URL，再拉取原固定提交。
