# 功能性剩余项收尾

11月18日 POCO F2 Pro / API36 从本轮授权订阅实际导入 32 个节点。Trojan（16）、Hysteria2（12）、VLESS（4）各抽取两个不同端点，纯代理 GLOBAL 下 6/6 返回 HTTP 200，并在对应连接上核对叶节点及双向流量。[脱敏回执](acceptance/subscription-protocol-traffic-poco-20261118.json)

2026-11-18，首次联网 TLS 按用户决定作为网络环境问题归档。语义预检已覆盖 POCO 的 CUSTOM 系统选档替换、92 份缓存 MRS 一次解析、128/129 个文件边界和 VMess 实流。[验收记录](acceptance/custom-saf-preflight-poco-20261118.json)


2026-11-18增补：在线 age 订阅完整链路已在POCO API36真实UI验收1/1；授权源轮换到新identity，测试覆盖缺钥、错钥无写入、正确密钥解密导入、Mihomo运行加载和非直连HTTP200。见[验收说明](online-age-subscription-acceptance.md)与[脱敏JSON](acceptance/online-age-subscription-poco-20261118.json)。较早记录中的“明文/无密钥”只反映当时状态。

2026-11-20更新发布：KClash 0.1.11/code1011 已发布为 GitHub Latest，标签`kclash-v0.1.11`固定`10697f3f`。标签CI 44754219878 的 Go race、JVM/R8、双Lint及签名/版本/ABI/DEX门禁通过；公开ARM64 APK 49,584,177字节，SHA`b7fb8727…488c08da`与远端digest和SHA256SUMS一致，沿用既有证书。Release APK已回下载核验，未安装手机。 本轮无新增实网/系统验收，功能边界保持。见[发布证据](acceptance/release-0.1.11-20261120.json)。

2026-11-17历史快照：当时正式版本为0.1.5/code1005，按用户要求直接本机签名构建发布，未重跑单测、完整Lint、Go或真机；TUN受控原生异常补偿最终5/5、前后10笔实网通过，原RULE路径新增TLS失败另列。见[0.1.5发布记录](acceptance/release-0.1.5-20261112.json)。

2026-11-11开始，基线`2153854b`。本清单完整保留用户授权的六项目标；分组验收通过不代表总目标完成。2026-11-11用户最初将应用内更新暂缓；最新明确将范围简化为检查当前/最新版本，有新版时跳转GitHub发布页。原应用内下载和安装的剩余边界取消，不再推进。

2026-11-11用户进一步确认：配置/偏好长期稳定性和Provider运行态恢复已有近期通过证据，旧异常目前不能复现，无需继续追根。两项按“现状验收通过、接受未归因历史记录”收口；保留已有审计/恢复保护和失败附件，出现新的可复现问题再重新打开。不是宣称所有历史原因已查明。11月11日当时6项中4项按当时范围收口（名单保护、两项按用户接受历史收口、简单版本检查）；语义预检主流程已验但环境/协议边界未全验。2026-11-12用户更换手机要求有界复现后，POCO冷进程首请求5/5通过，当时TLS旧失败转为历史观察。11月17日本轮原RULE首请求再次失败，11月18日按用户决定将首连TLS作为网络问题归档。当前六项目标五项收口、语义预检一项部分完成。

| 项目 | 完成所需证据 | 当前状态 |
| --- | --- | --- |
| 按应用代理名单保护 | 隐藏/未安装项保留、无效剪贴板不覆盖、无Launcher包可选、异步及运行态守卫、实际VM/Room与Redmi回归 | 已完成本组：KUI-038，889JVM、Redmi13/13、最终构建/双Lint/DEX及清理核对通过；OEM权限兼容边界单列 |
| 启动/恢复首次联网 | 直接核心READY与DNS/拨号/协议/TLS/HTTP关联，保留首失败；有新可复现失败再打开定位 | **按用户决定作为网络问题归档**：11月17日POCO原RULE路径五笔请求在注入TUN故障前TLS失败；两个节点SOCKS对照失败，固定原index2节点的TUN前后10笔成功。暂不能归属节点/网络/系统/软件，详见[TUN本组记录](tun-rebuild-compensation-acceptance.md)。此前2026-11-12用户指定POCO F2 Pro/API36，5次冷进程首请求均在READY后1285～1391ms开始，DNS/TLS/HTTP200、非DIRECT及VLESS响应头完成；最终节点端点与旧Wi-Fi失败相同。手机/Wi-Fi同时变化，根因未归属；不改产品网络或内核。前序另4次成功仅作补充。既有Redmi切网恢复6/6、新节点18/18和Wi-Fi矩阵9/10（含首请求失败）原样保留，不改写全绿。见[新设备记录](tls-first-request-poco-recheck.md)、[证据](acceptance/tls-first-request-poco-20261112.json)及[旧切网验收](acceptance/network-switch-redmi-20261112.json) |
| 配置/偏好长期稳定性 | 保留写入审计、逐键分析和长窗口/文件保护；按用户接受当前验收结果，旧异常只留档 | **已收口（用户接受历史未归因）**：近期窗口变化有来源、正式四表/两正文SHA-size-mtime/UID保持，未发现新配置丢失/损坏。旧元数据/mtime/未知偏好不再主动复现或追根，有新可复现异常再打开。见[审计](state-audit-key-join.md) |
| Provider运行态恢复 | 保存前/后真实死亡、恢复防重放及数据保持已分组验收；按用户接受现状，保留旧失败 | **已收口（用户接受历史未归因）**：前组两死亡窗口/6HTTPS通过；本组旧包after及最终包after各完成真实系统恢复/3HTTPS，缓存/正文/防重放与清理通过。旧保存错误未复现，保留KDIAG-003提前报错诊断；TLS失败单列网络任务，未进入死亡窗口的场景不记恢复通过。不再单独追旧超时/保存原因。 |
| 导入前核心语义预检 | 候选配置节点/组/规则/DNS等语义检查，错误不落盘；运行核心和原文件/资源不受扰；外部资源可用性边界明确 | Redmi18/18、混合FILE/URL/CUSTOM/密文草稿、真实管理器apply失败/仅重试、授权URL导入及90个外部正文分批解析证据保留（首轮89/90+定向1/1，非单轮全绿）。POCO本组最终6/6补独立UID默认TUN真实DNS/TLS/HTTP前后保护；显式绑定EPERM复现于VPN可见、提供者默认仍Wi-Fi的启动窗口，随后同UID/PID/Network成功。不是原生establish失败证据。911JVM含复用、motion构建/Lint通过，系统未授权establish-null拒绝/清理/授权后显式恢复另有1/1证据；11月17日另补系统建接口异常/核心拒绝真实FD的单次自动恢复、双次停止重试及普通Profile不误重建，单批5/5、10笔实网通过；剩余其他自然原生异常、低API/OEM/其他协议与来源，整体PARTIAL；在线age链路已完成，见[本组验收](acceptance/online-age-subscription-poco-20261118.json)。见[记录](core-semantic-preflight-investigation.md)及[本组证据](acceptance/semantic-vpn-poco-20261112.json) |
| 应用更新（简化范围） | 读取当前版本、筛选Kotlin稳定发布、比较最新版、失败重试、有新版跳转GitHub发布页 | 按用户最新决定移除应用内下载/安装，原复杂流程待验项取消。**已完成当前范围**：903 JVM、Debug/签名R8/motion-Test/双Lint、Redmi真实查询和浏览器跳转通过；0.1.4当时已发布并完成标签/分支CI、公开APK签名/SHA及Redmi从0.1.3原位覆盖到0.1.4/code1004回验通过。当前仅检查版本并打开GitHub，见[实现和验收](app-update-implementation.md)及[发布证据](acceptance/release-0.1.4-redmi-20261112.json)。 |

2026-11-12 KUI-042 对等待反馈作了跨页面审计：配置添加、文件导入、DNS/常规/LAN 保存及配置页异步操作的可见忙态已补齐；资源/规则资源/Provider/同步/核心/编辑器等路径已有等待反馈。首组911 JVM、Debug/AndroidTest/未签名Release-R8及双Lint通过，Redmi 新增反馈用例5/5；完整相关UI类38项当时有6失败、1跳过，原[证据](acceptance/async-feedback-redmi-20261112.json)保持。后续POCO补授权参数的同四类基线为31通过、7失败、0跳过；仅修正三份测试的旧文案、异步回调/宿主和拖拽合同，最终单批**38/38、零跳过**。911 JVM结果（含Gradle复用）、motion应用/测试构建与Lint零错误，13图核验通过；未改产品/内核、未发版。详见[溯源](async-operation-feedback.md)与[收口证据](acceptance/kui042-regression-poco-20261112.json)。在线age实网链路随后于2026-11-18通过独立真机验收，不扩大KUI-042本身的覆盖结论。

2026-11-12 随KUI-042发布KClash 0.1.4：GitHub Latest、标签/分支CI、正式APK摘要/签名及Redmi覆盖安装和启动均已核验。当前设备快照没有活动VPN transport，因此不把安装验收写成VPN运行态通过。发布细节见[0.1.4验收记录](acceptance/release-0.1.4-redmi-20261112.json)。

统一按“一组功能→构建→用户指定真机集中验收→roadmap/todo/证据→中文提交推送”推进。本轮按用户更换设备使用POCO F2 Pro，原Redmi记录保留。Flutter只参考；内核仅跟随chen08209/Clash.Meta:FlClash。当前优先剩余页面视觉、连续转场与菜单慢帧；不恢复TalkBack，备份/系统恢复继续暂缓；2026-11-20用户新增工具页整合及中英双语/主题，062已完成本组验收。

## 缺口、异常和未验边界分开统计

- 功能实现与剩余边界：核心语义预检主流程、20类入站分支、资源竞态与R8真实文件导入已验；不保证外部资源、端口和权限可用。应用更新已改为检查版本并跳转GitHub，原下载/安装异常边界不再属于目标。
- 网络新失败：11月17日原RULE请求再次TLS失败，固定原index2/GLOBAL前后10笔成功，11月18日按用户决定归档。11月12日POCO冷进程5/5是历史通过结果。最新旧失败有WS建立阶段read-tcp错误，此前另有VLESS响应头未完成，两类记录均保留且不混作已确认原因。手机和Wi-Fi同时变化，不能单因果归属；原始失败记录继续保留。配置/偏好和Provider旧异常仍按用户接受收口、只留档。
- 条件/覆盖待验：Tasker及后台定时/开机调用（MacroDroid前台Activity已5次通过）、低API核心/VPN/实网及其他OEM、剩余来源/协议流量及其他自然原生故障等覆盖升级。Wi-Fi与蜂窝及完全断网恢复已在Redmi API36通过，不能推广到其他ROM。混合配置、未提交草稿、运行VPN状态和真实管理器脚本apply失败已有证据；POCO新增普通独立UID默认TUN前后真实流量通过，提供者显式绑定的启动EPERM与稍后成功均留证，系统未授权establish-null拒绝补偿已单独通过1/1，11月17日受控系统异常与真实FD拒绝的热切补偿另已5/5、10笔实网通过，仍不等于所有自然原生故障。它们不按已确认缺陷统计。
- Always-on生产状态识别仍有限制：本轮确认`AlwaysOnVpn.state()`固定返回`UNKNOWN`，只提供系统VPN设置引导，不等于系统Always-on启动/恢复完全缺失；Debug回读和ADB契约不能替代生产入口/第三方E2E。首次干净安装VPN授权已有正式发布证据，不能继续列成未验。
- 8MiB正文上限功能已由KUI-040补验（UI6/6、密文草稿跨PID/真实核心预检/外层保存两阶段）；整链路准备/恢复提交仍约44/69秒，性能保持PARTIAL并按用户要求后排，不列为当前功能阻塞。官方MacroDroid前台Activity启停5次已验，Tasker/后台触发不外推；备份/系统恢复沿用用户暂缓决定；工具四Tab与语言/主题已由062补齐。TalkBack、自定义无障碍、早期Room兼容、横屏/大字号扩展及其他ABI不列待办。

## 已接入诊断与下一组接入点

- StabilityWindow已改为记录直接RUNNING时刻、首请求先行、Provider独立计时；三个初测成功不关闭历史根因。
- SOCKS成功回复在真实节点拨号之前；现已在connect前固定源端口、开启Core.beginDialDiagnostic，关联安全拨号分类及Tracker。TLS EOF不能直接归因应用TLS。
- 现有DNS事件没有socket关联ID，节点DNS时间窗只能作候选关联；严格因果须结合指定源端口dial诊断，不输出域名/地址/正文。
- 配置写入审计已覆盖Repository、Backup、DAO与文件替换等入口，并区分Delay事件/主动测速/过期清理；仅最外层事务成功后记DB_COMMITTED。旧window3多笔偏好、删除键及三处偏好updatedAt已补核；新格式SharedPreferences/Delay逐键联接已验，批量过期删除/非原子读取仍保留限制，不能将新窗口来源匹配等同于历史问题闭环。
- Provider verify现已拆分网络、组读取、密文恢复、正文绑定等等待的begin/end/fail与独立budget/elapsed。最终两死亡场景通过，受控关闭与超时也留存失败证据并恢复日志；旧超时保持未归因。证据见[最终集中复验](acceptance/functional-network-diagnostics-redmi-20261111.json)。

## 当前推进顺序

2026-11-19，按用户决定，语义预检与系统兼容保留当前覆盖和未验证记录，停止扩展验收。当前推进 Flutter 子页与面板、连续转场和菜单性能，玻璃默认关闭。

KUI-VIS-050 已在 POCO F2 Pro / API36 完成非空 Provider Kotlin 连续流程验收：真实资源选择器绑定隔离资源、添加并保存 TIMEOUT=5000、重开后 Room 回读；精确用例1/1、7张截图核验。未做 Flutter 同状态运行对照。[验收记录](acceptance/provider-options-nonempty-poco-20261119.json)

KUI-VIS-049 已在 POCO F2 Pro / API36 完成图标集预览 Kotlin 真机验收：顶栏搜索、专属图块空态、无结果恢复和长网格滚动位置恢复；资源页类 3/3、19 张截图核验。对照固定 Flutter `be847366` 源码的 AppBarSearchState、NullStatusIllustration.icons、`iconTile` glyph 及 88dp 网格参数；没有运行同状态 Flutter 截图。[验收记录](acceptance/icon-set-preview-poco-20261119.json)

KUI-VIS-048 已在 POCO F2 Pro / API36 验收。CUSTOM 规则的类型、资源与策略选择器补齐顶栏搜索、选中定位、分组与筛选条目转场。6/6 定向用例通过、17 张截图核验、规则类型搜索跨进程恢复 1/1。菜单 Perfetto 深浅各 10 次打开与关闭；匹配的首开慢帧集中在主线程，首次 Popup 的重组、测量与布局可见，路径绘制仅是 ART 候选热点。收回分类存在重叠，trace 丢弃限制仍保留；优化收益未验证。[验收证据](acceptance/visual-048-rule-picker-poco-20261119.json)

2026-11-12随KUI-042发布KClash 0.1.4；GitHub Latest、标签/分支CI、APK摘要/签名和Redmi覆盖安装/启动均已核验，当前设备快照未发现活动VPN transport。详见[发布验收](acceptance/release-0.1.4-redmi-20261112.json)。KUI-040正文与第三方入口的原始证据见[本组记录](acceptance/editor-external-redmi-20261112.json)：当组完成时尚未发版，之后的发布状态以0.1.4记录为准。
