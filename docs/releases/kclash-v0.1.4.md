# KClash 0.1.4

改进网络接管、长配置正文编辑和需要等待的操作反馈。版本检查仍由应用打开 GitHub 正式发布页，APK 下载交给浏览器。

## 更新内容

- 新安装和未保存过该选项的配置默认不允许应用绕过 VPN；保留用户显式选择及已有设置。
- 改进大配置正文的分段编辑、查找、跨段选区和撤销/重做。
- 订阅单条和批量导入保留表单并显示下载、解密、校验和逐条处理进度；缺少 age 密钥、失败重试和最终结果都有明确状态。
- YAML/age 文件导入显示读取、保存和完成状态；DNS、常规、Hosts、User-Agent 与局域网设置显示保存/应用反馈。
- 配置页其他等待操作和配置选择/管理显示忙态，减少操作提交后没有反馈的情况。

## 安装

- 安装包：`KClash-0.1.4-android-arm64-v8a.apk`，Android 7.0（API24）及以上，仅 ARM64。
- 包名：`com.lonnnnnng.flclash`；versionCode：`1004`。
- 沿用原正式发布签名，可覆盖此前 KClash 正式版。Flutter 原版独立安装。
- 随附 `SHA256SUMS`；签名证书 SHA-256：`3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`。

## 验证范围

911 项全模块 JVM 测试通过；Redmi Note 8 Pro / API 36 的新增等待反馈用例 5/5 通过。完整相关 UI 类回归中的六个旧断言失败和一个需要授权参数的跳过，保留在 [验收记录](../acceptance/async-feedback-redmi-20261112.json) 中。

低 API/其他 OEM、首次联网历史 TLS 超时归因和完整 Flutter 视觉仍按 roadmap 保留；本版不改变这些覆盖边界。备份/系统恢复、语言主题及观测次要打磨继续暂缓，玻璃默认关闭。

## 源码

标签：`kclash-v0.1.4`。内核固定 [`d43bbd751fe5eb95a599a6211423f645269ba57d`](https://github.com/lonnnnnng/Clash.Meta/tree/d43bbd751fe5eb95a599a6211423f645269ba57d)，仅跟随 `chen08209/Clash.Meta:FlClash` 同步。

详见 [roadmap](https://github.com/lonnnnnng/FlClash/blob/kotlin/docs/roadmap.md)。
