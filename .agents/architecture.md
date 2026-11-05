# Architecture

## 核心控制器源码修复与溯源（2026-11-05，当前）

本组在Mihomo固定基线c5062921上修复KCORE-002重建竞态与KCORE-003坏TLS配置端口泄漏：公开入口同步关闭/绑定，Serve后台运行，显式持有listener覆盖未登记窗口，配置快照及路由构造同锁；TLS客户端CA/ECH准备先于Listen。修复提交f83e9b1032ed02831457d4da5a414e063e932694已推lonnnnnng/mihomo的kclash-fixes，父仓库gitlink固定，原始来源/失败/撤销条件见docs/upstream-source-defects.md与ADR0021。KCORE-001保留前组6ca697d1记录；未认定为旧Always-on TLS EOF根因。

最终build2 App492/Service206/Core38/Common31共767项JVM零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8和双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Go父模块全量race修复前265/265但压力38/40，修复后265/265与相同压力40/40；嵌套route为8顶层方法及2子场景共10通过事件。TLS最小复现原始两个泄漏分支（含父项3失败）→相同修复3/3；overlay仅红绿证明，产品直接构建修复源码。CI补父模块和route的race，Windows Pipe只读核验、未运行。

Redmi最终同套App/Test final2精确4/4：控制器6轮，正确密钥12次200、旧/错误12次401、关闭后立即绑定端口6次、嵌入写配置6次405，运行会话/正式配置保持；另模式/组件/旧菜单3/3。Always-on-final1零额外等待，冷启动和SIGKILL恢复首个TUN HTTPS均200、同socket核心归因/正流量；纯代理独立恢复1场景通过。系统恢复观察耗时5.899/6.887及2.927秒仅服务/TUN就绪观察，不是HTTP恢复耗时。初批final1的明文HTTP测试策略/错误类名两失败保留，修正测试后复验，不改产品网络安全策略。

正式1份配置全窗口逐字段/选择/正文SHA-size-mtime摘要保持，前后保护各1/1；原1.yaml双端、Flutter APK、系统设置保持，安装回拉/构建源码哈希一致，恢复备份清空，结束无服务/tun0，独占XML匹配后定点删除。Always-on开/关两图已目视，仅作系统状态证据。证据docs/acceptance/controller-lifecycle-redmi-20261105.json，本机附件../acceptance-artifacts/controller-race-20261105/。

本组控制器缺陷已修验证；第8/11项整体仍PARTIAL。旧TLS EOF因果/长期稳定性、第6项原长窗口保护异常保持未闭环；蜂窝延期，Fallback/在线age输入仍受限，首次安装/第三方/OEM、低API、远端CI、正式签名运行及完整Flutter视觉仍待补。玻璃默认关闭，原暂缓/取消项保持。


## TUN保护gate与只读核心诊断（2026-11-05，历史切片）

本组修正Flutter共用Go适配层的TUN保护缺口：gate先于hook就绪，listener未返回也保护，平台false/Control错误透传；关闭等待inflight并释放回调，迟到旧gate拒绝。TUN启动仍在handler锁外，启停仍串行，Mihomo子模块不改。只读DNS执行序号/单调时间和源端口有界诊断只用于观察，不改变DNS调度或作为就绪屏障；默认停采、不存原始端点。见ADR0020。

最终build1 App492/Service206/Core38/Common31共767项JVM零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Go普通全量265/265、定向race7/7；全量race在控制器httpServer异步重建1项失败，旧源码overlay复现同一竞态，单列未修，不能写全量race通过。

Redmi final1/2两轮默认零额外等待，冷启动/运行中SIGKILL恢复共四次首个TUN HTTPS均HTTP200、同socket归因/正流量，直接核心诊断有对应出站事件，无补诊断。3/3模式/组件/菜单回归、独立纯代理恢复1场景、前后保护各1/1通过，App/Test安装回拉与代码快照匹配。旧包baseline1冷TLS EOF保留；四次callsDuringStart=0，真机尚未观测该窗口触发，不能认定旧失败因果或长期稳定性已闭环。

正式1份配置逐字段摘要/选择/正文SHA-size-mtime、原1.yaml双端、Flutter APK及设置保持；备份清空，结束无服务/tun0，临时XML匹配后定点删除。final2系统开/关两图已目视，仅作系统状态证据；第6项原长窗口保护失败保持。

证据`docs/acceptance/system-vpn-protection-redmi-20261105.json`，附件`../acceptance-artifacts/tun-protect-20261105/`。第8整体PARTIAL，蜂窝延期，首次安装/第三方/OEM及生产Always-on状态识别仍待验；低API、正式签名运行、远端CI和完整Flutter视觉未补，玻璃默认关闭、原暂缓/取消项保持。

## 进程级网络观察（2026-11-05，历史切片）

NetworkObserver在Application.onCreate、按需/恢复/配置加载之前注册并随进程存续，与本地Flutter quickSetup前初始化一致。ProxyService/VpnService模块只持有通知职责；服务停止不能注销共用观察器或清空系统DNS，停止态Provider和下一次启动仍依赖它。真实网络变化继续走callback、200ms DNS合并与resetNetwork。见`docs/adr/0019-process-network-observation.md`。

只读snapshot记录started/registeredAt/dnsUpdatedAt/DNS数量/网络句柄，无DNS端点。VpnService的SocketProtectionMonitor原样透传protect结果/异常，用AtomicLong计数；计数仅覆盖已进入回调的调用，不能证明上游listener为空的静默窗口不存在。核心启停/恢复仍仅由CoreRuntimeManager协调，不改Go或submodule。

Debug请求在DNS/connect前绑定源端口并订阅事件，supervisorScope隔离观察任务、finally cancelAndJoin和恢复日志状态。首请求失败仍判失败，同授权目标的物理HTTPS对照和两次TUN重测另记诊断；不更换目标/节点、不重载配置。Base64分片按token/id/index/count重组并拒绝缺片；15秒显式延迟仅diagnosticPassed，不能成为默认passed。

最终build3/767项JVM、Debug/Test、未签名Release/R8和双Lint通过；Redmi final5/6两轮默认冷启动+SIGKILL恢复首个HTTPS均200、同socket归因和双向流量，3/3模式/组件/菜单回归、独立纯代理系统恢复及前后保护通过。旧包与增强包的冷首请求失败仍保留，节点DNS/拨号确切根因未定，不能认定全部稳定性已修复。正式配置/原文件/Flutter/设置保持、备份清空、无服务/tun0；第6项原长窗口保护失败独立保留。

当前证据`docs/acceptance/system-vpn-dns-recovery-redmi-20261105.json`，附件`../acceptance-artifacts/always-on-dns-20261105/`。第8项仍PARTIAL，蜂窝延期，首次安装/第三方/OEM仍待验；生产Always-on状态仍UNKNOWN。Go、低API、正式签名运行、远端CI未重跑，完整Flutter视觉仍PARTIAL，玻璃默认关闭，原暂缓/取消项保持。

## Always-on无蜂窝部分验收（2026-11-05，历史切片）

本组在无蜂窝条件下推进todo第8项，只有Wi-Fi↔蜂窝子项延期。对照Flutter修正系统VPN启动：先前台通知，再等待Application诊断/resume初始化，统一进入CoreRuntimeManager；内部启动action才建TUN。新增7项状态机单测，重复回调、失败释放与按需排除已有覆盖。

最终build8 App492/Service203/Core38/Common31共764项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8和双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Redmi同套App/Test精确回归3/3，纯代理SIGKILL恢复独立1场景通过，保护前后各1/1；安装回拉SHA匹配。

Always-on final7仅部分已验：准备PID16294先消失，系统真实开关拉起16486，verify前独立观察双FGS/tun0（约4.706秒）；绑定VPN Network、TLS主机名校验/HTTP200及同socket核心归因通过，平台回读alwaysOn=true/lockdown=false。受控SIGKILL后约6.919秒独立观察系统PID16860及双FGS/tun0，但随后TLS EOF，核心同端口TCP_DIAL_FAILED，脱敏日志含DNS解析失败/context deadline exceeded，根因仍未确定；整组overallPassed=false，不记完整实网恢复通过。上述秒数仅服务/TUN观察耗时。Debug短暂热切日志等级/订阅并在finally恢复，不改正式日志偏好或重载配置。

各场景正式行/选择/正文SHA-size-mtime恢复并清加密备份；全窗口1份配置摘要保持，原1.yaml双端、Flutter APK和设备设置保持，结束无服务/tun0，单个设备XML临时文件已定点删除。最终系统开/关两张截图已目视，仅作系统状态证据。早期Lint/脚本定位/普通socket超时和各TLS失败批次独立保留，不累计、不覆盖第6项原长窗口保护异常。

证据`docs/acceptance/system-vpn-always-on-redmi-20261105.json`，附件`../acceptance-artifacts/always-on-20261105/`。下一步优先定位Always-on恢复后的DNS/拨号，再补实网恢复；首次干净安装VPN、蜂窝、Tasker/MacroDroid真调用及其余OEM/开机行为仍待验。生产Always-on状态仍UNKNOWN，本轮Debug平台回读不能推广生产界面；Go、低API、正式签名运行和远端CI未重跑，完整Flutter视觉仍PARTIAL，玻璃默认关闭，原暂缓/取消项保持。

系统入口使用VPN_START_REQUESTED签名保护显式广播，ServiceBroadcastReceiver等待KClashApplication初始化Job；九秒BroadcastLease归还广播不取消应用任务。核心启停仍只有CoreRuntimeManager，ACTION_START_VPN仅由AndroidProxyServiceController发送。CoreBridge.updateLogLevel只更新日志等级，不改变路由。

## Fallback输入复核与纯代理系统恢复（2026-11-05，历史切片）

todo第7项已重新盘点：原1.yaml为33组（32 select/1 url-test）、0 Fallback；正式缓存与本次唯一授权在线响应同为34815字节、33组、0 Fallback且SHA一致。在线只读下载/解码，不写正式Profile/正文/mtime。合法非空Fallback的now由Mihomo选存活项或首项；无效now协议防御本轮2项回归中已验，但不替代实核与Fallback冷恢复，仍受输入条件限制。

todo第8项纯代理受控SIGKILL系统恢复完成本组验收：准备Activity结束后旧PID8860及其前台ProxyService仍存活；外部带轮次token/PID门禁广播触发自身SIGKILL，约2.874秒在启动verify之前独立观察系统新PID9093及同PID前台服务。verify不调用start/restart/apply，回读RUNNING、纯代理、resume=true及READY之后新CORE_RESTARTED；前后本地SOCKS+TLS主机名校验/HTTP200、对应socket核心连接归因及非DIRECT链均通过。2.874秒仅为前台服务观察耗时，不等于完整核心/HTTP恢复耗时。

最终build6 App492/Service196/Core38/Common31共757项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过；Debug0错误/67警告/10提示，Release0错误/66警告/9提示。Debug新增1条仅验收说明文字SetTextI18n；生产运行逻辑未改，新增DUMP权限保护的Debug宿主/receiver及外部驱动，最终Release清单确认无验收组件。Redmi final5系统恢复1场景、精确回归2/2、回归窗口保护前后各1/1；安装回拉SHA匹配。

各场景内部正式行摘要/选择/正文SHA-size-mtime、VPN/模式/auto/resume已恢复，验收加密备份已清；最终设备快照原1.yaml双端、Flutter包及设备设置保持，结束无服务/tun0。本组保持结论不消除第6项原长窗口元数据/mtime变化原因未定的失败记录。前两次编译失败、主线程网络失败、两次ServiceRecord格式解析门禁失败及中间包通过均独立保留，不与最终批次合计。

证据`docs/acceptance/proxy-system-recovery-redmi-20261105.json`，持久附件`../acceptance-artifacts/proxy-system-recovery-20261105/`。仅证明本Redmi API36既有配置纯代理的受控SIGKILL后Android重建，不推广OEM内存压力/强行停止/VPN。第8项首次干净安装VPN授权、Wi-Fi↔蜂窝、Tasker/MacroDroid真调用及Always-on OEM仍待验；下一步继续具备条件的系统入口。Go、低API、正式签名运行及远端CI未重跑，无新视觉修改/截图，完整Flutter视觉仍PARTIAL；玻璃默认关闭，原暂缓/取消项保持。

生产恢复链仍是Application读取Room runtime_should_resume→恢复诊断→CoreRuntimeManager.startProxy→服务/监听器；ProxyService空Intent先返回START_NOT_STICKY以等待Application初始化。新增只在Debug的宿主/receiver提供外部SIGKILL和只读恢复核验，DUMP权限+加密轮次快照/PID门禁，不修改正式生命周期。

## 首页IP/质量持久缓存与全部来源尝试（2026-11-05，历史切片）

todo第6项成功质量缓存闭环已验：复用Room偏好表，按IP保留3天且不延长原查询时间；失败仅当前路由内存冷却5分钟。force、IO失败降级、取消、路由失效、损坏/未来/过期项、事务并发及迟到失败保护已覆盖；显式JSON字段避免Release混淆改变磁盘格式，不改schema。

生产build2 App492/Service196/Core38/Common31共757项JVM结果零失败/错误/跳过（含up-to-date复用），Debug、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。testbuild4 Redmi final3 5/5、cold2双阶段、页面回归10/10；14PNG经3拼图目视。诊断包diagnostic5仅增强只读保护检查，构建/双Lint通过，独立cold5双阶段及短窗口保护前后各1/1；生产APK与功能源码哈希保持。

8个出口源/6个质量源及13个服务目标分别DIRECT和运行RULE真实尝试；出口解析6/8与7/8、质量3/6与4/6，其他结果保留FAILED/NO_TYPE/IP_MISMATCH，不能写全部源成功。运行服务12项可用、Prime Video失败；DIRECT逐项受限见JSON。真实跨PID回读保持原checkedAt，拒绝联网Runtime调用0且正式DI一致；prepare退出时PID已消失，不称杀死仍存活的准备进程。

长窗口正式配置保护after为0/1：正文SHA/34815字节/当前ID保持，但currentGroupName、lastUpdateAt及文件mtime变化；系统审计无对应下载成功，原因未确定。未回写旧快照或覆盖失败基线；各fixture内部保护及新cold5短窗口通过，不能替代原长窗口失败。原1.yaml双端、Flutter包和设备设置保持，结束无服务/tun0，安装回拉哈希匹配。

证据`docs/acceptance/ip-quality-cache-redmi-20261105.json`，持久附件`../acceptance-artifacts/ip-quality-cache-20261105/`；早期并发红测、live1失败和中间批次独立保留、不累计。第6项缓存与全部目标尝试已验，远端受限及长窗口保护异常单列；第7项Fallback仍缺授权非空组，之后推进第8项具备条件的系统边界。完整Flutter视觉仍PARTIAL；Go、低API、VPN、正式签名运行和远端CI未重跑；玻璃默认关闭，原暂缓/取消项保持。

成功质量通过Hilt注入RoomIpQualityCache；app_preferences单行JSON在事务内合并，显式协议字段与64IP上限不依赖模型反射名。只保留IP质量成功结果，出口IP/服务路由缓存继续内存；失败冷却绑定route revision，不能跨网络阻止重查。

## Provider部分失败恢复与真实规则命中（2026-11-05，历史切片）

todo第5项本组范围已验：Provider批量部分失败保留逐项受控原因，单项恢复只移除本项，轮询/前后台保留剩余失败，关闭/换配置拒绝迟到结果，超时释放忙态。Redmi真实Mihomo经受控HTTP服务完成1成功+503/缺失YAML payload两失败、旧缓存保护、单项恢复和只重试剩余项。公开8源更新SHA/条目/时间前进通过；HTTP8种、File8种、Inline3种实际HTTPS均有精确RuleSet/Provider归因，另验原配置geolocation-cn MRS命中及Inline Proxy原两个节点展开。

最终build9 App485/Service196/Core38/Common31共750项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi同套App/Test final7新增4/4、回归14/14、保护前后各1/1；25张PNG经5张拼图目视，安装回拉哈希匹配。正式1份配置逐字段/选择/正文SHA-size-mtime、原1.yaml双端、Flutter包及设备设置保持，结束无服务/tun0；临时配置/脚本/缓存恢复，18个独占设备输入按SHA核对后精准删除。

证据`docs/acceptance/provider-recovery-hits-redmi-20261105.json`，持久附件`../acceptance-artifacts/provider-recovery-hits-20261105/`。本组仅在原1.yaml临时副本的正式脚本投影中隔离规则并关闭嗅探；非整份原配置路由/嗅探交互、全部90个MRS站点或所有代理协议流量覆盖。原MRS首试失败、第二次成功已逐次保留，早期6个失败批次不累计。托管资源混合同步/apply失败新分支仅静态审查，未独立故障注入；Go、低API、VPN、正式签名运行、远端CI未重跑。完整Flutter视觉仍PARTIAL；下一项第6项IP/质量来源、持久缓存与失效恢复，玻璃默认关闭，原暂缓/取消项保持。

Provider失败集合与错误文案保存在同一不可变UI状态，providerGeneration只随详情关闭/切换递增；后台仅清列表。单项和批量只替换本次目标的失败结果；托管资源有同步成功项才apply，应用失败仅标记已成功下载项。测试以同socket源端口+目标443、TLS主机名、HTTP200/正文与精确规则归因联合验证；临时正文删除后确认不存在，失败保留加密恢复标记。

## 资源真实文件选择与外部脚本实网（2026-11-05，历史切片）

todo第4项本组范围已验：规则YAML/MRS与图标集真实OpenDocument，策略组本地图标GetContent经Android16照片选择器“更多选项→浏览…”进入DocumentsUI；覆盖取消、空/错误文件拒绝、重试、预览不写入、正式保存和Activity重建回读。MRS原字节保持，仓库/VM/页面预览摘要一致。公开外部脚本真实下载、改源、同步与绑定配置运行态应用通过，直接Bridge确认新增CustomProxy、暂存规则及Mihomo epoch 4→5→6；正文、DIRECT选择与纯代理会话保持。

最终build3 App481/Service195/Core38/Common31共745项JVM结果零失败/错误/跳过（含up-to-date复用），Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi同套App/Test final3新增5/5、相关回归12/12；MRS定向1/1独立保留，前后保护各1/1。15张PNG及2张拼图已目视，安装回拉哈希匹配；正式1份配置逐字段/选择/正文SHA-size-mtime、原1.yaml双端、Flutter包及设备设置保持，结束无服务/tun0。临时资源/配置/脚本与图标历史已收尾，8个独占设备输入按哈希核对后删除，本机附件保留。

证据`docs/acceptance/resource-saf-scripts-redmi-20261105.json`，持久附件`../acceptance-artifacts/resource-saf-scripts-20261105/`。仅新增验收代码，生产逻辑和Flutter上游不改。脚本走正式VM方法、无Activity轮询，非完整点击导航E2E；两URL的原始脚本正文相同，只证明改源/文件版本/核心重载，不证明远端正文变化或实际流量命中。组父编辑入口由正式VM打开，图片选择/失败重试/保存为真实UI；资源为Activity重建，不推广选择器冷进程。首批3/5及第二批4/5的定位/系统选择器路径/异步等待失败独立保留，不与最终批次累计；一张选择器截图为切换中间帧，不作完整视觉等价结论。第3项在线age仍受输入条件限制，第5至11项继续待办；玻璃默认关闭，原暂缓/取消项保持。

新增验收清单由SHA绑定独占设备文件；系统UI按实际包识别DocumentsUI或Android16照片选择器，再走真实浏览入口。规则导入先等异步读取/保存退出，重开时等VM绑定正式资源ID及busy清零，再比较仓库/VM/页面摘要，避免以旧占位符误判。脚本在无Activity环境调用正式VM并收集WhileSubscribed状态；先profileInputsApplied与直接Bridge epoch/proxyTypes，再查暂存规则，不用管理器读入口补加载。作用域取消后等待join，才恢复选择、VPN并按独占ID收尾；中断标记加密保存，成功清空并回读。

## 在线引用资源实网与age输入边界（2026-11-05，历史切片）

todo第3项CUSTOM引用在线源已验：手动ProfilesViewModel同步与生产到期Repository自动更新各完成一次真实下载、提交和当前CUSTOM应用。临时资源初值使用原1.yaml；下载后源字节版本变化，真实Mihomo epoch分别3→4、8→9，按正文SHA生成的文件Provider路径和7个节点回读匹配；当前CUSTOM、DIRECT组选择、正文及纯代理运行会话保持。无Activity/节点轮询参与补加载。唯一授权响应实测为34815字节明文，既有密钥为空，在线age下载/解密/应用仍受输入条件限制，不记通过。

最终build2统一检查通过：App481/Service195/Core38/Common31共745项JVM结果零失败/错误/跳过，含up-to-date/缓存复用；Debug/Test、未签名Release/R8和双Lint成功，各0错误/66警告（Debug10/Release9提示）。Redmi同套App/Test在线批次3/3、相关回归8/8，前后保护各1/1；正式1份配置逐字段/选择/正文SHA-size-mtime、原1.yaml双端、Flutter包及设备设置保持，安装回拉哈希匹配，结束无服务/tun0。证据`docs/acceptance/subscription-reference-network-redmi-20261105.json`，持久附件`../acceptance-artifacts/subscription-reference-20261105/`。自动场景直接调用生产updateDueProfiles，非另一轮WorkManager自然周期；手动场景走正式VM方法，非完整点击导航E2E。本组证明源字节更新及核心重载，不证明节点定义变化、规则/流量命中或VPN；中断恢复代码已审查，未新注入网络中断/死亡。未重跑Go、低API、正式签名运行、远端CI或完整Flutter视觉；玻璃默认关闭，原暂缓/取消项保持。

Debug入口只增加正式DI下载/解密/Bridge回读，main生产链不改。自动更新走withProfileSourceWrite的实际use引用判断；手动同步走applySyncedCurrent。验收在无Activity进程中先核对profileInputsApplied、profileProviders和Bridge epoch，再直接回读组，不用会ensureConfigLoaded的管理器节点查询补加载。正式开关在automatic屏障内暂停并加密记录；取消手动VM后join整个scope，再恢复选择/正式开关并按独占ID删除源、CUSTOM和其哈希/精确.tmp投影。

## 提交检查点与退出应用证据（2026-11-05，历史切片）

todo第2项保存/退出受控死亡窗口已验：规则、Proxies、策略组结构化及原始YAML、本地节点的意图落盘/正式提交窗口，实际引用配置的退出应用前后、父草稿清除前后及CAS冲突共15场景。准备测试命中门闩后保持存活，外部核对同PID后force-stop并确认消失；15次首次恢复、5次测试进程正常退出后的再次冷启动检查通过。恢复不重复写入，未提交等待显式重试，源变化保留旧CAS；退出应用调用计数在恢复后为0、显式重试后为1。

最终build7 App481/Service195/Core38/Common31共745单测零失败/错误/跳过，Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）；同套App/Test相关回归21/21，安装回拉哈希匹配。正式1份配置/逐字段摘要/选择/正文SHA-size-mtime、本地目录、原1.yaml双端、Flutter包及设备设置保持；临时行/正文与两个草稿Store的主文件/.bak/.new均已清除，结束无服务/tun0。

证据`docs/acceptance/editor-commit-window-redmi-20261105.json`，原始附件在外层`acceptance-artifacts/commit-window-20261105/`。这是受控force-stop，未推广系统自动回收/OEM；结构化保存走正式MainActivity的VM入口，本地保存/退出使用Compose点击。实际引用分支调用并返回applyCurrentProfile，核心保持STOPPED；不推广运行中VPN/流量、事务内部崩溃原子性或完整Flutter视觉。首批11场景后退出恢复断言误将输入加载当作退出重放；第二批13场景后父清除检查点未覆盖自动写入器，均保留失败及现场收尾，不与最终批次相加。第1/2项本组范围已验，第3至11项待办；下一组按第3项真实age在线解密与CUSTOM引用源更新应用推进。玻璃默认关闭，原暂缓/取消项不变。

Debug/release同FQCN检查点实现隔离：debug仅同进程一次arm，锁内记录命中，锁外NonCancellable挂起，无持久开关或导出入口；release no-op并经R8 DEX排除。Repository完成返回的检查点位于profileWrite lambda内，早于核心管理器后续应用，不能把文件rename或外层profileWrite返回混同。父草稿恢复等待信号只读；退出应用前后计数只标记真实引用分支，首页普通getProxyGroups→ensureConfigLoaded不记作退出重放。

## 规则YAML加密UI状态与剩余冷恢复（2026-11-05，历史切片）

规则快捷YAML的正文/选区、错误与放弃确认已进入现有加密保存树，仅正文修改清错；异步校验冻结正文，保留原CAS/关闭守卫。

App481/Service195/Core38/Common31共745项零失败/错误/跳过；final1统一Debug/Test、未签名Release/R8及双Lint通过，各0错误/66警告（Debug10/Release9提示）。Redmi最终同套App/Test六场景各prepare/verify通过，外部严格核对PID消失与恢复PID不同；相关回归18/18，定向绿测独立1/1。31张PNG/6拼图已目视，安装回拉哈希匹配；正式1份配置/原选择/逐字段摘要及正文SHA-size-mtime、原1.yaml双端、Flutter包和设置保持，结束无服务/tun0。证据`docs/acceptance/remaining-editor-cold-redmi-20261105.json`。

todo第1项本组范围已验；第2项保存意图落盘后未提交、已提交未清草稿及退出死亡窗口仍待真机。第3至11项保持待办，玻璃默认关闭，原暂缓/取消项不变。原始附件改存工作区外层acceptance-artifacts/remaining-cold-20261105，未随源码上传。

## Proxies/本地代理加密持久恢复（2026-11-05，历史切片）

`ProfileEditorDraftSnapshot` v3纳入Proxies原CAS基线、正文/选区UI树及pendingContent；本地目录使用named `local-proxy-draft`独立Keystore AES-GCM/noBackup AtomicFile，快照保存opened/expected/seenNames/pending/pendingClose。正式导航由恢复后的isOpen驱动，节点、集合和分享链接窗口有独立注册表。Release保留新增Gson类型及Pair，mapping/DEX另验。

配置与目录写入前先持久保存意图，恢复只比较正式正文/目录确认是否已提交，未提交等待显式重试，不自动重放。退出先落关闭意图，再应用/清草稿；同写锁和epoch/session/closing拒绝旧写入复活。checkpoint返回false阻止保存；本地UI捕获只按当前session合并uiDraft，不回写旧集合/错误。Noop store跳过空持久化，保留无持久层的同步关闭语义。

初次read结束前的新开入口延后；metadata及子页读取返回后复核最初generation/epoch。store或源正文暂时读取失败保留旧密文并禁止collector/显式写入覆盖，重试需先恢复完整快照。Proxies参考Flutter解析后的节点定义，在完整替换结果校验顶层name/type及重名，保留跨分区anchor、行内map、顶层flow列表及其它分区原文。

745单测、最终10场景双阶段/14项回归及Proxies正式宿主保存回读见`docs/acceptance/local-proxy-draft-cold-redmi-20261105.json`。规则YAML/保持打开的类型选择器、策略组成员/选择/排序/批选及保存中/落盘后UI收尾前死亡窗口仍待真实冷验。在线age/CUSTOM引用源、其他资源SAF/外部脚本、Provider剩余来源/故障恢复/规则命中、质量来源/持久缓存、Fallback/蜂窝/第三方OEM和第三步视觉/性能保持待办；玻璃默认关闭，原暂缓项保持。

## 规则/策略组加密子树与提交恢复（2026-11-05，历史切片）

`ProfileEditorDraftSnapshot` v2纳入rules/groups的正式入口、原CAS基线、纯值UI树和pendingContent，继续复用Keystore AES-GCM/noBackup AtomicFile。`EditorStateRegistry`只接受明确值类型/标准MutableState policy；状态变化、ON_STOP与保存前checkpoint捕获，不向Activity父保存树登记。Compose的Dialog新建ComposeView会覆盖注册表，`rememberEditorWindowRegistry`以可恢复UUID命名空间隔离窗口，`ProvideEditorWindowRegistry`在各窗口内容中重新提供。

LazyColumn内部`DefaultLazyKey`会出现在保存树Map键中；只接受精确类，校验Class并读取index:int，以整数编码后用真实int构造器恢复，Release保留该类字段/构造器；不开放任意Parcelable或Bundle序列化正文。真实类单测和Release keep/mapping/DEX核对已有证据。

保存前同写锁落盘pendingContent，恢复比较磁盘正文确认是否已提交；未提交保留规则操作供显式重试，源变化保留旧基线使CAS拒绝覆盖。collector拿锁后重取最新快照，防排队旧快照擦pending；关闭以UNDISPATCHED+NonCancellable等写锁清子页后才关闭UI。generation/epoch/closing守卫覆盖迟到回调和恢复读取。

726单测、最终8场景双阶段真实冷进程、15/15旧入口及父正文两阶段各1/1见`docs/acceptance/structured-editor-cold-redmi-20261105.json`。当时配置内Proxies/本地代理仍VM内存（现已由顶部新组覆盖），规则YAML/打开的选择器、策略组成员/批选和保存死亡窗口未全面冷验；不推广全部编辑器或Release真机运行。

## 后台订阅提交与受影响配置应用（2026-11-05）

ProfileRepository先在核心锁外下载、解密、校验；automaticCommitter通过Provider延迟取得唯一CoreRuntimeManager，避免runtime→repository→runtime的构造环。withProfileSourceWrite只应用当前源或CUSTOM正文中实际use引用的稳定资源ID；无关资源不重载。固定锁序automaticUpdateMutex→transitionLock→fileMutationMutex，手动updateRemoteProfile保留原调用语义。

原子文件/Room提交时同事务写subscription-pending-apply摘要，绑定来源、密钥、更新时间和正文SHA；核心应用失败独立反馈，保留新源与旧核心，下轮先重试应用，不重复下载。改源或正文版本变化使旧pending失效。关闭自动更新只阻止未来批次选择，不取消已经受理的下载；显式冻结批次可用withAutomaticUpdateBarrier等待旧批退出。隔离仓库拥有observationScope，取消并等待观察者后才能关库。

Worker记录最近16次work ID、attempt、时间、到期输入和结果ID，不记录地址、标签、正文、密钥或异常原文；同步落盘保证正常返回前审计存在，取消或整体异常未返回时可能无记录。

718项单测、最终Redmi回归7/7与自然周期独立1/1、正式数据保护及安装证据见`docs/acceptance/subscription-auto-cycle-redmi-20261105.json`。自然更新为既有生产20分钟调度中的单次到期流程，t0到t1为25分40秒，不证明严格周期、上游正文变化或在线age密文；CUSTOM引用源实网全链路仍待验。

## 策略组与本地代理草稿、服务拖回（2026-11-05，第一步）

整组YAML与单组图标/选项子页各有保存状态；返回解析成功只更新父草稿，失败可继续或放弃。ProxyGroupDraft统一包装分区与正文错误行列换算。本地代理列表/映射放在LocalProxiesViewModel内存，父窗口session拒绝旧回写，凭据草稿不写Bundle；隔离宿主真实recreate已验，冷进程另列。

SheetBackGesture按Flutter位移/归一化速度收尾，DashboardModalSheet以独立父页揭露、页对标记抑制本次重复返回动画，后台清除迟到提交；450ms切页采用Flutter分段缓动。ServiceStatusPanel保留最后quality快照，选择清空后退场页仍绘制原正文。LTR触摸、取消/快甩/反向、后台中断、下一次普通返回与管理滚动由Redmi44/44覆盖；RTL运行及仅PAUSED边界未验。

715单测、构建、安装/保护与视觉边界见`docs/acceptance/stage-one-interactions-redmi-20261105.json`。下方旧切片的“尚未实现”仅代表当时状态。

## 主页面子页状态与退场隔离（2026-11-05）

该历史切片以规范化规则摘要和行key恢复身份，正文由当前源重建；正式入口现由顶部加密注册表隔离，正文和pending行只进入密文草稿。重复内容按原索引定位，原提交回读匹配才关闭子页；源已变更且旧行不可定位时拒绝误新增。

GroupQuickYaml返回解析成功只更新父组草稿；失败保持正文并提供继续/放弃。ProviderQuickYaml提交收焦点/IME，失败按尝试次数回顶，重复相同错误也可见。

DashboardModalSheet以pageKey/depth保存标题、正文及滚动状态，450ms横移四分之一宽；ServiceStatusPanel按页key生成独立Compose内容/操作lambda，避免离场树被下一页内容覆盖并丢失Lazy列表状态。只由活动页回报自然高度。ConnectionModalSheet退场统一消费触摸/按键，系统返回也不再触发子页业务。边缘拖回尚未实现，完整Flutter缓动/逐像素一致性仍PARTIAL。

最终源码验证见`docs/acceptance/main-subpages-recovery-redmi-20261105.json`；保存状态重建不等于冷进程或实网验证。

## 菜单背景光学与可逆层栈（2026-11-04）

- `AppMenuSource`的PixelCopy结果通过`prepareAppMenuBackdrop`在Default后台降采样/三次盒式卷积，分离raw/frosted；取消每16行检查。`AppMenuGlassRenderer` API33+用RuntimeShader/BitmapShader绘制连续圆角SDF、折射/bevel及goo高光，窗口坐标与父层paintScale参与背景采样；API27离线捕获与fallback已在手机AVD验收，API24/25、31/32和其他ABI未验，API27设置恢复不能由证据JSON确认。
- `AppMenuStackState`保留Level/Owner及每层ScrollState/key。新卡从owner触发行按分段曲线展开，父卡缩小/压暗/减影；同一Animatable收回可反向，退完才remove。外部祖先同步不可被用户返回误反向，标题复用触发行并旋转箭头。后层清语义/禁用命中，露出父卡只返回一级。
- 交互clip与开场外层morph相交，释放使用最后真实落点重新命中，hitRevision使动画/滚动后重新计算；`maxValue`必须在组合读取后发布可滚动性，不能只在SideEffect里读而漏掉首帧测量变化。回源t=0保留全部材质/blur/sourceAlpha，稳定卡与开场光学层互斥。
- `ProfileFormRow`承载56dp同行标签/值与右侧编辑，`ProfileFormSection`允许按页面关闭默认内边距/间距；本地代理认证/可选分组、Provider选项与资源引用仅改排版，不改保存、草稿与核心边界。共享regular/pill/menu/alert/tinted/lens材质及STANDARD/ULTRA等级可复用，默认玻璃仍关闭；策略组/更多/链式代理覆盖子页已验，其他页面和完整全局parity仍PARTIAL。三次盒式卷积只是预模糊近似。

## 源快照菜单与代码正文视图（2026-11-04）

- `AppMenuSource`记录源按钮GraphicsLayer，在内存快照成功后交接原绘制；没有快照保留普通200ms退场。`AppMenuMorph`/`AppMenuGoo`负责开场、从中断帧回源及距离场桥接；`present`独立保留500ms回源图层，不能依赖未发生透明度变化的AnimatedVisibility生命周期。
- `AppDropdownMenu`在退场完成且宿主仍STARTED、原expanded仍有效时才执行待办操作；ON_STOP撤销，源销毁恢复绘制。子菜单旧层移除语义并禁用命中，`opensSubmenu`只表示导航停留，普通保持打开的开关不触发400ms导航。
- `AppMenuCarry`区分长按、跨slop拖动、释放与取消；源手指未释放前Popup不抢Window焦点，源持续更新Window坐标。语义长按没有物理指针，不进入携带状态，由Popup接管系统返回。可滚动菜单不抢竖向手势；取消路径撤销悬停，不执行最后条目。
- 玻璃开关默认关闭，开启时用宿主Window的PixelCopy内存背景，先隐藏源绘制后抓帧；API33+使用折射shader，旧API保留fallback。API27离线材质、Window捕获、菜单与子页已通过；API24/25、31/32、其他ABI及低API网络/VPN/核心路径未测。
- `ProfileCodeField`用显示span保持OffsetMapping.Identity，行号独立Canvas；超过512Ki字符跳过着色扫描。查找选区显式驱动滚动，预览行号DisableSelection；正文只返回父草稿，不增加直接文件写入。BasicTextField仍整体测量，极限正文性能另验。
- 排序列表保留既有暂存/真实拖动链路，视觉统一72dp行和单尾部拖柄，只由真实长按拖动调整；身份验证分组和Provider选项不改变协议字段/保存边界。

## Provider列表路由重读与默认视觉（2026-11-04）

`profileProviders`在3秒总预算内最多读取3次；`ProviderRouteMovedException`仅表示同一epoch内路由version变化。丢弃旧列表后，在transitionLock内复核原配置/正文key/修订号和epoch，重新取stamp；任何源输入、会话或生命周期变化仍失败，更新/写入守卫不放宽。3项Service回归覆盖单次变更后返回新结果、连续变化有界失败、跨epoch/正文变化不重试。核心写入协议未改。

默认颜色/字体由Flutter oracle导出；`AppGlyph`保留body/detail/line、透明镂空和RTL，`AppMenuMorph`分离位置/内容缩放/高度/角部弹簧；空态使用真实cookie曲线。`tool/export_flutter_visual_reference.py`在独立/tmp工程从固定上游SHA生成参考，未恢复Flutter运行时依赖。完整视觉仍PARTIAL；验收与公开加速源见最新testing/roadmap及`flutter-visual-provider-types-redmi-20261104.json`。


## 主导航连续滑动与代理标签（2026-11-04）

`MainNavigationHost`在外层Scaffold固定Dock和snackbar，消费底部insets；`MainPageTransition`按目标序号/RTL选择方向，以300ms easeOut移动完整标题和正文。各页由SaveableStateHolder保存布局状态，退场页清语义并消费触摸，非相邻入口只构建起止页；快速反向及草稿/滚动恢复已有真机帧断言。`MainNavigationDock`再次轻点当前项也调用路由，工具子页返回及未保存检查统一走navigateTo；原地拖动和取消不提交。

代理标签宽度由同一字体测量，居中偏移包含16dp起始padding，首帧直接定位、后续滚动；末端24dp使用DstIn渐隐，接近末项时恢复不透明并适配RTL。点击目标立即高亮，取消旧job后generation保护最后目标；Pager落定才持久化组名。配置与代理72dp工具栏、操作胶囊及正文间距共用现有颜色/形状，主题与完整图标parity未在本组替换。

Fallback真实Bridge协议边界通过，但授权订阅与本地原1.yaml均未提供本次可验的非空Fallback。原1.yaml全部90个HTTP规则资源已分三次实网回读（32+55+3）；Go批量更新实现未改，首轮失败原因未知。结果、失败与安装范围见`docs/acceptance/page-continuity-providers-redmi-20261104.json`。


## 主页详情的自然高度与停靠（2026-11-04）

`DashboardModalSheet`复用`ConnectionModalSheet`，标题/操作/返回/关闭位于滚动正文之外；滚动修饰符内部测量正文自然高度，加标题、48dp拖柄和导航安全区。短内容fitsContent，长内容9/16与0.9双停靠；内容增减按原弹簧重排。`ConnectionSheetGeometry`重合停靠点只保留PARTIAL/HIDDEN，拖甩只从有效锚点选目标；原连接详情默认轮廓和行为保留。

配置切换保留LazyColumn，依据可滚动性选择fitsContent并在退出动画期间只认领一次提交；服务详情/质量各有滚动状态，管理页保留Lazy排序，只有列表顶部且未拖动条目时允许外层接管下拉。Dialog系统返回可先回子页，关闭仍经面板退场；内存回收固定标题栏，IP质量分组加载保留行/旧结果，隐藏IP整行合并开关语义。主页四入口采用已有准确连续圆角，不把原连接近似轮廓宣称为精确parity。

本组Providers只增真实页面验收方法，生产读取预算/更新清理沿用前组实现。90项真实列表两资源UI更新、后台恢复、三分钟轮询与重建通过，原分钟级现场本次未复现；全部资源下载和唯一根因仍未确认。构建、分批26/26与2/2、安装及保护边界见`docs/acceptance/dashboard-sheets-redmi-20261104.json`。

## 主页面共用菜单与 Provider 更新清理（2026-11-04）

`AppDropdownMenu`供配置来源、配置卡片、代理工具/组选择和节点菜单共用。全窗口透明Popup区分外部点击关闭与Back/Escape返回子菜单上层；ON_STOP关闭。菜单按真实Window锚点、安全区和IME定位，长按优先避开原卡片，子菜单尺寸变化固定右上角；内容超高时内部滚动。卡片旁听Initial落点而不消费手势，并接通Shift+F10/菜单键。配置写权限、导出忙态、代理配置/运行态/刷新变化使旧菜单失效；卡片显式裁剪防止长按涟漪越出连续圆角。

`ProfileStatisticsViewModel.updateProvider`对齐Flutter `ProxiesAction.updateProvider`的finally清理：超时和取消也清除inFlightName/updatingNames，防止已结束任务留下进度圈或污染下一份配置。超时显示“更新超时，请重试”并登记失败项；真实协程取消仍向上传播，不向新窗口发布旧提示。它与既有8秒Providers列表读取预算分别覆盖更新态和加载态，原始分钟级现场缺失，不能认定唯一触发原因。

该切片验收的是浮层底色、连续圆角、阴影、弹簧缩放/淡入淡出和禁用语义；随后已补齐共用液滴回源、滑动携带、玻璃折射/多层展开与API27 fallback，细节见本文件前述最新状态及`docs/acceptance/general-glass-subpages-api27-20261104.json`。整体页面parity仍PARTIAL。

## 主页面连续圆角与绘制反馈（2026-11-04）

`AppSuperellipseShape`沿用Flutter engine八分弧参数，统一Dashboard、配置卡片/表单、代理节点和List组合边角；保留四个逻辑角及RTL映射。独立Flutter `Path.addRSuperellipse`输出7种轮廓的像素中心包含区间，Android按行比较LTR/RTL端点，栅格边界最多允许1px差异，不能由普通圆角截图自证。Flutter BSD声明随APK assets保留。

`AppSpring`在连续更新目标时保留速度，`appElasticPress`只变换绘制，静态命中/语义/菜单锚点不随按压膨胀；短按先升至0.975再回落，关闭动画时收敛到目标。`appFocusRing`跟随子树焦点，选中本身不画焦点。Dashboard投影使用Flutter多层参数，配置与节点沿各自卡片反馈，不给CommonCard额外套按压。

`MainNavigationDock`单一指针受理拖动和释放，滑块/抬升/拉伸/形变分别用物理状态更新；取消合成的已消费抬手退回当前入口，不提交导航。语义入口仍由selectable提供，外扩8dp命中区和绘制增长不移动入口语义矩形。代理页刷新保留原列表，同时统一禁用Tab/List的选择、菜单和测速，并显示忙态；组图标按完整字素取首项，显示副本修复非法代理字符，保护显示字符有效性。构建、真机与视觉边界见`docs/acceptance/main-visual-redmi-20261104.json`，完整页面parity继续PARTIAL。

## 代理选择隔离与详情读取预算（2026-11-04）

ProfileRepository在Room事务中只patch当前代理组映射并合并最新其他组/元数据。ProxyGroupsViewModel仅实际输入变更使generation失效，revision不丢保存，重复选择同步认领；页面回调携原渲染Profile ID。管理器的读取/切节点/单与批测速在transitionLock内复核原ID，代理Bridge读取5秒、页面独立8秒等待，首页摘要与代理页各有失败重试。

ProfileStatisticsViewModel的窗口身份只含前台/配置/运行状态；5秒tick、revision、retry合流并conflate，revision不取消正在读取的任务。独立viewModelScope读取涵盖核心只读及managed bindings，页面8秒后返回失败并取消读取等待；底层不可取消阶段结束后不能覆盖旧页面或阻塞新配置。同配置Providers刷新保留列表，失败暂停自动读直到显式重试或窗口身份变化。该路径不下载或重载配置。三个旧实现红测/修复绿测与90资源真机窗口见本组验收JSON；整体UI仍PARTIAL。

## 脚本写入与全局规则生命周期（2026-11-04）

ScriptRepository通过延迟Provider取得唯一管理器，下载锁外、提交固定transitionLock→fileLock。withScriptWrite在提交前识别实际scriptPath使用者，提交后应用；删除解绑也刷新原使用者，无关脚本/排序不重载。ScriptDocument保存目录/正文digest/mtime，更新和下载提交CAS；原子rename失败不覆盖，Room失败恢复原字节/mtime，文件删除失败回滚绑定。ScriptsViewModel仅挡启停过渡态，ProfileApplyAfterSaveException携目标ID表示源已提交，关闭编辑并保留独立应用重试；资源总览区分同类失败。Provider保存把打开时proxyProviders基线带入Room事务，只比较该分区并合并最新其他覆盖；metadata-only不改正文。GlobalRulesController经withExtensionWrite共持提交/应用锁，仅受理目标仍当前时应用，切换不重选旧项。实际证据见advanced-runtime-redmi-20261104.json，后续首页/代理组及视觉仍待推进。

## 文件选择器冷恢复与核心通知时机（2026-11-04）

根部稳定注册FILE/remote、脚本、Provider launcher；系统受理前持久化加密令牌，结果等待VM恢复再按原目标单次消费，无对应launcher的孤立pending解锁。Provider快照只存profile/name/输入与缓存摘要，冷进程重新建立核心binding；保存后核心回读，已提交而回读失败仅允许重读确认。

`ensureConfigLoaded`仅在校验后以NonCancellable完成已受理setup、模式与loaded keys登记，再invalidateIpRoute通知观察者；刷新取消等待也不能丢失Go已完成输入。此前通知在登记之前会触发ProxyGroupsViewModel取消自身加载、统计/Provider collectLatest反复重置。Flutter参考setup.dart的登记后onUpdated语义，两个gated异步setup回归覆盖。详情保存ID在初始空Profile快照时保留。只读运行预览仍在后台/宿主销毁清除正文，不套用父编辑草稿恢复规则。

## 配置提交、删除与加密草稿闭环（2026-11-04）

- `CoreRuntimeStatusProvider.withProfileWrite`由真实管理器持启停锁执行受理后的NonCancellable提交，再重读真实当前ID并应用；页面不在保存与应用之间释放生命周期锁。`ProfileApplyAfterSaveException`仅表示源已提交，VM返回已保存结果并保留独立应用重试，避免把应用失败误当回滚成功。
- `saveCustomDocument`沿用文件共锁、原正文CAS、Room内最新行合并和失败补偿；`patchProfileOverrides`在Room事务中读取最新覆盖，只修改DNS/链式代理分区并前后检查会话，DNS重试比较打开时基线。`reflectStructuredSave`清除过期父正文草稿，防止旧YAML覆盖已保存分区。
- `removeProfile`先成功应用fallback再删除，删除事务失败尝试恢复旧项，恢复失败停止核心；无fallback先停止。Repository先提交Room删除/最终选择再清正文，管理器不重复持久化null选择，避免成功删除后误报失败；失败提示包含核心已停止状态。
- `ProfileEditorDraftSnapshot`保留父表单、正文子页、打开时Profile/正文指纹、替换字节与picker令牌。`AndroidProfileDraftStore`以Android Keystore AES-256-GCM加密，AtomicFile存于noBackup；正文/密钥不入SavedState或备份。epoch与同一写锁拒绝迟到快照，关闭以UNDISPATCHED启动不可取消清理后再关闭UI。picker暂存完成才启动，系统启动失败归还令牌；loading快照不冒充已读取内容。
- 配置排序沿用`ProfileReorderDialog`符号但以`ProfileEditorPage`整页呈现，共用`ResourceOrderList`长按/边缘滚动；本地草稿与增删ID合并，保存成功revision驱动返回，失败留页重试。
- App414/Service182及Redmi分批15个不同方法最终通过，父/正文在后台am kill后新进程恢复；安装与失败证据见`docs/acceptance/profile-closure-redmi-20261104.json`。真实DocumentsUI两路径已验，选择器等待中进程回收再返回尚未单独E2E；运行态原1.yaml同语义保存不代表全部高级分区/脚本或外网路由变更。整体Profiles/UI仍PARTIAL。

## 配置正文父草稿与本地副本提交（2026-11-03）

- `ProfileDocumentSnapshot`持有打开时Profile、正文指纹及mtime；`profileDocumentText`统一非空、8MiB、严格UTF-8/BOM/NUL边界，敏感正文不进入toString。URL/FILE使用同一快照保存路径，CUSTOM继续分区编辑。
- `ProfilesViewModel.documentEditorState`独立于父表单，正文确认只更新父draftContent；会话generation和正文session拒绝迟到校验。远程表单草稿由VM持有，Activity重建保留；系统picker令牌绑定配置/代际，字节冻结后暂存，取消不落盘。完整应用进程草稿恢复尚未证明。
- `ProfileRepository.saveProfileDocument`先解密/校验，再以文件共锁检查原SHA-256/mtime，Room事务内重读最新行，比较表单所属字段/更新时间并合并最新选择、组记忆和其他覆盖；提交前后复核守卫，NonCancellable完成提交或文件补偿。元数据only不重写正文/mtime，FILE类型和远程来源各自保持。
- 远程正文/文件替换外层保存可在稳定RUNNING受理，过渡态拒绝；保存后复用管理器应用当前配置，禁止先重新下载覆盖本地修改。远程页面对自动更新覆盖作明确选择；保存成功和应用失败分别反馈。FILE/CUSTOM结构化运行态安全保存仍待后续，不能由远程路径推广。
- 最终App409项/Debug/AndroidTest/Lint和Redmi精确12项通过；运行态Host只用原1.yaml字节证明提交/应用与会话计时保持，不证明变更节点/路由后的实网行为。安装与保护证据见`docs/acceptance/profile-document-redmi-20261103.json`，整体Profiles/UI仍PARTIAL；旧同日章节是此前切片边界。

## 配置运行态订阅操作与批量同步（2026-11-03）

稳定RUNNING开放首页/配置单项与批量同步、排序、重命名和远程订阅设置/自动更新，过渡态暂禁。VM四并发、同步认领busy、逐项进度/汇总/失败项重试和独立应用重试已接通；混合重试保留已保存项及应用失败。同步后只应用当前选中ID，不切下载目标。仓库元数据读改写改事务内合并；下载锁外，文件提交共锁+原字节/mtime校验+Room事务，DB失败恢复，改源/删除/迟到同源拒绝。

最终build2通过App406项、Debug/AndroidTest/Lint，0errors/62warnings/9hints；Redmi精确8/8含三项隔离原1.yaml存储竞态、结果页深浅和正式运行态设置保存，运行计时/原正文保持。三图已目视，正式Profile前后count2/missing0，原选择/文件元信息及1.yaml双端哈希保持，最终两包安装匹配，结束无服务/tun0，旋转1/字号1.0。证据`/tmp/kclash-profile-sync-20261103-`。build1测试selectedMap符号错误与review发现混合重试丢应用失败均修复。Service/Core/Common/Go/Release未重跑；正式实网同步/下载后核心E2E未验。远程正文/本机文件替换、FILE/CUSTOM结构化运行态安全保存、通用YAML编辑预览/排序整页等下一组继续，不能称全部运行态编辑完成；整体PARTIAL，未提交/推送。


## 按需连接首版与真实SSID运行链路（2026-11-03）

前后台精确定位分步授权、SSID精确管理/排序/批删、草稿快照重建、VM保存结果和存储CAS已接通。应用级带位置信息回调独立于被暂停VPN；同网络回读窗口最多3秒，失权/全断网清名称。管理器保留RUNNING意图与计时，独立suspended流控制两服务/核心listener暂停恢复，手动停止不自启；暂停时配置投影不重启服务，出口走DIRECT并失效缓存。v6可选SSID归档兼容旧缺项、显式清空和失败补偿。

最终build2通过App402/Service179共581项与Debug/AndroidTest/Lint，0errors/62warnings/9hints；Redmi最终8/8，真实当前SSID匹配/规则移除触发实际VPN和端口监听释放/重建、计时不变、手动停止保持通过。四图已目视，正式Profile前后各1/1且count2/missing0，原选择/文件元信息/1.yaml双端哈希保持；定位权限与flags、SSID/VPN偏好恢复，结束无服务/tun0，旋转1/字号1.0，最终两包安装哈希匹配。证据`/tmp/kclash-on-demand-20261103-`，final-*；首批组件恢复测试6/7的Dialog宿主问题已修，最终8/8。系统授权弹窗完整E2E、后台切网/进程回收/FGS恢复及完整视觉仍未验。下一组配置运行态编辑/同步与批次汇总，整体PARTIAL，未提交/推送。


## 脚本库整页与导入改源（2026-11-03）

- `ScriptsPage.kt`沿用旧组件符号但改为`ProfileEditorPage`整页：添加来源菜单、整行编辑、本地/远程与最近更新、逐项进度、操作菜单、删除绑定提示；排序复用`ResourceOrderList`的长按/边缘滚动/取消保护。正文编辑提供查找、替换、撤销/重做和保存/放弃/继续编辑退出确认；不再使用限高脚本列表弹窗。
- `ScriptsViewModel`持有正文与原版本、远程表单与原Script、错误/忙状态。同步认领写入，generation拒绝关闭后的迟到读取；保存使用原版本CAS，失败留草稿。读取失败禁止把模板保存回原文件；Activity重建保留两种草稿，完整进程重建关闭未提交编辑，不把缺失列表目标当成新导入。
- SAF令牌保存在窗口状态，待选期间VM认领busy；取消归还，重复/过期回调不提交，IO线程读取显示名及有界流。`ScriptImport`对本地/远程统一严格UTF-8、BOM、非空/NUL和2MiB边界，保留正文空白；文件同名分配`-01`等后缀，原绑定不变。
- `ScriptRepository`创建/编辑/删除/排序共用目录锁；写入检查实际Room目录，不依赖迟到的观察流。改源先锁外下载，再复核原名称/源/版本，一起提交地址和正文；失败保留旧地址/正文。编辑CAS忽略纯排序变化，提交阶段再次核对完整元数据；受理后NonCancellable完成文件/Room或补偿。排序要求完整ID排列。
- 运行中脚本修改仍沿既有停止态限制，自动应用/运行态安全编辑、完整进程草稿与SAF系统完整E2E尚待继续；本轮不改变核心脚本执行沙箱或跨备份写者的一致快照范围。

## 备份 v6 与工具备份页（2026-11-03）

- `ProfileMaintenancePage`按Flutter分远程/本地/选项；账号编辑独立整页，密码只保留当前会话，不进入rememberSaveable，并使用密码键盘防止IME候选回显；常规密码/密钥输入沿用同一约束。保存/删除回读凭据成功后关闭；远程文件名独立编辑，上传先明确覆盖，恢复共用范围与持久策略。本机Keystore凭据不写入ZIP。
- `BackupRepository`增加同实例operationLock串行化导出/恢复，v6全量归档新增`application_settings.json`；仅配置归档不携带。内容为GeneralSettings、NetworkSettings、全局DNS、VPN开关、LAN可空覆盖与恢复策略；显式空列表/空监听/false/null分别保留。v1–v5没有此项时保留设备当前设置，v6全量缺项拒绝。
- `ApplicationSettingsBackupCodec`先检查JSON字段及原始类型，再调用业务校验；避免Gson把字符串静默变成布尔/端口。恢复设置不调用prepared生成新认证；归档中的凭据按已保存值恢复。设置快照toString脱敏，源Profile仍按既有归档恢复。
- 生产恢复在`CoreRuntimeManager.withStoppedDataRestore`启停锁内完成，仅STOPPED/FAILED且无Geo更新时受理；不加载新配置、不启动服务。结束或补偿后从落盘值刷新常规/网络/DNS/Geo/VPN/LAN状态，失效旧配置缓存和路由，通知hooks同步；页面不用等重建才能显示新值。
- SharedPreferences设置写入位于Room事务末尾；失败回滚Room/文件，并逐项补偿设置，单项补偿失败不会跳过其余偏好。异常保留suppressed原因。此补偿不等于跨文件/Room/全部其他写者的原子事务；订阅自动更新、资源目录与配置编辑仍有各自写锁，全局并发快照待后续。
- SAF读写改IO线程并共享忙状态，取消选择归还忙状态；本地归档流64MiB限制，解压条目16MiB、总量128MiB/4096条目上限。手动WebDAV上传与本地导出均允许运行态，恢复仍需停止。BackupUiPreferences保存恢复策略，归档也包含该选择。
- 自动云同步不进入本轮；其他未纳入v6的界面/按应用代理等偏好、SAF等待期间完整进程/Activity重建恢复、外部WebDAV与发布版验收仍待独立推进。

## 常规设置与请求策略（2026-11-03）

- `GeneralSettingsStore`独立持久化应用行为、请求策略及核心常规参数；生产DI共用一个store，Gson类型保留发布混淆规则。认证启用时空账号生成凭据，控制器只监听127.0.0.1:9090并补密钥；设置对象toString脱敏。
- `CoreRuntimeManager.updateGeneralSettings`在transitionLock内比较旧快照、校验归一化并写盘。runtimeKey变化才应用当前Profile；通知/日志/测速超时/统计口径等应用参数独立更新。任何应用或日志订阅失败恢复偏好、旧核心和hooks，补偿失败停止服务并回读实际偏好。配置投影顺序在网络后、LAN前加入general，LAN仍是缓存键末项；预览冻结general快照并拒绝迟到结果。
- 五端口、控制器/认证、查进程、日志级别、Hosts、UA与追加系统DNS只改暂存输入，不回写原订阅。单次与批量测速以0表示使用保存超时，显式参数仍优先；切节点后按设置关闭旧连接。流量统计参数同时传给核心与通知，收集日志开关控制核心日志监听。
- Subscription/Script/规则与图库下载、WebDAV每次请求冻结UA和证书策略；宽松TLS只修改本连接或克隆的OkHttpClient，不改变进程默认。Core.setupConfig同时接skipCertVerify，证书开关变化重新应用配置。
- `MainActivity`按当前任务ID更新最近任务标志，使用API24兼容的RecentTaskInfo.id。自动运行由retained MainViewModel一次认领，复用既有权限和启动流程；退出最小化退后台，否则统一停止成功后移除任务。常规端口复用ProfileEditorPage整页表单和IME安全区；UA/Hosts独立子页，保存失败保留草稿。
- 自动检查应用更新尚未接Kotlin发布链路；全局常规/网络/DNS设置尚未纳入备份。完整进程自动启动、退出行为和Release运行需另行验收，Activity重建通过不能替代它们。

## 全局 DNS 与独立附加规则（2026-11-03）

- `GlobalDnsSettingsStore`独立保存手写JSON，不依赖Gson字段反射。`ProfileDnsOverrides`补fakeIpFilterMode，空listen/空list往返不再变成null；null表示继承。`GlobalDnsSettings`约束Flutter普通配置十字段并结构化合并DNS分区，避免flow/引号写坏暂存YAML。
- `ActiveProfile.usesGlobalDns`由Repository根据CUSTOM投影；普通配置渲染顺序为脚本→标准扩展→baseline→全局DNS→既有Profile覆盖→网络→LAN→Geo，CUSTOM跳过全局DNS。manager预览冻结全局快照，cache key包含全局值且LAN仍最后；updateGlobalDns在transitionLock内写盘/应用/补偿，偏好补偿失败停止服务。
- `GlobalRulesController`不依赖Profile编辑窗口；目录保存同步认领并冻结当前ID，NonCancellable完成受理后的写入/应用；切配置不重新选择旧目标。保存失败保留旧列表/表单，应用失败保留新目录并单独重试应用。Repository新增Room事务CAS，旧ProfileExtensionController也改用CAS。标准/脚本/CUSTOM规则边界不变。
- 工具DNS子页返回高级配置；附加规则复用ProfileEditorPage独立整页、ExtensionRuleForm/Presets及ResourceOrderList。新全局DNS/网络设置还未进入备份；真实运行态新字段热改和完整进程重建未在本轮证明。


## 全局 Android 网络设置（2026-11-03）

- `NetworkSettingsStore`独立持久化网络参数，生产SharedPreferences与`MainViewModel`、`CoreRuntimeManager`、`DefaultVpnOptionsProvider`共用；Gson持久化类型有发布混淆保留规则。页面只提交不可变草稿，管理器复核预期旧值，所有写入和核心应用走transitionLock，受理后NonCancellable完成或补偿。
- `patchNetworkSettings`只改变预览/运行副本的TUN字段，保留DNS基线、认证、未知参数及订阅原文；configKey/automaticConfigKey包含网络设置，LAN覆盖仍在末位供既有热更新维护。异步预览冻结网络快照并最终复核。
- 数字IP校验后计算包含/排除CIDR差集，对齐Flutter私有地址列表；显式`VpnOptions.routeAll`区分无筛选与差集为空，Builder不能把全部排除变成接管全部。系统代理仍使用实际mixed-port并受认证/端口守卫。
- 网络修改复用配置应用/系统服务重建与回滚，保留当前会话VPN enabled（该开关仍显式重启生效）；应用失败恢复偏好，偏好回滚失败则回读实际值并停止服务。新全局设置暂未进入备份；全局DNS/附加规则与常规其他项待后续，不通过Profile编辑入口冒充全局设置。

## 外部 Provider 文档与批量操作（2026-11-03）

- 真实入口是首页配置详情；`ProfileStatisticsViewModel`保留前台轮询，新增批量busy/失败集合及独立文档会话。正文state与核心document不用data class.toString；文档generation与picker令牌阻止关闭/换配置后的迟到结果。保存同步认领，失败保留内容，系统选择器遮挡保留绑定；严格UTF-8/NUL与8MiB预算。
- Bridge新增scoped read/side-load/batch方法；私有路径仅在Bridge计算哈希供仓库匹配，原路径不进入页面。`CoreRuntimeManager`私有ProviderSnapshot绑定配置输入/缓存修订/核心stamp，批量与单项/侧载共门禁，网络锁外执行；NonCancellable等待已受理核心回调，完成后复核原会话再刷新路由/节点/统计。
- `provider_editor.go`在configMu内校验scope和文件digest，真实SideUpdate校验并写缓存。失败恢复原字节/mtime，恢复失败保留备份；缺失缓存用missing指纹。MRS dump输出受限，额外记录写入超限以处理上游转换器吞错。批量先冻结全部HTTP对象再锁外并发，逐项只返name/code。
- `ManagedProfileProviders`以原CUSTOM稳定资源引用与核心文件身份哈希共同识别应用远程规则，更新通过RuleResourceRepository生成新版本，最后只重新应用同一当前Profile。此接线有代码，实际远程同步/运行态路径仍待真机补验。
- `DashboardProfileDetailSheet`沿用旧符号/tag但使用ProfileEditorPage整页；Provider菜单、用量与正文页共享现有主题和安全区。文本可编辑，MRS只读，二进制受控提示。系统OpenDocument到真实核心写入完整E2E与完整Flutter视觉尚未证明。

## 本地代理分享链接编辑（2026-11-03）

- `LocalProxyLinkEdit`对照Flutter同名规划器，按链接逐次匹配旧行，重复链接每次只消费一个ID；保留行不解码，pending保留源文本行号，droppedLinks用于回读旧链接表示能力。批量同名替换仅在名称尚未占用时继承ID；冲突按上游`-01`分配，未命名`-01`等回退服务器名。不能编码的节点按最近保留行锚定，不随文本删除。
- `withUnlinkedProxyFields`仅在旧链接可回读且协议一致时递归合并链接无法表达的字段，链接已承载而新链接移除的键不恢复；无回读基线或换协议不混入旧设置。`fillLocalProxyFromLink`供单节点填充使用，同协议改名也合并，保留当前ID、避开其他节点/组/保留名，空名称优先沿用当前名称。
- `LocalProxiesViewModel`批量只解码pending与dropped，先校验解码定义，合并后再次校验改动节点；错误行号来自原文本，不展示核心原始错误。单节点从不直接落盘，也不覆盖父页expected快照，最终保存由Repository拒绝过期目录。批量确认展示新增和移除名称，继续编辑保留文本，写入中禁止取消确认状态。
- 本地目录排序复用`ResourceOrderList`视口手势和边缘滚动，只有松手提交完整ID顺序；取消/后台不提交，回读失败恢复已保存顺序，搜索时退出排序以免过滤索引写回全目录。

## 节点菜单与共享本地代理导入（2026-11-03）

- `ProxyGroupsSnapshot.providerProxies`仅投影有有效非空`provider-name`的节点名，不把完整定义或Provider私有名称传入页面。`ProxyGroupsViewModel`刷新和选择后投影所属Profile ID、CUSTOM状态及成员归属；内建、组和CUSTOM自有节点不允许添加，CUSTOM中的Provider叶子节点仍可添加。
- `ProxyNodeCard`长按菜单复用选择/优选入口与当前/固定状态，添加事件携带所属Profile ID；LoadBalance虽不能选择，仍允许叶子节点菜单与独立测速。切配置/组、启停过渡或禁用时关闭旧菜单，Tab/List沿用同一政策。
- `CoreRuntimeManager.getProxyDefinition(profileId, name)`在transitionLock内核验当前Profile，停止态加载实际输入，读取前后复核配置与自动输入指纹并核验loadedAutomaticConfigKey；拒绝过期菜单和同名串配置，不启动服务。UI经ViewModel调用管理器，不直接取核心定义。
- `planLocalProxyAddition`供节点菜单与CUSTOM导入共用：深拷贝并移除配置专属dialer-proxy，定义比较忽略name且递归将数值转字符串。同名同类型/服务器/端口/凭据账号复用本地设置，其余冲突避开已有节点、CUSTOM组及保留名分配后缀，保留未知字段。`LocalProxiesViewModel.addFromRuntime`原生校验新增定义、保存前复核当前ID，复用既有全局Snackbar反馈最终名称，失败不透出原始核心错误。

## 资源排序与组图标来源（2026-11-03）

- `ResourceOrderList`供规则资源和图库独立排序模式复用，以视口持有长按手势及按帧边缘滚动。取消/ON_STOP不提交，松手提交完整排列；新顺序回读保留视口索引，避免旧key锚定掩盖新首项。`RuleResourceRepository.reorder`和`IconSetRepository.reorder`在目录锁内校验完整ID集合，无重复/缺失/额外项，保留最新元数据与正文文件。
- `LocalResourceIconSets`提供完整只读图库；`LocalResourceIcons`保留旧测试fallback。`IconRecommendations`复用上游名称归一化、匹配分数/稳定排序、最多24项匹配后追加去重最近记录。`ProfileGroupIconPicker`后台生成来源，按当前图标定位图库；删除最近项只更新最近记录，配置依旧保存实际图标URL/data URI，不保存图库引用。无在线推荐目录。

## Provider 结构化整页（2026-11-03）

- `ProviderCatalog` 从 CUSTOM 的组 use、普通/嵌套 RULE-SET 及 DNS/Sniffer 引用投影列表；普通配置展示定义。绑定用稳定 Profile/规则资源 ID，配置别名独立保留。选项、过滤成员与快捷 YAML 各有草稿边界，保存失败留页，成功 revision 回读后回到资源列表。
- `ProviderFilters` 将过滤/排除分段混排，`memberLayout` 仅保存不能由自然顺序恢复的前缀，不传入核心。null 继承与显式空串清空分开；预设与自定义按选择顺序重放，撤回预设不重排其他条件。
- `ProfilesViewModel` 通过 generation 丢弃关闭后迟到的读取/校验，同步认领保存防止重复。`saveCustomProviders` 元数据only不重写正文/mtime；定义变化使用原子文件与Room事务，前后守卫失败回滚。普通Profile通过`saveProviderOptions`事务合并最新覆盖，保留DNS/脚本等其他分区。
- Provider暂存合并支持中文/转义键、flow/引号分区和内联健康/override map，保留未知字段；健康选项启用时补Flutter默认测速地址。原始订阅不回写，只有CUSTOM定义变更才写正文。分区编辑兼容原1.yaml的行内map，解析放入独立分区以隔离脚本codec默认map。

## 配置实时扫码导入（2026-11-03）

- `ProfileQrScannerPage` 管理扫码窗口、相机权限、相册选择与前后台状态。CameraX `ProfileQrCamera` 只绑定当前可见窗口的 Preview/ImageAnalysis；暂停时直接关闭会话，迟到初始化/识别不重新占用镜头。只解绑本会话用例，不调用全局 unbindAll。
- ML Kit 使用既有 bundled QR 识别器，KEEP_ONLY_LATEST 避免排队；识别结束后归还 ImageProxy，退出时等待正在读取的帧完成再关闭识别器。相册解析复用 `QrCodeImageImporter`。
- `DeepLinkProfileParser.fromQrCodes` 共用 HTTP(S)/install-config 候选解析，保留名称、跳过无效码。一次识别只交付一次；前台才打开预填订阅表单，用户确认后仍进入 ProfilesViewModel/ProfileRepository。无效码继续扫描，不记录原始内容。无相机/拒绝权限仍可使用相册。

## 过滤预设、批量 URL 与资源偏好备份（2026-11-03）

- `FilterPresetRepository` 在 Room preference 保存有界、有序的名称/表达式列表；核心 regexp2 校验后在目录锁内检查旧版本和重名。预设按反引号分段，只将表达式追加到策略组/Provider 字段，不保存预设引用。
- `ResourceUrlImportController` 共用四并发批次，整批先校验 URL，逐项保留成功/跳过/失败/取消状态。规则与图标仓库在下载前、提交锁内各检查同 URL；规则下载/编译在锁外，只有未提交的暂存会被回收。
- 备份 v5 的 `resource_preferences.json` 只随全量备份导出，包含图库、过滤预设与 Geo 设置。仅配置恢复不改这些全局偏好；v1–v4 没有此条目时保留现有偏好。兼容恢复按图库 ID/过滤名称合并，归档 Geo 设置生效。
- 图库/过滤/Geo 归档先做结构和容量校验，生产恢复还做核心过滤校验，再写文件和 Room。Geo 独立持久层在事务末尾写入，失败通过 NonCancellable 补偿恢复；其变化流只使管理器缓存失效，不在 Room 事务中获取核心启停锁。此流程不等于所有资源写者共用全局事务锁。

## 全局本地代理与配置链路（2026-11-03）

- `LocalProxies` 在既有 AppPreference 中保存有界UUID/完整定义；表单保留未知字段，原生validate/分享链接编码复用CoreBridge已有Go方法，Go不改。
- CUSTOM 源只保留名称引用。`toActiveProfile` 仅投影组显式引用的全局节点并形成哈希输入，旧inline节点优先；组候选可看全部global但不落源，规则候选/概览/链式页使用运行投影。全局definition不保存配置独有dialer-proxy。
- ProfileRepository 的文件锁内规划语义改名，嵌套原子文件替换与Room事务共同提交/恢复字节和mtime；复核目录、元数据与正文。旧同名组/inline节点拥有本配置名称空间，不随global误改。删除保留缺失引用；管理页关闭后通过唯一CoreRuntimeManager尝试应用当前引用配置。
- `ProfileDialerCatalog` 沿组成员与节点链检查循环，包含无过滤include-all；链式页逐项保存最新覆盖，保留其他分区。映射只属当前Profile，不修改global定义；快捷映射仍兼容既有文本入口。
- 备份v4增加local_proxies.json，兼容v1–v3；同ID已改名的compatible恢复另分ID，incoming重名只同步归档内引用，保护归档外配置。原有备份与文件并发协调边界未扩展为全局写锁，完整并发仍待补。

## 资源总览与 Geo／图标集（2026-11-03）

- `ResourceUpdatesRepository` 汇总 Geo 核心事件、规则文件、脚本和全局图标集，下载前认领资源 key，结束后重读真实文件信息；`ResourcesViewModel`/`ResourcesPage` 只转发操作和渲染。工具资源总览与自定义资源管理分开导航。
- `GeoResourceSettingsStore` 使用 App SharedPreferences；`CoreRuntimeManager` 持久化/热切并将全局设置补到运行/预览输入。Bridge 的 `geoUpdate` 才是异步下载完成依据，受理响应不能提前结束状态。Geo 下载不占启停锁，停止服务仍保留未完成下载和自动更新的事件监听。
- `IconSetRepository` 使用 Room `app_preferences.global_icon_sets` 保存有界图库 JSON 与稳定 ID，HTTP 使用既有有界客户端；下载后复核 URL/版本，允许并发改名。图库和推荐现通过`LocalResourceIconSets`提供完整来源，详见上方最新节；配置仍保存实际图标 URL。
- `ResourceAutoUpdateWorker` 与订阅任务独立；到期扫描不含 Geo、不启动 VPN，Geo 由核心 updater 执行。脚本下载提交在文件锁内复核版本，正文与 Room 同事务写入并在异常时恢复旧文件；脚本更新按顶部当前运行态规则处理当前绑定的应用。图标集/Geo 偏好已在后续 v5 备份接通，范围与补偿边界见上方最新节。

## Target modules

- `app`：唯一 Android application，包含 Compose UI、Navigation、ViewModel、Hilt 入口和 Android Manifest。
- `core`：Go Mihomo 的 gomobile AAR 构建和 Kotlin Bridge；只有 Bridge 可以调用生成的 gomobile 类。
- `service`：`CoreRuntimeManager`、`VpnService`、`ProxyService`、前台通知和服务间状态协作。
- `common`：跨 app/core/service 共享的纯 Kotlin 类型、错误、Intent 常量和协议数据结构。
- `data`/`domain`：先在稳定边界后拆分；Repository、Room DAO、UseCase 不让页面直接依赖底层实现。

## Runtime ownership

`CoreRuntimeManager` 是唯一核心生命周期所有者。它串行化 start/stop/restart/apply-config 请求，维护运行状态、配置版本、错误和恢复策略。UI 只能发送命令并订阅状态；Service 只能执行平台职责并向管理器报告结果。

系统撤销 VPN 时，`ServiceBroadcastReceiver` 只负责接收受签名权限保护的 `VPN_REVOKED` 显式广播并转交 `CoreRuntimeManager`；接收器不直接启停服务，避免形成第二套生命周期。

`VpnService` 创建和销毁 TUN、转发网络变化和前台服务状态；`ProxyService` 管理本地代理模式。两者共享核心管理器，不能各自启动第二个 Mihomo 实例。

## Core boundary

Go 核心继续从 `core/mobile` 生成 Android AAR。Kotlin Bridge 将生成类转换为稳定的领域请求、结果和事件，处理线程切换、错误映射、字节/JSON 编解码和资源释放。Compose、ViewModel、Room 和 Service 不直接依赖 gomobile 生成的类型。

## Data and state

Room 只承载 Kotlin 新应用的数据模型；Repository 负责数据库与领域模型之间的转换。ViewModel 通过 StateFlow 暴露不可变 UI State，核心运行事件、Profiles、代理组、连接、请求、DNS 和日志各自拥有明确状态源。

Profiles 的页面状态通过 `ProfileStore` 和 `CoreRuntimeStatusProvider` 依赖稳定边界，生产环境分别绑定 `ProfileRepository` 和 `CoreRuntimeManager`；测试可以替换这两个实现而不启动 Mihomo 或 Android VPN 服务。

## UI

Compose 使用 Material 3 作为基础，并由独立 Design System 保存 Flutter 参考实现中的颜色、字体、间距、形状、状态色和导航行为。页面不通过平台分支复制逻辑；差异应进入明确的 Android 手机布局策略。

## Profile 扩展与应用输入

- URL/FILE 的标准扩展保存在 typed `ProfileProviderOverrides.extension`：配置独有规则、稳定 ID 的全局规则禁用集合和显式模式标记；模式、脚本 ID、默认目标沿用 Profile 自有字段。全局规则定义保存在 `app_preferences.global_extension_rules`，完整备份使用 v2 并接受旧 v1 归档；仅配置恢复不改全局规则。Room schema 不变。
- 标准模式按本配置附加规则 → 启用的全局规则 → 原始规则合并；附加规则中的 `MATCH` 目标取显式默认目标或原配置最后一个 MATCH。脚本模式只执行绑定脚本，不叠加标准规则。源 YAML 不回写，DNS baseline、显式覆盖、LAN 继续作用于暂存输入。旧绑定未明确配置新模式时继续执行；CUSTOM 既有脚本行为保留。
- `ProfileExtensionController` 用串行队列处理元数据、显式应用和退出自动应用，真实应用只能进入 `CoreRuntimeManager.applyCurrentProfile`，并在启停锁内复核当前 ID；应用失败保留已保存编辑并由管理器恢复旧配置/服务。
- `loadedConfigKey` 表示已交给核心的完整输入，普通配置另有 `loadedAutomaticConfigKey` 比较源文件、非扩展覆盖和 LAN。后台节点刷新不能因扩展元数据/脚本变化提前加载；显式应用、选用或启动走完整加载。LAN 布尔热切只更新旧输入的 LAN 指纹，不把待应用扩展标为已应用；待应用时恢复继承 LAN 会提示先应用扩展，避免重新执行未提交脚本。
- `YamlConfigCodec` 通过安全构造器按调用创建解析器；Reader 在普通整缓冲读取时预留一格，供 SnakeYAML 2.3 在末尾遇到 UTF-16 高代理项时补读低代理项，避免包含 emoji 的节点名在扩展/脚本重序列化后触发越界。字符内容不变，源配置不回写。

## 更多分区编辑与暂存合并

- `ProfileMoreFields` 定义 NTP/Sniffer 字段、Flutter 默认值、裸分区 YAML 编解码及校验。父列表的添加/移除/开关逐项保存，无父页总保存；文本/列表子页确认后保存，失败留页。HTTP/TLS/QUIC协议页分别打开端口子页和目标选项，目标选中即保存，空端口表示协议默认端口。
- 持久化的空文本/列表用于保留尚未填写的设置入口，`effectiveJson`过滤这些空值后才进入核心；false仍生效。移除或确认清空字段恢复继承，空sniff映射不禁用源协议。暂存合并继续保留未知字段、未选协议，并处理旧sniffing/port-whitelist和大小写协议名。
- `ProfileMorePatch`按before/after差异合并最新JSON；协议内端口/独立目标各自合并，保留另一个字段和未知参数。`ProfileRepository.saveMoreOptions`在Room事务内重读整行、检查CUSTOM类型和前后写入守卫，仅改覆盖元数据，不重写源文件/mtime；会话或运行状态变化使事务回滚。
- `ProfilesViewModel`同步认领保存，复核分区/父编辑窗口代际，revision确认后才关闭对应子页；失败保留输入并可重试。有效快捷YAML返回保存，无效返回需确认放弃。初始YAML中未列出的空入口保留，显式清空则移除；编解码器的可选preserveNulls区分主动null和缺省字段，默认脚本输入行为保持。全页面视觉与运行态能力仍按roadmap继续核验。

## CUSTOM 规则整页

- `CustomRules` 保留原始规则文本与稳定行标识，解析正则/逻辑规则的逗号 payload、src/no-resolve 和裸行/列表 YAML；`CustomRuleCatalog` 从当前源正文提取策略组、本地代理与规则资源，失效引用可见并可修复。类型选择不提供 SUB-RULE；已存在的旧规则可保留、复制、排序和删除，快捷 YAML 确认会按 Flutter 行为移除 SUB-RULE。
- `CustomRulesEditor` 列表、搜索、批选、复制/删除、预设、排序和表单共用逐操作保存；子页独立保留草稿，回读成功才关闭，写入失败恢复列表顺序。搜索与排序互斥，拖动取消恢复已保存顺序；快捷 YAML 在后台解析。
- `ProfilesViewModel` 用规则页 generation 丢弃关闭后的迟到读取，以同步 saving 状态抑制重复提交；成功刷新父概览及源正文快照并留在列表。`ProfileRepository.saveCustomRules` 复用文件 mutex、正文快照、原子文件替换和 Room 事务，取最新元数据后仅更新规则及时间，过期正文或启停/运行状态变化拒绝写入。配置选择仍由核心管理器串行处理，不使用文件写锁阻止热切。
- 普通分区 YAML 保留其余原文与分区边界说明；flow 根对象或整体缩进按语义替换，避免重复 rules。空规则保存为列表 `rules: []`，不把空值变为 null；全局资源选择映射与长列表边缘拖动仍需后续补齐。

## 新增文件导入

- `ProfilesViewModel.fileImport` 持有独立 controller，Host 只负责 OpenDocument、生命周期和预览/密钥弹窗。8 MiB 有界读取后冻结源字节；界面 StateFlow 只包含文件元数据和操作状态，错钥保留源快照原位重试，取消/后台通过 generation 和 Job 作废迟到结果。系统选择器自身遮挡保留令牌；其他后台清理导入会话和密钥输入。
- `ProfileRepository.importFileProfile` 在解密/YAML 校验之后再进入文件 mutex，NonCancellable 包住原子文件替换与 Room 事务，插入前及文件提交后复核会话/运行守卫，异常回滚新文件与新增行。成功保留已有当前选择；密钥复用既有 Room 字段，不新增 schema。完整 Mihomo 语义预检仍未接入。

## 策略组问题、过滤与图标

- `ProxyGroupIssues` 为列表/表单提供结构错误与可保留的失效引用警告；复制生成新 key 并清除 originalName，避免副本引发旧引用改名。删除提示按名称解析规则目标、成员与链式代理引用，不进行全文替换。
- `NativeProxyGroupFilterValidator` 通过 Bridge 的既有 validateFilters method 使用 Go regexp2。UI 防抖、单组确认与 ViewModel 保存均校验；异步确认冻结 JSON，期间修改后必须重新确认，取消后的协程不继续提交。此操作不启动、加载或改写核心配置。
- `ProfileGroupIcon` 使用 Coil 位图/SVG/网络管线和默认/失败回退；本地图片经 ViewModel 的有界 reader 和 ProfileGroupIconData 签名识别后转为 data URI，128 KiB 上限。最近图标通过 ProfileStore/Repository 的 AppPreference 保存，串行更新并限制 24 条/512 KiB，无 schema 变更。UI 不直接读写数据库或配置文件；图标集管理与推荐已在上方后续切片接入。

## 全局规则资源与配置引用

- `RuleResourceRepository` 在 `app_preferences.global_rule_resources` 保存有界元数据，原文与 MRS 位于 Mihomo `providers/rules` 私有 UUID 文件。`NativeRuleResourceCompiler` 延迟取得管理器，避免 ProfileRepository→资源→核心管理器→ProfileRepository 构造环；初始化/编译/预览都归 `CoreRuntimeManager`，不启动服务。
- CUSTOM YAML 中 `path: kclash-resource:<id>` 是稳定引用，配置别名独立于全局名称。`toActiveProfile` 解析到当前原文或编译 MRS 的绝对路径，生成哈希命名输入投影；原配置正文不回写。更新资源为新文件，旧文件/投影暂保留供已应用配置读取，回收待补。
- CUSTOM 导入在配置文件锁外下载/编译规则资源，目录锁内复核全局快照；由配置文件替换事务调用资源登记回调，共同提交或回滚。普通/嵌套 RULE-SET 及 DNS/Sniffer 引用按语义改名，源代理凭据与 URL 不作字符串全局替换。`saveCustomProviders` 对正文和覆盖做原子保存，复核旧正文与运行守卫，合并最新元数据。
- 备份v3携带资源元数据、原文、所需MRS，兼容v1/v2；仅配置备份也携带所引用资源，稳定ID使跨设备路径重新解析。全局代理、非CUSTOM Profile资源映射、Geo/图标集/过滤等仍不在此已完成切片内。

## 全局代理资源与普通配置引用

- `ProfileProxyResources` 将非 CUSTOM Profile 映射为代理资源候选，配置中保存 `kclash-profile:<id>` 和独立别名。策略组 `ProxyGroupsSave.providerProfiles` 与组成员在同次原子保存中登记，中文/重复标签不会覆盖原定义；缺失引用保留供修复。
- `toActiveProfile` 只解析组 `use` 中实际引用的配置，将完整解密缓存复制到 Mihomo `providers/profiles/<id>-<sha256>.yaml`，保留旧版本供已应用配置使用。资源的扩展/脚本不参与 Provider 内容；原配置和被引用源均不回写。投影文件路径随节点源变化，进入管理器既有输入失效链路。
- CUSTOM 导入在文件锁外规划代理资源与下载解密，规则资源继续在目录锁内复核。最终配置文件/Room 事务复核全局 Profiles 快照，插入新资源文件/记录并登记规则资源；失败回滚正文/DB，且只回收本次新分配文件。普通订阅同源复用，显式密钥参与匹配，导入未提供密钥时按 URL 复用上游既有资源。
