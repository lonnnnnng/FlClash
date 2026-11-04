# Architecture

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

菜单采用浮层底色、连续圆角、阴影、弹簧缩放/淡入淡出和禁用语义；尚未实现Flutter完整液滴、截图变形、滑动携带和玻璃模式。整体页面parity仍PARTIAL，集中构建/安装/失败证据见`docs/acceptance/main-menu-redmi-20261104.json`。

## 主页面连续圆角与绘制反馈（2026-11-04）

`AppSuperellipseShape`沿用Flutter engine八分弧参数，统一Dashboard、配置卡片/表单、代理节点和List组合边角；保留四个逻辑角及RTL映射。独立Flutter `Path.addRSuperellipse`输出7种轮廓的像素中心包含区间，Android按行比较LTR/RTL端点，栅格边界最多允许1px差异，不能由普通圆角截图自证。Flutter BSD声明随APK assets保留。

`AppSpring`在连续更新目标时保留速度，`appElasticPress`只变换绘制，静态命中/语义/菜单锚点不随按压膨胀；短按先升至0.975再回落，关闭动画时收敛到目标。`appFocusRing`跟随子树焦点，选中本身不画焦点。Dashboard投影使用Flutter多层参数，配置与节点沿各自卡片反馈，不给CommonCard额外套按压。

`MainNavigationDock`单一指针受理拖动和释放，滑块/抬升/拉伸/形变分别用物理状态更新；取消合成的已消费抬手退回当前入口，不提交导航。语义入口仍由selectable提供，外扩8dp命中区和绘制增长不移动入口语义矩形。代理页刷新保留原列表，同时统一禁用Tab/List的选择、菜单和测速，并显示忙态；组图标按完整字素取首项，显示副本修复非法代理字符，保护系统无障碍序列化。构建、真机与视觉边界见`docs/acceptance/main-visual-redmi-20261104.json`，完整页面parity继续PARTIAL。

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
