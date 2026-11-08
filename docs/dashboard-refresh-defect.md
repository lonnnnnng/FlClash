# KUI-020：首页出口反复加载与网速卡跳高

2026-11-09，用户在正式签名候选包上报告：节点/网络未主动变化时，国家代码和 IP 反复变成加载态；启动核心后网速卡左下角高频出现加载圈，整体高度跳动；累计流量下方留白过大。问题在首个正式发布之前处理，未发版候选为 `d5f992fc`。

## 来源与原因

- Kotlin `MainViewModel.refreshStats` 每秒设置 `isLoading=true`，`RuntimeStatsCard` 在两列下方插入带 10dp 顶边距的 18dp 加载圈，成功后删除，产生 28dp 高度变化。Flutter `lib/views/dashboard/widgets/traffic.dart` 的 `NetworkSpeedCard` 直接观察流量，没有该采样加载行。
- `OutboundIpViewModel` 对成功结果也每 60 次轮询重查；路由总版本变化直接清空全部出口字段。一次无关规则资源/其他自动组变化也可能改变总版本，不等于这次 IP 查询的实际出口改变。不能将所有现场刷新归因为同一种触发，用户观察的秒级间隔未独立计时归因。
- `MainActivity` 每次 onResume 无条件失效，初次网络/链路通知也重复失效，造成仍有效的结果重新显示加载。已抓到停止态显示完整结果后回到查询态的画面，原始附件保留。
- Flutter `lib/providers/routed_probe.dart` 依据真实命中 `checkRoutes` 判定出口是否改变；Kotlin 原 `CoreIpResponse` 丢弃了 Go 已提供的 `hits`。本组参考此机制在 Kotlin 现有 Bridge/Runtime/VM 链路实现，不合入 Flutter 提交，不改 Mihomo。

## 修复

稳定成功结果取消周期性重查。重复的底层网络属性通知去重，前台恢复只检查实际消失/变化的链路。保留 IP 响应的命中信息，路由版本变化时先验证命中出口；只有验证未保持、核心重启、网络变化或用户操作才重新查询。核验前后检查核心版本与宿主 revision，缺失/不完整命中不得复用，旧网络结果不可迟到回写。

国家代码在已有结果的手动刷新期间保持显示。后台统计保留旧数值/错误直到成功，不再插入加载行。累计值下方内边距 14dp→4dp，趋势图 36dp→18dp，默认双列布局固定减少 28dp；仍保留趋势，不采用随流量变化的动态高度。

## 验证与边界

新增/更新 JVM 回归覆盖稳定五分钟不重查、手动刷新、无关路由命中保持、真实出口变化、网络失效强制重查、核验网络/版本竞态、核心响应命中解析，以及慢速后台采样保留旧值和错误。本组定向测试第一次因 SDK 未公开 LinkProperties 复制构造器编译失败，改用属性字符串快照后定向检查通过。

以下为修复前计划，最终签名包 Redmi 长窗口、卡片几何、手动切换/刷新结果见末节；远端发布回验仍待。先前 R8 修复包的真机结果不计作本组通过。

本机原始证据：`../acceptance-artifacts/release-0.1.0-20261109/` 内 `redmi-stopped-refresh.mp4`、`redmi-running-refresh.mp4`（尽管文件名含 running，实际画面仍是停止态，不计运行态证据）、`dashboard-focused-tests.log` 与 `dashboard-focused-tests2.log`。

后续同步 Flutter 时复核命中复用与刷新语义；网速图高度是用户明确调整，不能被视觉对齐覆盖回原留白。

全量构建第一次出现 Lint/KAPT 并发读取失败：`Hilt_KClashTileService.java (No such file or directory)`。已保留 `build-dashboard.log`，本地与 CI 均改为先完成 Debug/Release 构建再运行双 Lint，不忽略检查。第二次构建及双 Lint 成功，853 项 JVM 零失败/错误/跳过；Debug 68 警告14提示、Release66警告13提示，均0错误。

## 路由诊断追加

首个修正候选 `1d8033b5…` 的78秒窗口仍失败：18次UI采样中4次查询态；卡片标题间距恒定617px，较旧版694px减少77px（Redmi密度下对应28dp）。原始失败保留 `dashboard-stability.json` / `dashboard-after-fix-failed.mp4`，不记整组通过。

临时脱敏诊断确认剩余查询由核心总路由版本变化触发，未伴随底层网络失效。相同查询目标的真实出口标识在多次查询间发生变化，且新命中立即checkRoutes=true，证明自动选组实际在换节点，不是把无关版本一律判为变化。核验为held=true的无关变化没有重查。详见 `ip-diagnostic4.log`；日志仅记录计数/版本/哈希，正式源码已移除临时诊断。

最终行为：固定节点与稳定网络持续缓存；自动组真正更换出口时仍后台查询，但保留上次国家/IP/质量展示，成功后替换；手动选组/启停/网络变更仍按明确失效边界处理，不能永久显示旧出口。质量查询完成时也核验命中，而不是只看全局版本清空结果。

诊断固定叶子节点后，从02:33:17至02:35:54的多次核心版本变化均held=true，无新增出口查询（`ip-fixed-node-diagnostic.log`），证明无关Fallback/自动组变化已经隔离。最终全量854项JVM通过，临时诊断已移除。

## 最终签名包 Redmi 验收

最终APK SHA-256 `72527d6543808f6a09e33bf44fc42e6ff269a21e6a00fdb4d9006e370de6548d`，854项JVM、Debug/签名Release-R8及双Lint通过，源码快照匹配。固定节点76.91秒18次、恢复自动选择76.32秒18次UI采样：各仅1个IP/国家集合、0查询态、全部运行、卡片标题间距恒定617px；旧版同几何694px，减少77px。手动刷新与前后台恢复保持展示；停止后冷进程31875保留授权本地配置，最终APK回拉字节匹配、无新增崩溃。自动选择已恢复，服务停止，正式包保留；原始1.yaml哈希保持。采样不是逐帧性能测量，不推广全部网络/OEM。详见 `docs/acceptance/release-0.1.0-20261109.json` 与本机 `final-fixed-node/`、`final-automatic/`、`final-redmi-summary.json`。

## 发布后按反馈继续压缩（2026-11-09）

用户再次指出累计值下方仍偏高。本次仅将 `DashboardTrafficChart` 的固定高度从18dp改为8dp，保留4dp间距，下方区域22dp→12dp；空闲和运行共用同一高度，不按流量增减布局。此处沿用用户明确的紧凑偏好，不恢复Flutter `_SpeedTile` 的宽高比留白。

Redmi同签名Release覆盖安装回拉一致；深浅停止态和深色运行态6次采样的网速/出站模式标题间距均为589px，旧公开包为617px，实际减少28px。累计文字位置保持，曲线仍可见；2/2现有首页回归、4项流量曲线单测、Release构建与Lint通过（0错误/66警告/13提示）。本轮无新增测试；首批诊断变体和Debug单测任务不兼容的配置失败保留，拆分后通过。手机保留本地修正版、原配置及默认字号，服务停止；公开0.1.0资产未覆盖。完整证据见 `docs/acceptance/traffic-card-compact-redmi-20261109.json`。
