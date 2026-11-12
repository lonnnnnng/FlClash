<div align="center">

<img src="assets/images/icon.png" alt="KClash" width="80">

# KClash

基于 Kotlin、Jetpack Compose 与 Mihomo 内核构建的原生 Android 代理客户端。

[English](README.md) · **简体中文**

[![Android](https://img.shields.io/badge/platform-Android-3ddc84?style=flat-square&logo=android&logoColor=white)](https://github.com/lonnnnnng/FlClash)
[![Release](https://img.shields.io/github/v/release/lonnnnnng/FlClash?filter=kclash-v*&style=flat-square&label=版本)](https://github.com/lonnnnnng/FlClash/releases)
[![License](https://img.shields.io/github/license/lonnnnnng/FlClash?style=flat-square)](LICENSE)

[下载](https://github.com/lonnnnnng/FlClash/releases/latest) · [从源码构建](#从源码构建) · [反馈问题](https://github.com/lonnnnnng/FlClash/issues)

</div>

KClash 是本仓库的 Android Kotlin 主线，将 [FlClash](https://github.com/chen08209/FlClash) 的 Android 体验重写为原生应用。Flutter 工程仅作为功能行为和 UI/UX 参考，不属于 Kotlin 版本的运行时。

## 功能

- **Mihomo 代理内核**：规则、全局和直连模式，策略组、节点选择与延迟测试。
- **配置管理**：支持订阅链接、YAML 文件和自定义配置，可编辑规则、代理、策略组、Providers 及已接入的配置分区。
- **流量与观测**：实时上传/下载速度和历史、连接、请求、DNS、核心日志及请求链路详情。
- **Android VPN 与代理控制**：VPN/TUN、本地代理模式、按应用代理、快捷设置磁贴和显式自动化 Action。
- **配置资源与备份**：本地资源、脚本、文件导入/导出，以及用户主动触发的本地或 WebDAV 备份/恢复。
- **无广告**，以 GPL-3.0 协议发布。

## 平台与状态

| 项目 | 支持范围 |
| --- | --- |
| 平台 | Android 手机 |
| 最低 Android 版本 | Android 7.0（API 24） |
| 发布 ABI | 仅 `arm64-v8a` |
| Application ID | `com.lonnnnnng.flclash` |
| 桌面端、Android TV 和其他 ABI | 不属于当前 Kotlin 版本 |

Kotlin 版本仍在持续开发。配置、代理、VPN 和观测主流程已有自动化测试及 Redmi Note 8 Pro 真机覆盖；完整 Flutter 视觉一致性、不同设备/ROM 的性能归因、低 API 覆盖以及部分系统/OEM 场景仍在推进中。范围和验收证据见 [`docs/roadmap.md`](docs/roadmap.md)。固定夹具测试通过不代表所有设备或生产网络均已验证。

## 下载与安装

从 [GitHub Releases](https://github.com/lonnnnnng/FlClash/releases/latest) 下载签名安装包 `KClash-0.1.5-android-arm64-v8a.apk`。当前版本适用于 ARM64 Android 设备。Android 可能要求允许当前浏览器或文件管理器安装应用。

在工具→应用更新中检查最新版本；发现新版后，点击跳转 GitHub 发布页，由浏览器下载。

KClash 使用独立的 Android Application ID 和签名身份，会与上游 Flutter 应用并行安装，也不会迁移 Flutter/Drift 的应用数据。升级 KClash 时必须继续使用同一发布签名身份，Android 才能验证更新。

已有的 KClash Debug 版本使用不同证书，不能用正式 APK 直接覆盖。请先导出需要保留的配置再卸载；卸载会清除应用数据。后续正式版本可以沿用该签名升级。

安装后可从订阅链接或 YAML 文件导入配置。启动 VPN 模式需要通过 Android 系统授权；代理节点和订阅服务由用户自行提供，本项目不包含代理节点或订阅访问权限。

## Android 自动化

Tasker、MacroDroid 等 Android 自动化应用可以通过以下 Action 启动 KClash Activity：

```text
com.lonnnnnng.flclash.action.START
com.lonnnnnng.flclash.action.STOP
com.lonnnnnng.flclash.action.TOGGLE
```

也可以从已连接的设备通过 ADB 触发：

```bash
adb -s <设备序列号> shell am start -a com.lonnnnnng.flclash.action.TOGGLE
```

应用只接受这三个显式 Action；启动 VPN 仍遵循 Android 系统授权流程。

## 从源码构建

Kotlin Android 构建使用 Java 17、Go 1.27.1、Android SDK Platform 37 和 NDK `28.2.13676358`。克隆 Kotlin 分支及 Mihomo 子模块：

```bash
git clone --branch kotlin --recurse-submodules https://github.com/lonnnnnng/FlClash.git
cd FlClash
./gradlew :app:assembleDebug
```

内核 fork 于 2026-11-10 迁至 `lonnnnnng/Clash.Meta`。已有检出更新到 Kotlin 主线后，先执行 `git submodule sync --recursive` 再更新子模块；检出旧标签 `kclash-v0.1.0`、`kclash-v0.1.1` 时请按[旧版本检出说明](docs/mihomo-fork-migration.md#historical-checkout)设置新地址，原固定核心提交仍保留。

Debug APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。运行 JVM 测试和检查：

```bash
./gradlew test :app:lintDebug
```

构建未签名、已启用混淆的 arm64 Release：

```bash
./gradlew :app:assembleRelease :app:lintRelease \
  -Ptarget-platform=android-arm64 \
  -PkclashVersionName=0.1.2 \
  -PkclashVersionCode=1002
```

未签名 APK 输出到 `android/app/build/outputs/apk/release/app-release-unsigned.apk`。正式发布由 GitHub Actions 使用项目独立的 Android 签名密钥签名；不要将签名密钥库或密码提交到 Git。

## 项目边界

- Kotlin Android 应用在 `kotlin` 分支开发。
- Flutter [FlClash 仓库](https://github.com/chen08209/FlClash)只作功能行为与视觉交互参考，不将其应用提交合入 Kotlin 主线。
- Mihomo 改动仅通过 `chen08209/Clash.Meta` 的 FlClash 分支及配置的 `lonnnnnng/Clash.Meta` fork / `kclash-fixes` 分支同步。

## 许可证与反馈

KClash 按 [GNU GPL v3.0](LICENSE) 发布。请通过 [GitHub Issues](https://github.com/lonnnnnng/FlClash/issues) 提交可复现的问题或功能建议。请提供应用版本、Android 版本和设备型号；分享日志前先删除订阅地址、凭据和私人配置正文。
