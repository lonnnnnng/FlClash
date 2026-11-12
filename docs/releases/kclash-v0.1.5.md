# KClash 0.1.5

当前 Kotlin 主线的维护发布，沿用 0.1.4 的产品功能与内核基线。

- 同步配置导入、设置反馈、全局规则拖拽和 age 仓库保护的回归用例与验收记录。
- 补充独立 UID 的真实 VPN 流量、启动期网络绑定以及系统未授权拒绝建立 TUN 后清理与恢复的验收记录。
- 上述增量主要是验收工具、测试和文档；热切 TUN 失败自动回滚、底层建接口异常及核心拒绝文件描述符等剩余分支仍在推进。

安装包：`KClash-0.1.5-android-arm64-v8a.apk`，Android 7.0（API24）及以上，仅 ARM64。包名 `com.lonnnnnng.flclash`，versionCode `1005`，沿用原正式签名，可覆盖此前 KClash 正式版。

按用户本次要求直接构建签名 Release 并发布，未重新运行单测、完整 Lint 或真机验收；Release 构建自带的 lintVital 随构建执行。此前各批次的测试结果仅代表对应历史验收，不作为本次新包复测结果。

随附 `SHA256SUMS`。签名证书 SHA-256：`3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`。

标签：`kclash-v0.1.5`。内核继续固定 [`d43bbd751fe5eb95a599a6211423f645269ba57d`](https://github.com/lonnnnnng/Clash.Meta/tree/d43bbd751fe5eb95a599a6211423f645269ba57d)，仅跟随 `chen08209/Clash.Meta:FlClash`。其余范围见 [roadmap](https://github.com/lonnnnnng/FlClash/blob/kotlin/docs/roadmap.md)。
