# KClash 0.1.1

本版修复正式包打开二维码扫描页崩溃，并改善资源更新、配置子页与菜单交互。可直接覆盖安装 0.1.0 正式版，保留现有配置。

## 修复与改进

- 修复点击“扫描二维码导入”后立即崩溃：保留 ML Kit 通过反射创建组件所需的构造器。发布流程新增最终 APK 检查，避免 Debug 正常而 Release 失效。
- 修复 Geo 资源下载提前断流及请求连接回收，保持原始 GitHub 资源地址和现有代理规则，无需替换成加速地址。
- Geo 下载遇短暂超时、网关错误或正文截断时，最多尝试 3 次，共用 165 秒下载预算；失败保留旧资源，支持手动重试，逐次记录脱敏错误。
- 改善菜单源按钮涟漪、主体阴影、胶囊回源、顶卡交接和添加条目退场；完善更多设置、协议目标菜单和资源搜索排版。
- 修复配置表单键盘与保存中返回的边界，压缩首页网速卡累计流量下方留白。

## 下载与升级

- 安装包：`KClash-0.1.1-android-arm64-v8a.apk`，Android 7.0（API24）及以上，仅 ARM64。
- 包名 `com.lonnnnnng.flclash`，版本 `0.1.1`，versionCode `1001`。
- 沿用 0.1.0 独立发布签名，正式版直接覆盖升级；Debug 版签名不同，不能直接覆盖。Flutter 原版 `com.follow.clash` 不受影响。
- 使用随附 `SHA256SUMS` 核对下载文件。

## 验证范围

扫码修正版已在 Redmi Note 8 Pro 的签名 R8 包验证：取景、手电筒、前后台释放与恢复、关闭释放、真实系统相册识别到确认表单；用户独立复试确认不再崩溃。

Geo 恢复已完成 5 类故障场景；独立核心自动／固定出口四轮原址完整正文下载 16/16，正式包三轮全部更新通过。865 项 JVM 结果、双 Lint、Go 父模块及 Mihomo route/http/updater race 检查通过，具体批次和复用范围见仓库验收记录。

菜单剩余慢帧、完整 Flutter 页面视觉、低 API、蜂窝切换与其他 OEM 仍有待验项目。当前网络成功不能证明旧网络故障的具体远端原因或所有网络长期稳定；玻璃默认关闭。

## 签名与源码

证书 SHA-256：`3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`。

源码标签：`kclash-v0.1.1`；Mihomo 固定在 [`464552e44ddf92affe5c41ee5a5d653a0e1e8daf`](https://github.com/lonnnnnng/mihomo/tree/464552e44ddf92affe5c41ee5a5d653a0e1e8daf)，仅从 `chen08209/Clash.Meta:FlClash` 同步。进度见 [roadmap](https://github.com/lonnnnnng/FlClash/blob/kclash-v0.1.1/docs/roadmap.md)，扫码溯源见 [KREL-002](https://github.com/lonnnnnng/FlClash/blob/kclash-v0.1.1/docs/release-mlkit-registrar-defect.md)。
