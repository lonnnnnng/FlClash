# Kotlin 主线复用 Go Mihomo 核心

状态：accepted

Kotlin 主线继续使用现有 Go mihomo 核心及其 gomobile Android 构建链，保留代理协议、路由、DNS、TUN、配置解析和运行时连接的既有行为；Kotlin 只重写 Android 应用层，并通过 Repository/API 隔离 gomobile 生成类。这样可以把功能一致性的风险集中在 UI、状态和平台集成，而不重复实现网络内核。

代价是 Kotlin 构建仍依赖 Go、Android NDK 和 AAR 生成流程，且上游 mihomo 变化需要同步检查 Kotlin API 适配。
