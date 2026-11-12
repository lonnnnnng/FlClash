# 第三方 Activity 启停入口

2026-11-12，Redmi Note 8 Pro / API36，官方 Play 安装 MacroDroid 6.1.5，调用公开正式 KClash 0.1.3。正式 APK 回拉 SHA 与公开发布一致；此组没有修改启停协议。

MacroDroid 中使用“发送 Intent”：

| 字段 | 设置 |
| --- | --- |
| 目标 | Activity |
| Action | `com.lonnnnnng.flclash.action.START`、`.STOP` 或 `.TOGGLE`（后两者也填写完整前缀） |
| Package | `com.lonnnnnng.flclash` |
| Class | `com.lonnnnnng.flclash.ExternalActionActivity` |
| 分类 | 本轮使用 None；可选 Android DEFAULT |
| Data / MIME / Extras | 留空 |
| Flags | 本轮使用 0 |

临时动作编辑器内真实点击“测试动作”，不是 ADB 发送业务 Intent，也不是自建调用器。系统日志确认调用者为 `com.arlosoft.macrodroid` UID10244，进入公开入口后转内部 `QuickActionActivity`。KClash 页面与系统服务回读共同验收：START 进入运行；重复 START 保持同一对前台服务且运行时间继续累计；STOP 关闭服务；TOGGLE 从停止启动，再次 TOGGLE 关闭。5次动作全部符合预期。

本轮仅证明前台第三方 Activity 调用和既有 VPN 授权路径，不证明 Tasker、后台定时/开机触发、其他 OEM 或未授权 VPN 的完整流程。第三方动作执行完成没有成功回执协议，仍须用 KClash 状态和服务回读确认。

临时未命名宏草稿已放弃，MacroDroid 列表确认“未配置宏”，应用已停止；免费版保留供后续验收，没有购买 Pro 或开通付费订阅。正式 KClash 最终无服务，原始 `1.yaml` 内容保持。没有采集正式库完整前后快照，不把此轮操作宣称为四表逐字段保护验收。

证据见 [本轮验收](acceptance/editor-external-redmi-20261112.json)。原始页面和系统服务/调用者日志留在工作区外 `acceptance-artifacts/editor-external-20261112/`，包含网络信息的原始日志不上传。
