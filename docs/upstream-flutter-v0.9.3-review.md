# Flutter v0.9.3 增量与 Kotlin 功能映射

审查日期：2026-11-05（北京时间）。本表是源码差异审查，不是新增功能验收。

## 固定版本与同步边界

- Kotlin 当前提交：`16fa671b7126c5e9e90f9f233e0674315652461a`。
- Flutter 旧参考：`7fd417fb9a9a277c6a9339a8ac87f907df7c25f7`（v0.9.2）。
- 本次新参考：`be84736605527034689c95bf27828ff27d912c5e`（v0.9.3，发布记录日期2026-11-04），新增9个提交；[完整上游比较](https://github.com/chen08209/FlClash/compare/7fd417fb9a9a277c6a9339a8ac87f907df7c25f7...be84736605527034689c95bf27828ff27d912c5e)。
- 本轮只执行不递归子模块的上游fetch、git show/diff和Kotlin源码核对；没有merge/cherry-pick Flutter提交，没有修改产品运行代码、构建、安装或设备操作。
- Flutter仓库只作UI/UX、功能与实现参考，提交禁止合入Kotlin主线。内核代码唯一同步来源仍为`chen08209/Clash.Meta:FlClash`，自己的`kclash-fixes`保留本地修复；两种同步边界不可混用。
- 以下“已有/部分/缺失”均指本次静态源码映射，既有测试仅确认源码存在；旧验收证据不能证明新差异已经通过。

## 九项提交总览

| 提交 | 上游改动 | 对应Kotlin现状与处理建议 |
| --- | --- | --- |
| [2a5a42c3](https://github.com/chen08209/FlClash/commit/2a5a42c3c53afbd34be47dc7405031ec709f5cee) | Core协议重构、手动/健康检查超时对齐、后台预热与错误传播、Android Go本地时区等 | **部分**。当前已有IO初始化、VPN失败清理/恢复、DNS网络重置和错误解析；统一超时及Go本地时区缺失。协议重命名没有独立产品收益，不整体跟随。 |
| [b8f84b3d](https://github.com/chen08209/FlClash/commit/b8f84b3d17fb81d02cc04cfe28978d3a389b3975) | 应用内下载更新、校验、进度/取消及系统安装 | **未实现**。当前只有构建版本/签名配置与未签名CI制品，没有APK更新入口。须先完成Kotlin自己的签名发布来源。 |
| [bb47f2cc](https://github.com/chen08209/FlClash/commit/bb47f2cc306433b065df62d10022d1a642bfaa07) | UI迁到Prism组件库，更新按压、转场、文字选择及列表呈现 | **部分**。已有原生菜单/子菜单、Sheet、页面连续过渡与弹性按压；新Prism视觉及文字选择菜单未对齐，不能按组件迁出文件数统计新功能。 |
| [138016b1](https://github.com/chen08209/FlClash/commit/138016b17995a0ed8ad905b218b3721c7578fd7b) | MITM应用侧预埋 | **上游未启用，Kotlin未实现**。默认关闭，公开随包核心缺执行能力，不列当前必跟公开功能。 |
| [52b975f8](https://github.com/chen08209/FlClash/commit/52b975f863e799922cb81dce244dfcacd9e8894b) | 实时连接并入请求，筛选/分组，通用快速列表与映射编辑，导入边界调整 | **部分**。已有两个数据源、详情复制、历史搜索、分区YAML；统一入口、类型化筛选/分组、页面断开连接与通用quick-edit缺失或未等价覆盖。 |
| [b7df4583](https://github.com/chen08209/FlClash/commit/b7df45838e2f1bbf6fda1acaeb6b43df1147094f) | UI帧预算与性能审查规则 | **文档标准**，不是新增运行功能。当前未证明最大数据下逐帧达标，不能用LazyColumn或动效测试代替帧性能验证。 |
| [63aaf6d8](https://github.com/chen08209/FlClash/commit/63aaf6d8cfc501f57b0da53603a35a7f85a990ec) | 按应用代理保留隐藏选择、智能选择/剪贴板/权限与计数修正 | **部分**。保存已保留隐藏选择，智能选择、导入校验、进入权限重查和全量已选计数仍有差距，优先参考修正。 |
| [7d5db7e2](https://github.com/chen08209/FlClash/commit/7d5db7e239e010a0fb94de9afbca47a304255a72) | 新建自动选择组离开无效首节点 | **待内核集成**。运行时代码只有子模块`4a8e40cf`→`024245dc`，对应此前审查的4项内核增量中的后两项；不能重复当成另一项独立修复。 |
| [be847366](https://github.com/chen08209/FlClash/commit/be84736605527034689c95bf27828ff27d912c5e) | v0.9.3版本与发布说明 | **元数据**。只改CHANGELOG、changelog.json、pubspec.yaml，不需要套用到Kotlin自己的版本号。 |

## 核心、测速与启动

1. **统一测速超时缺失**：上游`2a5a42c3:core/config.go:applyOptions`调用Provider默认超时；`core/delay.go:configuredTestTimeout`对同名节点、同测速URL查Provider的显式超时，取最长值，否则使用宿主请求超时。当前[`core/delay.go`](../core/delay.go#L86)仅采用请求值，[`CoreBridge.kt`](../android/core/src/main/java/com/lonnnnnng/flclash/core/CoreBridge.kt#L123)的setup参数尚无此选项。内核新增API需宿主接通，不能只改gitlink就记完成。
2. **Provider刷新后漏测与url-test缓存修复待集成**：前者修复旧健康检查轮次/1秒缓存未覆盖新对象，后者对无有效测量缓存至少保留1秒后惰性重选；已有有效测量仍保留原缓存策略。当前产品内核`f83e9b10`没有这些增量。此前候选树`3c3a86f8f8c4078fd64587b0c78b7cffaf6fc07f`保留控制器修复，相关四包race114通过事件、父Go race265通过事件；属于上一阶段的隔离候选验证，没有Android新包验收。Provider切片仍有无锁读写静态路径，这些测试未报race不构成全部交错安全证明。
3. **Android本地时区缺失**：上游`core/timezone/timezone_android.go`读取系统时区，必要时用bionic offset回退，并在进程启动设置`time.Local`；当前`core/mobile/mobile.go`没有对应初始化。仅应评估Go侧本地时间一致性，不据此推断所有界面时间都错。
4. **VPN/核心IO初始化和失败清理已有独立实现**：[`CoreRuntimeManager.kt`](../android/service/src/main/java/com/lonnnnnng/flclash/service/CoreRuntimeManager.kt#L649)和现有`VpnService`已在IO链路初始化，失败释放TUN/服务并清恢复意图；不因缺Flutter的Core.load预热形式判定Kotlin仍在UI线程初始化。上游本提交未改Android VPN状态机本体。当前TUN保护gate、JNI错误防护、控制器修复必须保留。
5. **资源准备主要目的已有**：`CoreRuntimeManager.ensureInitialized`的IO链路调用`paths.prepare()`；实际实现位于[`AppModule.kt`](../android/app/src/main/kotlin/com/lonnnnnng/flclash/di/AppModule.kt#L267)，先写临时文件再rename。rename失败时回退直接复制，因此不宣称所有失败分支都具有同等原子性。
6. **Core协议不整体迁移**：上游将方法改为命名空间式、参数对象化、命令成功返回null、错误结构化；当前Kotlin Bridge与旧Go方法配套并已有错误转换。事件envelope仍为method=message，没有仅为名称变化迁移协议的必要。调用等待上限尚需按具体入口审计，不能把错误解析存在等同所有等待边界都已覆盖。

## 按应用代理：应优先参考的行为差异

2026-11-11更新：以下历史差异中的智能选择范围保护、剪贴板校验、完整安装包计数/枚举和返回刷新已由KUI-038实现，889JVM、Redmi13/13通过，见[溯源与边界](access-control-protection-defect.md)。OEM专用权限拒绝/授权的跨厂商真机验证仍待补；原表保留审查时证据。

| 行为 | 当前状态与证据 |
| --- | --- |
| 保存时保留被筛选隐藏的名单 | **已有**。[`AccessControlViewModel.kt:save`](../android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/AccessControlViewModel.kt#L319)仅去空、去重、排序；存储层也不按可见集合裁剪。 |
| 智能选择只调整本次筛选范围、保留范围外旧选择 | **缺失这部分保护**。当前`buildSmartSelection`/`smartSelectApps`用完整apps重建accept/reject。失败不重写已覆盖；空apps和异步期间状态变化仍需测试。上游智能选择范围按系统/联网筛选，不包含搜索词，不应自行把搜索范围当成上游定义。 |
| 剪贴板trim/合法包名、无有效输入不覆盖 | **缺失**。当前[`parseAccessClipboardList`](../android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/AccessControlViewModel.kt#L124)按原换行拆分，非null输入会替换名单；既有单测还明确期待保留空行，需与行为和中文注释同步修正。 |
| 每次进入重查应用列表权限，拒绝时保留已有列表并提供授权入口 | **缺失专用状态/流程**。当前入口仅切页，加载依赖PackageManager launcher查询，不能保证枚举所有已安装应用；需在Android实际权限/应用可见性模型下验证，不能简单照搬Flutter插件。 |
| 已选数量不受搜索/筛选影响 | **缺失**。[`selectedCount`](../android/app/src/main/kotlin/com/lonnnnnng/flclash/ui/AccessControlViewModel.kt#L61)当前统计visibleApps。目标为可枚举已安装应用中的全部已选项，不把未安装包算入，不改变顶栏全选的可见范围语义。 |

## 请求、连接、编辑器与UI

- **数据与详情基础已有，统一页面缺失**：现有实时连接卡、历史请求搜索/标签和共用详情复制；上游统一入口可切live/recent并保存选择，首页两类入口分别指定初始feed。当前仍分离，不应把两个数据源存在记为统一功能完成。
- **页面断开操作缺失，底层接口部分已有**：Go已有closeConnection/closeConnections，Bridge已有批量关闭用于切节点；当前连接详情只有关闭面板与复制。后续需接页面事件、运行态守卫及回读验证，不能将关闭面板当断开网络连接。
- **类型化筛选/分组未覆盖**：上游同类facet OR、不同类AND，支持网络/规则/应用/出口等筛选及按应用/主机/规则/出口分组；当前请求关键词全AND且未携带facet类型。实时搜索、分组持久化尚缺。速度排序主体来自旧连接能力迁入，不全部算此次新功能。
- **快速编辑部分已有**：Kotlin有原始/分区/单组YAML及部分逐行输入；上游统一单项新增与整列表替换，list为逐行文本，map为逐行key:value，带长度/保留键/逐行错误校验。不能概括为所有输入都支持JSON/YAML，通用入口与一致失败保留仍需补齐。
- **导入边界有额外变化，暂未完整对齐**：上游同批相同代理复用、账号身份比较、组名trim/冲突让名、dialer引用保留。当前`CustomProfileImport.kt`仅为YAML分区过滤之一，不能代表整个资源导入链。需要连同实际Repository/资源规划路径单独核查，尚未判定全部缺失或全部覆盖。
- **Prism UI只是功能参考，不引入Flutter依赖**：固定子模块`63450c0ac0e8e1b2c0f3ed4ad7926dc94e083185`本轮未拉取，已核对App调用点与测试，未核实全部内部动效参数。当前有`AppMenuStack`、`AppPopupMenu`、`ConnectionModalSheet`、`MainPageMotion`、`ElasticPressState`；后者部分页面调用，`MainPageMotion`仍按旧300ms easeOut参考。新版按压着色、文字选择旁编辑菜单、来源行到页面的连续形变和精确排版仍待验证。
- **二维码主体已有**：Kotlin ZXing后台编码、UTF-8/M级/四格静区与整像素绘制已存在，无需为编码器实现不同而重写；超长原文回退和错误提示策略存在差异。
- **帧预算未验**：上游新文档提出启动最多漏一次vsync、运动阶段8ms预算及行级重建/到场调度规则。它是目标，不是上游或Kotlin已达标证据；现有动画/截图测试不替代最大数据的逐帧测量。

## 应用更新与MITM

2026-11-11更新：Kotlin独立签名与0.1.2公开Release资产已建立；应用内更新仍待实现，见[六项目标](functional-closure.md)，不再受下述历史“缺发布源”前置条件阻塞。

- **APK更新未实现**：上游Android使用DownloadManager进度/取消、SHA256SUMS精确资产名校验、PackageInstaller确认；状态在内存，重启时清理，不是任务恢复。未知来源授权往返和APK签名预解析未见完整显式流程，不能直接宣称上游覆盖所有安装边界。
- 历史审查时Kotlin只有独立包名`com.lonnnnnng.flclash`、arm64构建、版本参数和可选Release签名配置；CI产出未签名APK/校验文件，不是可安装的签名Release更新源。应先完成Kotlin自己的发布身份/版本/资产契约，再实现更新，不能安装Flutter的`com.follow.clash` APK作为升级。
- **MITM为关闭预埋**：固定新参考`lib/common/constant.dart:35-37`的`FEATURE_MITM`默认false；8个`mitm.*`方法缺handler，`core/lib.go:SetMitmPressure`为空。模块、正文交换、CA管理/root系统安装、压力降级等应用侧存在但公开随包核心未实现，当前不列Kotlin必须追齐的公开能力。现有普通请求证书校验/forceGC不等于MITM能力。

## 建议推进顺序及未验证边界

1. **先内核/测速稳定性**：只从`chen08209/Clash.Meta:FlClash`集成4项已审查内核增量，保留本地修复，接通宿主超时；按原流程统一构建、Redmi集中验收后才更新产品gitlink/台账。时区初始化按适用性在当前Go适配层实现。
2. **随后按应用代理的数据保护**：智能选择范围与隐藏选择、无效剪贴板输入保护、稳定计数和页面权限/可见性状态；先验证现有守卫、失败不覆盖，再验UI。
3. **接着常用编辑与新视觉参考**：通用快速列表/映射编辑、导入冲突专项核对，再做按钮、文字选择、菜单/页面/Sheet连续过渡及最大数据帧性能。新版视觉先补足Prism固定源码/运行参考，不能沿用旧截图就记v0.9.3对齐。
4. **请求整合单独登记**：它是功能差距，但观测页次要打磨的既有暂缓保持；是否把统一入口、断开连接提升为主流程任务需另行排期，不借本次审查自动展开。
5. **更新待发布链路，MITM暂不跟**：备份/系统恢复、语言和主题等原暂缓保持；玻璃默认关闭，TalkBack/无障碍取消保持。

本次没有新构建/测试/设备证据。上游所有新测试仅用作后续用例参考；未完整审计Prism内部实现、所有导入冲突和JSON/schema、所有设备权限/OEM/安装行为。本机原始API快照和两份专题核查位于外层`acceptance-artifacts/flutter-upstream-review-20261105/`，原始附件未随文档发布。审查完成不改变旧Flutter参考下的已验结论，也不把当前Kotlin整体升级为v0.9.3 parity。
