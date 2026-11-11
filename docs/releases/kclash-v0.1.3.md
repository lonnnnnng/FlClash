# KClash 0.1.3

应用更新简化为版本检查：显示当前版本，有新版时打开 GitHub 发布页下载。本版同时补齐配置导入前的核心语义检查，并改进组编辑和按应用代理名单保护。

## 更新内容

- 工具→应用更新只检查本仓库的 Kotlin 稳定版，比较当前与最新版本，展示更新说明；发现新版可前往 GitHub 下载。
- 删除应用内 APK 下载、断点续传、安装会话、后台下载任务及安装权限。
- FILE、URL、CUSTOM 整份配置导入和正文替换使用独立 UID 核心预检，提前定位节点、策略组、规则、DNS、TLS 等语义错误，失败保留原配置和未提交草稿。
- 补齐运行中导入守卫、CUSTOM 最终投影和资源版本竞态保护，修正规则目标及 Dialer 引用遗漏。
- 改进组编辑成员分类、混排、拖拽、浮动面板和冷进程草稿恢复。
- 按应用代理保留暂时隐藏或未安装的名单项，支持选择无 Launcher 的应用，防止无效导入覆盖已有名单。
- 修正内核坏证书、危险传输参数、双栈空结果错误和 VLESS 附加头截断吞错，保留诊断与源码溯源记录。

## 安装

- 安装包：`KClash-0.1.3-android-arm64-v8a.apk`，Android 7.0（API24）及以上，仅 ARM64。
- 包名：`com.lonnnnnng.flclash`；versionCode：`1003`。
- 沿用原正式发布签名，可覆盖此前 KClash 正式版。Flutter 原版独立安装。
- 随附 `SHA256SUMS`；签名证书 SHA-256：`3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`。

## 验证范围

配置语义边界已在 Redmi Note 8 Pro 完成 18/18 定向验收；90 个外部规则资源正文通过首轮 89/90 与单项补验覆盖，分批交给隔离核心解析。新授权节点各自独立启动并完成 18/18 HTTPS 请求；这些成功不追认历史 TLS 失败原因。

完整 Flutter 视觉、菜单慢帧、低 API/其他 OEM 和其余协议实流仍按 roadmap 推进；本轮验收进程绑定 TUN 网络的 EPERM 边界保留。备份/系统恢复、语言主题及观测次要打磨继续暂缓，玻璃默认关闭。

## 源码

标签：`kclash-v0.1.3`。内核固定 [`d43bbd751fe5eb95a599a6211423f645269ba57d`](https://github.com/lonnnnnng/Clash.Meta/tree/d43bbd751fe5eb95a599a6211423f645269ba57d)，仅跟随 `chen08209/Clash.Meta:FlClash` 同步。

详见 [roadmap](https://github.com/lonnnnnng/FlClash/blob/kotlin/docs/roadmap.md)。
