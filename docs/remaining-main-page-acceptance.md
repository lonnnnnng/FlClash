# 第9项剩余交互与恢复验收清单

> **新内核仓库重新构建与真机运行（2026-11-10）**：App `300efb31`／新fork核心 `464552e4` 全新递归检出、独立Go编译缓存、关闭Gradle构建缓存并强制重编，165任务执行；签名Release与Lint零错误通过，AAR→独立符号剥离→APK原生库逐字节吻合。同提交CI 42738505695全检查成功。Redmi覆盖安装回拉SHA `afd2a28c…100af068`；核心/VPN启动、90项Provider页面、停止释放端口已验。HTTPS首轮3成功，显式重启紧接前台服务出现的首请求TLS失败，后续同会话3成功，总6/7保留失败；未直接等待核心READY，因果未定，不记网络恢复闭环。配置21字段／偏好／正文SHA-size-mtime保持，测速238键保持但值／时间更新；WAL/SHM等变化单列。探针和10个手机文件已清，服务停止，手机保留新签名包，公开Release未替换。证据[构建与Redmi验收](acceptance/mihomo-refork-build-redmi-20261110.json)，见[迁移记录](mihomo-fork-migration.md)。

> **内核 fork 迁移（2026-11-10）**：已迁至 `lonnnnnng/Clash.Meta:kclash-fixes`，GitHub 直接 parent 为 `chen08209/Clash.Meta`；唯一同步上游仍为其 `FlClash` 分支。五项修复／合并提交及产品 gitlink `464552e4` 原样保留，独立核心克隆完整性、当前子模块初始化及旧版0.1.0/0.1.1固定提交检出均通过；新 fork 原分支／标签保持。主线引用、README和溯源链接已更新；未新增功能验收计数。见[迁移记录](mihomo-fork-migration.md)及[证据](acceptance/mihomo-fork-migration-20261110.json)。

> **代理搜索连续过渡与实体扫码（2026-11-10，KUI-031）**：代理根页搜索接固定Flutter分段弹簧/透明度、组合入口和单入口液滴回收；快速反向保留位置/速度，搜索中只有一个测速按钮，取消清空并收IME。修复开发中异步组加载导致搜索/测速重叠及材质退场顺序。869项JVM（App587执行、其余282复用）、Debug/签名Release-R8/motion及双Lint零错误通过；Redmi深色8/8、浅色4/4、自然深浅2/2、动画关闭1/1，共10个不重复方法15次执行通过，56PNG/12拼图及两套原PTS视频已检查。签名本地候选`5f00dc54…5098d250`安装回拉一致；实际代理搜索/Back通过，配置21字段/选择、2偏好及正文SHA-size-mtime保持，测速缓存更新仍未逐条归因。用户实体摄像识别测试QR，确认表单精确匹配且相机释放，未导入；此证据属于当前候选，公开0.1.1未替换。测试包/手机诊断文件已清理，服务停止。接续资源/添加Sheet下沿搜索、剩余完整Flutter及菜单/RenderThread性能；历史异常/低API/蜂窝/OEM仍PARTIAL。证据`docs/acceptance/proxy-search-motion-redmi-20261110.json`，溯源`docs/proxy-search-motion-defect.md`。

> **搜索动效接续参考（2026-11-10）**：固定Flutter原Scaffold已在Redmi独立包验证根页顶栏搜索、推入子页下沿搜索及真实IME取消；原始录屏777帧、32个采样位置已检查。仅浅色组件参考，Kotlin连续过渡尚未适配。下一切片优先代理根页搜索展开/收回，再补资源/添加Sheet的下沿搜索，见`docs/search-motion-reference.md`。独立参考包已清理，原Flutter包未操作。

> **发布后边界与资源长目录（2026-11-10，KUI-030）**：原始正式0.1.0→0.1.1覆盖升级已验，UID、单FILE配置21字段/选择、2条偏好、238条测速记录及正文SHA-size-mtime在安装边界保持；启动后配置保持但测速value/时间更新，未确定事件归因。Perfetto在KClash无进程时，process_stats成功而最小sched ftrace使traced_probes进入D并flush失败，原生慢帧仍未归因。代理/规则64项目录补首次显现当前选择，Redmi深浅两例各2/2、相邻回归2/2，4个不重复方法共6次执行全过、23PNG已核验；真实IME清空/末项选择、一次回调/别名与重开取消已验。865项JVM（App583执行，其余282复用）、Debug/签名Release-R8/独立测试包及双Lint零错误通过。最终本地候选SHA `9b7fad97…88fcaa7`已安装回拉匹配，公开0.1.1资产和标签未替换；正式配置保留，3个自有测试包与12个手机诊断文件已精确清理，服务停止。实体QR、完整Flutter视觉/动效、低API和历史异常因果继续待验。证据`docs/acceptance/post-release-boundaries-redmi-20261110.json`；溯源`docs/resource-picker-long-list-defect.md`、`docs/release-upgrade-acceptance.md`。

> **扫码崩溃修复与0.1.1正式发布（2026-11-10，KREL-002）**：正式Release修复ML Kit三类注册器构造器被R8裁剪；旧包3/3缺失、修复APK DEX门禁3/3保留，源码`5aed72fe`和标签`kclash-v0.1.1`已发布。标签CI 42673055574成功，Go race、JVM test、签名R8、双Lint及最终APK门禁通过。公开Latest APK SHA `02924c48f20fa6885fec061deebcb82bb18ef9c90930c24fab3b25aa8fef4437`；Redmi覆盖安装回拉哈希一致，相机取景/手电筒/后台释放恢复/关闭释放6阶段通过，同PID 4709。配置卡仍可见，未清正式数据或提交导入；验收后相机空闲、VPN/ProxyService停止。865项JVM本机强制执行零失败/错误/跳过；实体摄像二维码/低API未验。证据`docs/acceptance/qr-camera-r8-redmi-20261110.json`及`../../../acceptance-artifacts/qr-camera-published-0.1.1-20261110/`，溯源`docs/release-mlkit-registrar-defect.md`。

> **Geo更新恢复闭环（2026-11-10，KCORE-006）**：共享核心补齐瞬断重试，最多3次、1/2秒退避及165秒总预算，旧文件保护、唯一终态和安全错误日志保留；核心`464552e4`已推自己的kclash-fixes。865项JVM、Go父模块及route/http/updater race、Debug/签名Release-R8/motion与双Lint通过。Redmi故障注入5场景通过；独立motion按自动80→固定80→固定8880→恢复自动2086四轮原GitHub完整正文16/16（HTTP200，bytes/length/文件大小一致），正式R8包三轮全部更新12项成功（缓存校验路径）。正式包`033d6c10…`已覆盖安装回读，配置及原URL保持、VPN/ProxyService运行，测试包和本轮手机输入已清。瞬断恢复能力及当前新网络更新范围闭环；实网均首次成功，旧网络具体根因、切网/蜂窝/OEM及长期稳定性仍未确认，历史失败不覆盖。证据`docs/acceptance/geo-download-recovery-redmi-20261110.json`；溯源`docs/geo-download-recovery-defect.md`。

> **Geo下载断流修复（2026-11-09，KCORE-005）**：确认应用自身mihomo请求走原代理链；Android强制Connection:close触发当前链路正文截断。curl交叉keep-alive4/4、close1/6，Go双路径close均失败/keep均完整。核心20bc0fc4取消强制close并在Body.Close/请求失败回收独立Transport；页面保留脱敏错误分类。HTTP5项race/父模块全量race/route race、864项JVM、Debug/签名Release-R8/双Lint通过。Redmi原GitHub四Geo首次更新4/4（约52秒）、重复操作4/4（约7秒，含缓存校验边界）；未换节点/未用加速。正式包a8c490c1安装回拉一致，原配置保留、VPN运行。远端具体丢尾实现、首轮504和历史TLS问题不追认同因。证据`docs/acceptance/geo-download-connection-redmi-20261109.json`，溯源`docs/geo-download-connection-defect.md`。

> **添加条目动效、关闭留窗与45分钟观察（2026-11-09，KUI-029）**：更多设置候选/分组标题按固定Prism独立300ms收起，保存失败保留、连选不重启、搜索退场清理已补。最终860项JVM、Debug/签名Release-R8/motion及双Lint通过；Redmi同套13/13、自然动效1/1，32PNG/4拼图与两套原PTS视频核验。固定Prism组件实机参考已建立，不代表完整Flutter页等价。关闭Popup三轮16/27/27帧、jank3/2/2已可见，Simpleperf正确PID仍拒绝，原生栈未得。独立纯代理45分钟10次Provider读取90项（61–112ms），HTTPS8/10、前两次TLS失败保留；元数据/文件SHA-mtime/选择保持，受控死亡系统恢复，审计/质量缓存/显式stop键变化可识别。正式包`a72a955a…`安装回拉与首页/配置烟测通过；原配置保留，独立包已清、服务停止。整体视觉/性能/历史因果仍PARTIAL。证据`docs/acceptance/selection-motion-stability-redmi-20261109.json`；溯源`docs/selection-entry-motion-defect.md`、`docs/menu-close-window-investigation.md`、`docs/historical-stability-investigation.md`。

> **更多设置与资源选择（2026-11-09，KUI-028）**：NTP/Sniffer换为clock/eye复合插图；添加面板分组/搜索/清空、最后一项成功回执后关闭，协议目标改行尾锚定菜单，代理/规则资源与图标搜索统一胶囊。修复保存中Back/外点先退场的缺口；旧包同断言失败，新包未点击区域像素差0并可失败重试。最终860项JVM、Debug/签名Release/独立包与双Lint通过，Redmi同套27/27、84图/11拼图；正式菜单24阶段、8终态图及首页/配置烟测完成。正式包SHA `ad5b0604…`，测试包/本轮输入已清理、原配置保留。最终菜单进程打开75/198、Popup31/138，关闭20/165且Popup销毁不可观测，仍不认定性能闭环。完整工具栏搜索/条目连续动效、固定Flutter运行对照及历史稳定性根因仍待补。证据`docs/acceptance/more-selection-parity-redmi-20261109.json`，溯源`docs/more-selection-parity-defect.md`。

> **配置子页与输入恢复（2026-11-09，KUI-026）**：修正Provider表单扣除删除触点后的标签/值宽度、开关与IP整行点击、可选提示、最后一项添加自动返回及分组选择排版；配置资源区分搜索无结果/目录为空并可清除恢复，NTP/Sniffer空态按视口定位。字段/YAML确认从子Dialog取IME控制器；旧包同一新增用例在awaitHiddenIme五秒超时，最终包通过。860项JVM（App578执行、其余282复用）、Debug/签名Release/独立测试包及双Lint通过；同套Redmi4+18个不重复方法22/22、68图/10拼图已验。正式签名安装回拉、原配置详情/Provider缓存与底部配置Tab烟测通过；配置未清，测试包及本轮上传原1.yaml副本已精确清理。当前仅本组视觉/交互修正，完整Flutter页面与菜单性能仍PARTIAL，KUI-025首开退化不覆盖。 证据 `acceptance/config-subpages-parity-redmi-20261109.json`，溯源 `config-subpages-parity-defect.md`。

> **源按钮底色与玻璃开场（2026-11-09，KUI-025）**：配置/代理按钮携带真实底色，普通材质按sRGB交接、玻璃只叠源底色。测试改等实际轮廓增长，补齐深浅玻璃展开帧、准备期取消、真实宿主背景冻结与重开重采；860项JVM（App578执行、其余282复用）、Debug/签名Release/独立测试包及双Lint通过。同套Redmi两批3+19个不重复方法22/22、56图/8拼图、8对终态像素一致；正式深浅视频102/103原PTS帧核对、各抽样24张。打开进程74/201→74/190、Popup31/129→37/124，退化观察保留，不认定性能收益；关闭23/168→21/172。手机保留同签名本地修正版，配置未清、测试包/采集文件已清理、服务停止。玻璃默认关闭，完整视觉/慢帧仍PARTIAL。 证据 `acceptance/menu-fill-readiness-redmi-20261109.json`，溯源 `menu-fill-readiness-defect.md`。

> **菜单胶囊与屏幕模糊（2026-11-09，KUI-024）**：补齐源按钮胶囊长边、Flutter稳定回源曲线/20%源图/50ms桥接；正文先变形再按屏幕sigma模糊，源图使用浮点轨迹，正文独立绘制节点复用。859项JVM（App577执行、其余282复用）、Debug/签名Release/独立测试包与双Lint通过；Redmi最终15/15、28图、8对终态像素一致，正文原尺寸/半尺寸/源图模糊宽度均29px。正式包打开进程75/206→73/206、Popup35/139→34/142，关闭20/178→19/167；仅回到基线附近，不宣称性能提升。首版80/183退化保留。深浅视频104/102原PTS帧核对并各抽样24张，玻璃开场两图仍为准备阶段；完整Flutter视觉/慢帧仍PARTIAL。手机保留同签名修正版、配置未清，测试包已清理、服务停止。 证据 `acceptance/menu-content-parity-redmi-20261109.json`，溯源 `menu-content-parity-defect.md`。

> **菜单阴影与顶卡回源（2026-11-09，KUI-023）**：对照固定Flutter/Prism修正29dp半径的sigma换算、仅主体投影并扣内部、普通/玻璃阴影接续，以及顶层卡片矩形/等比回源和后层隐藏。857项JVM结果（App575执行、其余282复用）、Debug/签名Release/独立测试包与双Lint通过；Redmi最终13/13、23图和深浅两段107/103帧原PTS视频已核验，旧联合阴影红测失败保留。GPU替换未过像素门槛已撤回。正式包进程打开69/209→68/209、关闭20/178→20/176；Popup打开24/147→29/144，未证明性能收益。手机保留本地签名修正版，完整Flutter视觉/慢帧仍PARTIAL。证据 `acceptance/menu-shadow-parity-redmi-20261109.json`，溯源 `menu-shadow-parity-defect.md`。

> **菜单准备阶段与分窗口复核（2026-11-09）**：截图准备即停涟漪、清空隐藏图层两个候选均未改善并已撤回，手机恢复KUI-022优化版。新增离线分窗口报告工具，4项边界测试及72份真实帧表解析通过；18次关闭Popup均已销毁，明确标不可观测。当前对照进程打开62/210、Popup自身21/149，两者不能混称；Android16 RTAnimation/Skipped位可重叠。下一步按Popup独立数据核对固定Flutter阴影轮廓、裁剪与回源透明度，完整性能/视觉仍PARTIAL。证据 `acceptance/menu-source-preparation-redmi-20261109.json`，记录 `menu-source-preparation-investigation.md`。

2026-11-09 当前源码盘点。GR-01～05、SAF-01～05、VIS-01～06、MOT-01交互范围已验；KUI-018/019为历史优化切片，最新KUI-022在正式签名Redmi包暂停隐藏源按钮涟漪，打开jank130/228→67/205（57.02%→32.68%），关闭17/179→17/180。8对终态像素一致、11/11回归、20图已验；整体流畅度与完整Flutter视觉仍PARTIAL。原生采样被本ROM SELinux拒绝，剩余慢帧/关闭早段与其余子页继续待办。证据`acceptance/menu-source-ripple-redmi-20261109.json`；不同包/批次不累计。

## 独立全局规则页（本组范围已验）

入口为工具 → 高级配置 → 附加规则，使用 `GlobalRulesController`，不是 Profile 扩展页的全局规则子页。本组使用独立 `GlobalRulesRecoveryInstrumentedTest`：activity5为5/5，cold2为14/14加2次再恢复，regression1为4/4；证据见[验收JSON](acceptance/global-rules-recovery-redmi-20261107.json)。冻结应用目标及连续失败分支另有JVM证据；STOPPED受控force-stop不推广VPN/OEM，旧选区失败原因未追认。

| 编号 | 操作与窗口 | 必须观察的结果 |
| --- | --- | --- |
| GR-01 | 已有规则长草稿、校验错误、放弃确认分别实际 Activity 重建；真实 IME 下滚动和逐层返回 | 原规则 ID、输入、错误、确认和返回层级保持；取消零写入，连续确认只提交一次 |
| GR-02 | 搜索后批选、删除确认重建；注入保存失败后取消和重试；清除搜索 | 只删除可见已选项；失败原位保留选择和确认，重试可达；不会把搜索结果当完整目录保存 |
| GR-03 | 表单打开后外部修改或删除原行，再重建/保存；排序时外部改目录 | 阻止旧草稿覆盖或把已删除规则重新新增；保留草稿及可操作的冲突提示 |
| GR-04 | 独立加密会话下，草稿/错误/确认/搜索/批选/排序冷进程恢复 | 外部核对旧/新 PID；自动恢复原入口，正文不进入 Bundle 或明文文件；不主动 open 冒充恢复 |
| GR-05 | 保存意图落盘后未提交、事务提交后未清会话、退出期间进程死亡；提交后再次恢复 | 未提交等待显式处理；已提交通过事务回执防重放；仅应用受理时配置，切配置不串目标 |

已接独立Keystore会话和Room事务回执；原行快照拒绝覆盖/重新新增，批删失败保持确认与选择，排序以完整目录CAS提交。KUI-016的红测、实现来源与失败记录见[缺陷台账](global-rules-recovery-defect.md)。本组16图/6拼图仅证明所列静态状态；完整Flutter视觉与连续动效另验。

## 真实资源选择器（本组具名范围已验）

最终build13同套包的`ResourceSafHostInstrumentedTest`为7/7、`ResourcePickerColdInstrumentedTest`为14/14及3次再恢复，相邻回归4/4；证据见[验收JSON](acceptance/resource-picker-recovery-redmi-20261107.json)。使用DocumentsUI路径和SHA绑定输入清单。规则 YAML/MRS 与图库走 OpenDocument，组图片走 GetContent；它们的提交边界不同，分别验收。

| 编号 | 操作与窗口 | 必须观察的结果 |
| --- | --- | --- |
| SAF-01 | 规则 YAML/MRS 选择器已打开时重建或杀宿主进程，再从系统 UI 选文件 | 返回原入口且只读取一次；先预览不写目录，显式保存一次；MRS 原字节/预览一致 |
| SAF-02 | 选择器取消、页面退出后的迟到结果、失效 URI、重复/孤儿回调 | 请求令牌正确消费；不复活已关闭页面、不新增资源、不停留忙态；保留可重试错误 |
| SAF-03 | 规则预览已有未保存正文/选区/错误时冷恢复，随后取消或保存 | 导入正文和 MRS 字节有独立加密恢复；取消零写入，保存保持原格式与身份 |
| SAF-04 | 图库选择器重建/冷死返回，坏文件后重试及重复回调 | 当前收到 URI 即导入的边界保持；实际图库/文件只创建一次，取消不写入 |
| SAF-05 | 组图片 GetContent 等待或有界读取期间重建/冷死；父组表单取消/确认 | 结果绑定原组会话；取消不写正文，确认一次；128 KiB 限制和错误重试保持 |

已接`PersistentFilePicker`独立密文请求与一次消费、规则预览密文、规则/图库同事务回执和组图片父会话绑定；实际图片读取预算128 KiB。失效URI/重复/迟到/孤立结果为受控协议检查，成功重选走真实DocumentsUI。系统等待只杀普通宿主PID并观察系统拉起新PID；预览/提交用受控停止，读取中断位于受理检查点，不推广任意流字节/OEM/VPN自然回收。38张PNG经13拼图核对，其中6张系统转场图不作稳定终帧，完整视觉另验。来源与失败见[KUI-017](resource-picker-recovery-defect.md)。

## 主页面、面板、空态与自然时钟交互（具名范围已验）

固定Flutter `be847366`与其Prism `63450c0`，最终build6同套包在Redmi API36完成final1回归14/14、final-natural1深浅场景2/2；62张PNG经22张拼图核对，并审阅6组原始PTS序列。来源和取舍见[视觉对齐记录](main-visual-motion-alignment.md)，构建、安装、帧统计及正式数据保护见[验收JSON](acceptance/main-visual-motion-redmi-20261108.json)。

| 编号 | 已核验范围 | 结果与限制 |
| --- | --- | --- |
| VIS-01 | 首页/配置标题24/30、72dp标题栏；配置卡单行名称、标题栏动作、同一行元数据 | 深浅主题截图及相邻交互通过；原配置页入口/整卡详情行为保留 |
| VIS-02 | 配置快切24dp圆角、16dp间距、17/22标题、13/18副标题及右侧选中标记 | 确认仅一次、返回与取消通过 |
| VIS-03 | 根面板共用48dp栏高、17/22居中标题和左关闭 | 详情/代理选择/质量等使用共用标题；子页原左返回/右直接关闭保留 |
| VIS-04 | 质量六行占位、网络来源、固定复制槽与刷新旧值保持 | 自然时间加载前后六行实际Y坐标相等；不代表真实远端查询 |
| VIS-05 | 搜索排除无匹配组、全页无结果和普通空态 | 清除搜索恢复原组，搜索不写临时组选择；深浅场景、IME返回通过 |
| VIS-06 | 配置页与快切共用短来源/流量副标题 | 深浅主题静态回归通过 |
| MOT-01 | 110/95/80ms导航反转、搜索/IME、多层菜单返回重入、List切换、面板展开和加载 | 无ComposeTestRule，自然帧时钟及实际触点；2/2交互通过，整体流畅度仍PARTIAL，菜单热点优化另见下节 |

前组混合采集窗口累计1872帧中592帧jank（31.62%），p50/p90/p95/p99为31/81/125/250ms。第二主题计数包含第一主题，不可相加。保留其`NOT_ACCEPTED_INVESTIGATE`结论，不用该混合分母与下组无采集结果直接相减。

本机Flutter3.41.9低于固定Prism要求3.47.0，本组为源码参数与Kotlin实际渲染核验，未做相同Flutter版本的运行截图逐像素对比。固定Debug宿主的浅色状态栏白图标不计正式主题对比度验收；8张裸组件图也不作为完整主页面顶部inset证据。

## 菜单性能热点（KUI-018历史切片）

稳定后层Shape、独立阴影透明度合成及拖选时才订阅命中revision，减少阴影位图重建。测量开关在截图之前生效，gfx按主题/阶段清零，窗口存活时取样。非debuggable独立`.motion`包复用同一领域对象宿主，优化前后各两轮深浅、无录屏/PNG；最终轮次在基线之后，未随机化。详见[缺陷及实验记录](motion-performance-defect.md)与[最终证据](acceptance/motion-performance-redmi-20261108.json)。

| 测量窗口 | 优化前jank | 最终菜单修复jank |
| --- | --- | --- |
| 一级菜单打开 | 61/146（41.78%） | 34/177（19.21%） |
| 二级菜单展开 | 61/92（66.30%） | 20/129（15.50%） |
| 返回/重入并切List | 121/325（37.23%） | 57/378（15.08%） |
| 全部非启动窗口 | 621/4401（14.11%） | 465/4599（10.11%） |

二级菜单主线程阶段中位34.25→9.45ms；这是Vsync到SyncQueued的采样区间，不是纯CPU时间。阴影方法采样支持归因，但有深栈截断，包容耗时不可跨层求和。面板延迟读取尝试曾退化，已撤回；最终质量面板74/531（13.94%）→62/535（11.59%），不声称面板自身已优化。

最终Debug同套包21/21回归、自然深浅2/2，另深色List终帧1/1补验；88PNG/31拼图及7组原始PTS序列核对。两张玻璃开场图仍为源按钮帧；一张深色List为入场帧，原视频35.497→35.763秒显示节点到达，同包补验终帧完整。正式数据/设备/Flutter基线保持，独立双包与采集文件已收尾。非Debug诊断包未混淆，库按debug回退，不替代正式签名/R8 Release、生产网络负载或全应用性能验收。

## 动态顶卡与菜单阶段复验（KUI-019，当前）

KUI-019继续优化菜单动态轮廓：阴影局部重组、同帧Path/Paint复用、宽度延迟布局及移除重复裁剪。Redmi API36独立非debuggable包、无PNG/录屏，前后各两轮深浅，菜单合计jank331/1429（23.16%）→261/1555（16.78%），顶卡收回38.61%→14.07%；首开26.42%→23.16%改善有限，关闭6.91%→9.94%退化保留，首次打开UI区间中位也未改善。完整自然流程各深浅一轮非启动窗口15.10%→13.39%，不冒充正式R8运行。final-build3共849项JVM零失败/错误/跳过（App568在build1执行、其余281复用，最终四模块UP-TO-DATE）；Debug/Test/未签名Release-R8/双Lint通过，Debug0错误68警告14提示、Release0错误66警告13提示。同套最终包回归19/19、自然整页2/2、三轮菜单2/2；92PNG/32拼图及8组原PTS抽样已核对，593源码与安装回拉匹配。用户确认重置，当前正式0份配置，排除依赖非空库的两条导航回归；Flutter参考包保持缺席、不安装。44份正式快照及最终独立检查一致，原1.yaml/设备设置保持；独立.motion双包与独占文件精确回收，结束无服务/tun0。旧包/候选12图逐字节一致后刷新环境基线，14/19、旧包0/5与run-as拒绝回拉失败均保留。整体性能和完整Flutter视觉仍PARTIAL；下一步优先首开、关闭退化及RenderThread慢帧，再补CUSTOM组数、首页无流量统计、内存图形与其余子页。玻璃默认关闭，既有边界保持。证据`docs/acceptance/menu-rendering-redmi-20261108.json`，溯源`docs/menu-rendering-performance-defect.md`，附件`../acceptance-artifacts/menu-rendering-20261108/`。

8张液滴图为过程帧；稳定菜单与深浅List完整节点另由自然流程确认。裸组件inset和固定浅色宿主状态栏白图标继续作为已知边界。新旧包12图一致排除本补丁引入静态差异，不追认具体字体/渲染原因。

## 2026-11-09 诊断与内存子页更新

菜单首开/关闭诊断已把 Trace 区间覆盖到打开/收回可见终态，Redmi 深色 `menu-diag-dark3` 精确 `1/1` 通过；Perfetto 在该 API36/ROM 上仍 `traced_flushes_failed=1`、`traced_final_flush_failed=1`，没有 `sched_slice`、应用线程或菜单 slice，RenderThread 根因保持未归因。失败 trace 只作平台证据，不计性能通过；待可用 trace 设备/ROM 再重跑。

内存详情子页已完成本轮固定 fixture 真机视觉验收：`DashboardInstrumentedTest#memoryCardAndDetailsCaptureDarkAndLight` Redmi `1/1`，深浅主题卡片、详情面板和完整窗口截图已回收并目视，无重叠或裁切。证据 `docs/acceptance/menu-rendering-diagnostic-redmi-20261109.json`、`../acceptance-artifacts/memory-visual-redmi-20261109/`；固定 fixture 不代表真实 Go 内存数值或 Flutter 同版本逐像素一致。

CUSTOM 配置卡组数芯片已完成固定 fixture 真机视觉验收：`ProfilesVisualInstrumentedTest#profilesScreenshotMatrixMatchesBaseline` `1/1`，6 组芯片与选中标记并存；长列表选中/长按回归 `1/1`。首页统计核对 `DashboardInstrumentedTest` 运行态与历史重建 `2/2`，确认上传/下载双块、30 点历史和连接/请求/DNS 三列摘要；Flutter 手机参考同样是双流量块，旧“无流量三列统计”描述已纠正。证据 `docs/acceptance/custom-profile-chip-dashboard-redmi-20261109.json`。

## 视觉、异常与后续顺序

1. 优先继续菜单首次打开、关闭卡顿率退化与剩余RenderThread慢帧，分开启动首帧、主线程、RenderThread及Buffer Stuffing；在当前分段测量基础上补正式R8运行边界。KUI-018/019已证实的缓存及收回收益保留，旧31.62%与中间面板退化记录不覆盖，整体流畅度仍未完成。
2. 继续其余子页完整视觉；CUSTOM 组数芯片、首页双向流量/历史与连接三列摘要的固定 fixture 已验，仍需真实 Go 数值和可复现的固定Flutter运行对比。逐页列明实际比较状态，不用Kotlin自身截图哈希证明Flutter一致。浅色正式状态栏、逐帧连续性和最终性能独立核验。
3. 回补第6项元数据/mtime保护异常、未知偏好变化、Provider运行态 final6-before 超时，以及旧网络根因与长期网络恢复稳定性。Geo有界恢复及当前新网络更新范围已由KCORE-006验收；原失败保留，后续绿测不反向证明原因。
4. 蜂窝可用后补 Wi-Fi↔蜂窝，其余首次安装/第三方入口/OEM 按真实条件逐项验收。
5. 再推进第10项实时背景/触点光效、玻璃品质、8 MiB正文性能，以及第11项低API及更广的升级场景；原始0.1.0→0.1.1单FILE配置保留已于2026-11-10验收。0.1.0远端CI、独立签名、正式发布资产下载/签名与Redmi运行已于2026-11-09完成，见 `docs/acceptance/release-0.1.0-20261109.json`；当前动效问题仍按第1项优先处理。

玻璃默认关闭。备份/系统恢复、观测次要打磨、语言和主题暂缓；TalkBack/自定义无障碍及早期 Room 兼容不进入本清单。Flutter 包 `com.follow.clash` 仅供参考，不修改；正式配置及授权原文件持续保护。

## 0.1.0正式发布与首页反馈收尾

KREL-001与KUI-020已修，公开APK在Redmi验证：真实SAF/首次VPN及运行的基本流程、下载签名/安装回拉、国家IP稳定/紧凑网速卡、停止/冷启动。具体不同候选与公开包范围以发布证据为准，不替代全部视觉或性能。接续优先使用同一公开包测量菜单首开/关闭；当前ROM无可用Perfetto线程数据时保留未归因，不换模拟器冒充Redmi结论。

## 正式Release菜单基线（发布后接续）

**发布后接续（2026-11-09，正式Release菜单基线）**：公开0.1.0签名R8包在Redmi停止态深色、冷进程三轮真实菜单开/展开/收回/关闭，12/12终态通过；无录屏/截图采样。打开68/116帧jank（58.62%）、展开14/92（15.22%）、收回10/65（15.38%）、关闭10/90（11.11%）。这是真实配置正式包基线，不能与旧.motion夹具直接比较；关闭Popup消失后每轮只剩1条Flags=0记录，RenderThread仍未归因。下一步优先正式菜单首开，关闭需在窗口消失前采帧再归因；透明内容层是源码候选，尚未修改/证实收益。证据 `docs/acceptance/release-menu-redmi-20261109.json`。

## 菜单性能候选与关闭窗口诊断

**菜单性能调查（2026-11-09，KUI-021）**：完成透明正文图层、硬件阴影、缓存软件阴影、源按钮图层复用四种候选核验。三个签名R8候选深浅各三轮24/24终态均通过，854JVM/双Lint通过，但首开/关闭无充分收益；硬件阴影未过外观门禁，全部撤回，产品源码与公开0.1.0保持一致。缓存软件阴影10对固定帧像素完全一致仍不足以证明性能。新增关闭窗口存活期诊断回收4/9/18条帧，早段UI区间最大75.928ms、渲染最大27.901ms，仅经过时间不作CPU归因。下一步采样首开/关闭前100ms的主线程工作，保留关闭前帧表。公开APK已恢复到Redmi，测试包清理、night=yes、服务停止；原配置/1.yaml保持。证据 `docs/acceptance/menu-performance-investigation-redmi-20261109.json`，溯源 `docs/menu-content-compositing-defect.md`。

## KUI-029后续顺序

1. 菜单关闭Popup帧已补齐；继续可用原生采样的调用栈归因。当前ROM正确PID仍被拒绝，保留首开/关闭/RenderThread慢帧为未完成，不能由留窗诊断称性能通过。
2. NTP/Sniffer添加候选及标题连续退场本组已验；代理/规则64项目录定位和真实IME已由KUI-030具名验收；代理根页工具栏搜索由KUI-031具名验收，资源/添加Sheet下沿搜索、其他工具栏与页面连续过渡、更广长目录场景继续推进。Prism组件运行对照已建立，完整Flutter页面仍未等价验收。
3. 长窗先保留首次TLS失败并关联DNS/节点拨号/远端关闭，再补更长及重复恢复。加入写入来源/时刻诊断后继续追查历史元数据/mtime/偏好异常；本轮未复现不关闭历史记录。
