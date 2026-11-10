# KClash 0.1.2

修复停止后再次启动仍累加上一轮流量的问题，并完善资源选择、搜索与配置面板交互。可直接覆盖安装此前的 KClash 正式版，保留现有配置。

## 修复与改进

- 完整停止核心后，清除本次累计上传、下载与速度采样；再次启动按新会话累计。按需暂停、恢复和运行中应用配置继续保留本次统计。
- 代理和规则资源选择页首次打开时显示当前绑定项，长目录无需从头查找；搜索或手动滚动期间不反复跳回。
- 代理页搜索补齐连续展开与收回，取消时清空搜索并收起键盘，快速反向操作保持过渡连续。
- 配置资源、Provider、图标和 Sniffer 添加面板，以及代理组成员选择页统一下沿搜索，完善键盘避让与取消后的草稿保持。
- 编辑器、工具栏和选项面板补充搜索进退与连续状态过渡，包括 DNS 查询页。

## 下载与升级

- 安装包：`KClash-0.1.2-android-arm64-v8a.apk`，Android 7.0（API24）及以上，仅 ARM64。
- 包名 `com.lonnnnnng.flclash`，版本 `0.1.2`，versionCode `1002`。
- 沿用现有独立发布签名，可覆盖升级 0.1.0、0.1.1 正式版；Debug 签名不同。Flutter 原版独立安装。
- 使用随附 `SHA256SUMS` 核对文件。签名证书 SHA-256：`3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`。

## 验证与进度

流量清零修复已在 Redmi Note 8 Pro 的真实 Mihomo 上完成旧包失败、新包通过的同进程两轮 SOCKS 流量回归；普通停止、暂停中停止和重复停止均归零，签名 R8 候选首页启停也已验证。相关资源选择、真实键盘、搜索取消与自然时钟转场已有分组真机证据。当前 871 项 JVM 测试通过，构建与双 Lint 无错误。

完整 Flutter 视觉、菜单首开/关闭慢帧、部分历史稳定性问题、低 API、蜂窝切换和其他 OEM 场景继续按 roadmap 推进；玻璃默认关闭。本版不宣称这些项目已经完成。

## 源码与内核

源码标签：`kclash-v0.1.2`。内核仓库迁移至 [`lonnnnnng/Clash.Meta`](https://github.com/lonnnnnng/Clash.Meta)，固定提交仍为 [`464552e44ddf92affe5c41ee5a5d653a0e1e8daf`](https://github.com/lonnnnnng/Clash.Meta/tree/464552e44ddf92affe5c41ee5a5d653a0e1e8daf)，与 0.1.1 相同；仅从 `chen08209/Clash.Meta:FlClash` 同步。

详细记录：[流量清零溯源](https://github.com/lonnnnnng/FlClash/blob/kclash-v0.1.2/docs/traffic-session-reset-defect.md) · [roadmap](https://github.com/lonnnnnng/FlClash/blob/kotlin/docs/roadmap.md)。
