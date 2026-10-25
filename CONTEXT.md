# FlClash Android 重写上下文

本上下文记录 FlClash 从 Flutter 多平台客户端向 Android 原生 Kotlin 主线演进时使用的项目语言。它描述产品边界和协作术语，不承载具体实现方案。

## 项目边界

**Kotlin 主线**：面向 Android 手机的 FlClash 原生实现，是本仓库未来持续开发和发布的主产品。

**Flutter 参考实现**：现有 Flutter 版本，作为 Android 功能、交互和视觉一致性的行为参考，并继续承接上游产品变化。

**上游**：`chen08209/FlClash`，提供 Flutter 参考实现及后续产品变化；上游变化需要经过 Android 适配后进入 Kotlin 主线。

**个人 fork**：`lonnnnnng/FlClash`，承载 Kotlin 主线的远程开发和发布分支。

**Android 手机范围**：Kotlin 主线首发面向 Android 手机的竖屏和横屏体验，包含 Flutter Android 端已有的网络代理和配置管理能力；Android TV、平板、车机和桌面平台不属于首发验收范围。

**Android 原生仓库**：Kotlin 主线的仓库根目录直接承载 Android Gradle 工程；Flutter 代码不作为 Kotlin 主线的运行时依赖，只通过上游分支提供行为参考。

**Mihomo 核心**：负责代理协议、路由、DNS、TUN、配置解析和运行时连接的 Go 引擎；Kotlin 主线复用它的 Android 构建产物，应用层通过 Repository/API 使用核心能力。

**新应用数据边界**：Kotlin 应用使用独立的数据目录和全新数据模型，不读取或转换 Flutter/Drift 数据；后续只维护 Kotlin 自身版本之间的升级迁移。

## 示例对话

开发者：上游新增了一个代理组能力，应该直接合并到 Kotlin 主线吗？

领域负责人：先在 Flutter 参考实现中确认 Android 用户能看到的行为，再把同一行为映射到 Kotlin 主线；桌面专属行为不自动进入 Android 产品。
