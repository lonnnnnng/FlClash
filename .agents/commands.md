# Commands

## Repository checks

执行命令前确认当前目录、分支和工作树：

```zsh
pwd
git status --short --branch
git remote -v
```

根目录 Kotlin Android 工程使用仓库根目录 Wrapper；常用检查为：

```zsh
./gradlew :app:assembleDebug
./gradlew test
./gradlew lint
./gradlew connectedDebugAndroidTest
```

Release 预发布检查（没有独立签名身份时输出未签名 APK）：

```zsh
./gradlew :app:assembleRelease :app:lintRelease --no-daemon -Ptarget-platform=android-arm64
```

`.github/workflows/kotlin-android.yml` 使用同一根目录 Wrapper，负责 Kotlin 主线的 arm64 单元测试、Debug/Release 构建、R8 与 Lint。分支/PR 检查未签名产物；`kclash-vMAJOR.MINOR.PATCH` 标签注入独立发布签名，校验证书/版本/包名/ABI 后发布正式 APK 和 SHA256SUMS。签名身份及本机保管方式见 `docs/adr/0013-kclash-release-signing.md`；Flutter 发布 workflow 不作为 Kotlin 主线的构建入口。

Release生成后执行`python3 tool/check_mlkit_release.py <APK>`核对清单注册器与最终DEX公共无参构造器。Debug不经过R8，不能用Debug扫码通过代替此门禁；升级ML Kit/AGP后还须在Redmi正式包打开取景、释放/恢复及相册识别。缺陷和历史红绿见`docs/release-mlkit-registrar-defect.md`。

自然交互性能诊断可以显式开启独立包名的非Debug应用：

```zsh
./gradlew :app:assembleMotionBenchmark :app:assembleMotionBenchmarkAndroidTest --no-daemon -Ptarget-platform=android-arm64 -PkclashMotionBenchmark=true
```

仅该次Gradle调用创建`motionBenchmark`变体及对应测试任务，包名为`com.lonnnnnng.flclash.motion`；应用非debuggable、未压缩混淆，库按debug回退并复用固定UI验收宿主。它不等于正式签名/R8 Release，默认构建不携带这些组件。`VisualMotionNaturalInstrumentedTest`按方法筛选：完整流程为`lightPagesPanelsEmptyAndContinuousMotion`/`darkPagesPanelsEmptyAndContinuousMotion`，三轮菜单为`lightMenuFirstOpenAndRepeatedTopCard`/`darkMenuFirstOpenAndRepeatedTopCard`；每批只选其中一对，不运行整个类，也不混用同名主题gfx输出。分段参数`kclashNaturalMotion=true`、`kclashMotionProfile=true`、`kclashCapturePng=false`、`kclashMotionInlineFrames=true`及唯一`kclashMotionRun`；不同时录屏。非Debug包禁止依赖run-as，帧表由Instrumentation内联回传并核验SHA。方法采样仅用于独立诊断，不与性能对照合计。完整保护、环境重置后的空库门禁及复测记录见`docs/menu-rendering-performance-defect.md`，前组历史见`docs/motion-performance-defect.md`。

菜单独立ART方法采样只选`VisualMotionNaturalInstrumentedTest#darkMenuOpenAndCloseMethodSamples`，参数`kclashMotionMethodTrace=true`、`kclashMotionInlineTrace=true`及唯一安全名称`kclashMotionRun`，不要启用PNG。每轮分别回传open/close，共6份trace；`kclash_method_chunk`分片必须核验name/index/count/bytes/sha256再解码。设备文件在完整发送后核对原字节并删除。Perfetto59.1只导入dual-clock墙钟，线程CPU需另解析原字段并交叉验证，不能把slice.dur称为CPU；见`docs/menu-content-compositing-defect.md`与`docs/acceptance/menu-methods-redmi-20261109.json`。

gfxinfo离线分窗口报告使用`python3 tool/android_menu_frame_report.py --expected-window 弹出式窗口 --output report.json input.gfx`。保留进程与各Window的原始计数，不相加；RTAnimation/Skipped位可重叠，区间只使用各窗口重置后的Flags=0行。关闭后Popup缺席必须报告不可观测，不可记零耗时。解析器验证：`python3 -m unittest discover -s tool -p test_android_menu_frame_report.py -v`。边界见`docs/menu-source-preparation-investigation.md`。

当前重置后的Redmi连Debug包也拒绝`run-as`的SELinux上下文，Debug自然视觉同样启用内联帧表。中断采集只执行`removeExactOwnInterruptedFrameArtifacts`，显式`kclashMotionCleanup=true`、原run及JSON `kclashMotionCleanupManifest`（name/bytes/sha256）；先核对清单全部文件再精确删除，空清单只断言采集缓存为空。2026-11-08重置窗口曾为0份；后续已恢复授权本地配置，当前数量必须按当轮正式环境核对，不能沿用旧两份或零份门禁，也不能静默导入补齐测试前提。

独立线程计数旧入口为`VisualMotionNaturalInstrumentedTest#darkMenuOpenAndCloseThreadCpuSamples`。当前输入并发采样选择`#lightMenuDispatchThreadTimeline`或`#darkMenuDispatchThreadTimeline`，显式`kclashMotionThreadCpu=true`、`kclashCloseWindowCapture=true`、`kclashMotionInlineFrames=true`及安全唯一`kclashMotionRun`，禁用PNG/录屏/方法采样。独立线程先取基线再触点注入，计划50ms、约0.8秒，不代表实际采样间隔。用`python3 tool/android_menu_thread_report.py instrument.log --output report.json`解析；按TID/startTicks分开同名RenderThread，读取失败/线程死亡单列readFailures，后创建线程不在采集范围。报告器测试用`python3 -m unittest discover -s tool -p test_android_menu_thread_report.py -v`。仅用于诊断，不等于正式R8包性能或原生调用栈；当前证据见`docs/nonempty-reference-menu-timeline.md`，历史见`docs/menu-cpu-geometry-investigation.md`。

## 关闭留窗、条目动效与长窗诊断

关闭留窗仅使用 `VisualMotionNaturalInstrumentedTest#darkMenuCloseFramesBeforeWindowRemoval`，参数 `kclashCloseWindowCapture=true`、`kclashMotionInlineFrames=true`、`kclashCapturePng=false` 和安全唯一 `kclashMotionRun`。原回源动画结束后才保留Popup并取帧，正式Release实现为空；不与正式性能或录屏窗口混算。原生探针 `#darkMenuCloseOwnUidNativeProbe` 需显式 `kclashNativeProbe=true`，当前Redmi已连续失败，详见`docs/menu-close-window-investigation.md`，不要无条件重复。

条目自然时钟选择 `#moreSelectionEntriesUseNaturalContinuousExit`，显式 `kclashNaturalMotion=true`；独立Dialog按可见文案定位，测试标签不跨Window继承。更多设置回归按 `ProfileMoreEditorInstrumentedTest#method` 精确选取。当前AGP在 `-PkclashMotionBenchmark=true` 下只创建motion对应App单测任务，默认 `test` / Debug单测与motion构建分开调用，不混写不存在的 `:app:testDebugUnitTest`。

`StabilityWindowPreparationTest` 和 `StabilityWindowAcceptanceActivity` 仅允许 `.motion` 独立空库、原1.yaml及外层AGENTS授权目标；输入加密、token/PID门禁受控死亡，长窗checkpoint不启动服务。逐阶段等待预算与哈希化Profile/文件/偏好快照用于诊断，首失败不能被重试覆盖。当前45分钟网络8/10，不能作为全绿脚本样板；驱动和原始批次见`docs/acceptance/selection-motion-stability-redmi-20261109.json`及其本机附件。

Provider恢复诊断使用`python3 tool/android_provider_runtime_acceptance.py --serial wsvwypiz7xwslvl7 --app com.lonnnnnng.flclash.motion --case <before|after|diagnostic-close|diagnostic-timeout> --output <全新目录>`。需已准备原FILE和唯一授权URL、停止服务；不得为门禁静默覆盖配置。driver核对包SHA后通过应用内只读preflight检查无遗留备份、初始化完成及恢复意图为关闭，再隔离准备进程；真实死亡仍必须在外部观察系统新PID/前台服务后才能verify。诊断失败两例仅限motion，不能当成旧TLS EOF复现。报告分片按SHA/字节数核验，失败原目录不覆盖；当前结果见`docs/functional-network-investigation.md`。

逐键状态报告使用`python3 tool/android_state_key_report.py <本轮窗口目录> --output <报告.json>`；输入需新格式的auditDelaySnapshot/auditSharedPreferenceSnapshot、相同sessionDigest及完整audit/*.jsonl.gz，不能分析旧SHA快照或混合审计会话。测试`python3 -m unittest discover -s tool -p test_android_state_key_report.py -v`。大于24KiB的Stability回执为verified-file-v1，收集器须限制独立motion包stability-reports路径、精确token-command-SHA名称与大小，读回校验后只删除该文件；旧logcat分片收集器不可直接复用。当前完整采集驱动和失败批次在`../acceptance-artifacts/functional-key-audit-20261111/`；协议/证据边界见`docs/state-audit-key-join.md`。Provider验收须分别观察runtime-ready/service-ready，不能在RUNNING第一笔Flow后即时假设其他状态同时发布；独立首请求仍只等RUNNING，不添加固定延迟。

复用Stability输入token时，collector必须只读取当前command及其子阶段，传输副本名称带正文SHA，不能让旧已删除回执或同名报告参与本轮SHA核对。修正后的本机示例为`../acceptance-artifacts/dns-startup-20261111/recovery2/probe.py`，原失败留在recovery-window。DNS诊断区分`kind=lookup`（源端口context关联、实际network/地址数/父context类别）与旧日志分类记录；DNS事件仍只是候选，不能凭同域名/时间窗称socket归属。KCORE-009及最新两种失败阶段见`docs/dns-lookup-empty-success-defect.md`。

TLS失败取证现保留`connectionTrace`的零/单向业务流量与`node-selected`最终端点；前8个`node-dial`只是候选，最多32条并公开omitted/dropped，不能取第一成功候选冒充winner。Tracker不是TCP线上字节，DNS仍无socket归属。Provider失败另看documentErrorCategory/hash；未到ready/kill不能计恢复通过。最终Provider两准备请求失败及独立FILE三成功分别保留，见`docs/tls-one-way-flow-investigation.md`。当前协议阶段见`docs/protocol-stage-investigation.md`：response begin无end只表示采集结束前未完成，需核对丢弃数；不能推断0个线包。Provider等待同时观察提交门闩与终态错误，避免把提前拒绝误记30秒超时。2026-11-11用户已接受状态/Provider历史异常并收口，不再主动追旧因；保留现有保护和记录，集中复现仍活跃的TLS问题。

## 菜单无线程采样对照（KUI-035）

精确选择 `VisualMotionNaturalInstrumentedTest#lightMenuWindowFramesWithoutThreadSampling` 或 `#darkMenuWindowFramesWithoutThreadSampling`，显式设置 `kclashMenuWindowFrames=true`、`kclashCloseWindowCapture=true`、`kclashMotionInlineFrames=true`、`kclashCapturePng=false` 和唯一安全 `kclashMotionRun`；不得启用线程/方法采样或录屏。每方法三轮开关，预期6份内联帧表、3次关闭留窗、0份线程计数。与线程诊断分开报告，A-B-A按窗口原始计数比较，关闭留窗不是正式R8场景。固定范围候选无稳定收益已撤回，见 `docs/member-picker-alignment-menu-investigation.md`。

## Go core

```zsh
go version
cd core
go test -race ./...
go test -race github.com/metacubex/mihomo/hub/route github.com/metacubex/mihomo/component/http github.com/metacubex/mihomo/component/updater
```

`core` 是 Go module 根目录，Mihomo 是嵌套 module；父模块 `./...` 不运行控制器、HTTP生命周期及Geo恢复回归，必须另执行 route/http/updater 包。核心修复来源、固定提交和同步复查条件见 `docs/upstream-source-defects.md`。

Geo恢复定向验收使用`GeoDownloadRecoveryInstrumentedTest`，只允许空`.motion`库和原`1.yaml`哈希门禁。`#geoRetriesKeepBusyProtectOldFileAndAllowManualRecovery`显式传`kclashGeoRecovery=true`；`#originalGeoUrlsDownloadFullBodiesAcrossAutomaticAndFixedLeaves`显式传`kclashGeoLive=true`，原配置须包含代码托管selector及已有80/8880叶节点，不满足则报告输入条件。先确认正式VPN/ProxyService已停止，避免独立核心DNS端口争用；不能用正式包承接故障改源。实网仅独立暂存配置关闭ETag并临时选组，原文件不改；`kclash_geo_live_chunk`逐轮验证index/count/bytes/SHA后解码，检查四轮每项HTTP200、bytes/length/文件大小一致及实际代理链，不能只凭JUnit成功认定全部固定叶通过。结束逐项恢复设置/选择/暂存文件并删除自有Profile；失败批次保留。证据见`docs/acceptance/geo-download-recovery-redmi-20261110.json`。

gomobile AAR 构建需要服从 `core/go.mod`、Android SDK、NDK 和 Gradle 配置；不要手工复制或修改生成的 AAR/classes。

## 授权本地配置验收

`readAuthorizedProxyProfile()`读取设备`/sdcard/Download/1.yaml`前要求显式`kclashAuthorizedProfileSha256`和`kclashAuthorizedProfileBytes`，由当轮工作区原`../1.yaml`生成；设备内容必须匹配。不能恢复旧硬编码哈希或自行构造配置满足用例。上传前检查目标是否存在，已有不同文件必须保留；本轮新上传的副本收尾按哈希精确清理。该输入不等于正式配置已导入。

## Android devices

```zsh
command -v adb
adb devices -l
adb -s <serial> shell getprop ro.build.version.sdk
adb -s <serial> shell getprop ro.product.model
```

安装、启动、截图、日志和清数据都必须显式使用目标 serial。失败时保留命令、serial、退出码和关键错误。

## Git

提交前运行 `git diff --check`、相关 Gradle/Go 测试和 `git status --short --branch`。提交说明使用中文，不把生成物、密钥或临时日志加入版本库。
