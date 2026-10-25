# KClash 只发布 arm64-v8a

状态：accepted

KClash 的开发、测试和发布统一只构建 `arm64-v8a`，不再维护 `armeabi-v7a`、`x86_64` 或其他 ABI。Go Mihomo 的 gomobile 构建、APK/AAB 配置和 CI 验证都以 arm64-v8a 为唯一目标。

这个选择减少原生核心构建、测试矩阵和发布资产的复杂度，并保证开发与发布使用同一 ABI；代价是不能在 x86/x86_64 设备或模拟器上运行，验收必须使用 ARM64 真机或 ARM64 模拟器。
