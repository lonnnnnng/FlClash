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

自然交互性能诊断可以显式开启独立包名的非Debug应用：

```zsh
./gradlew :app:assembleMotionBenchmark :app:assembleMotionBenchmarkAndroidTest --no-daemon -Ptarget-platform=android-arm64 -PkclashMotionBenchmark=true
```

仅该次Gradle调用创建`motionBenchmark`变体及对应测试任务，包名为`com.lonnnnnng.flclash.motion`；应用非debuggable、未压缩混淆，库按debug回退并复用固定UI验收宿主。它不等于正式签名/R8 Release，默认构建不携带这些组件。`VisualMotionNaturalInstrumentedTest`按方法筛选：完整流程为`lightPagesPanelsEmptyAndContinuousMotion`/`darkPagesPanelsEmptyAndContinuousMotion`，三轮菜单为`lightMenuFirstOpenAndRepeatedTopCard`/`darkMenuFirstOpenAndRepeatedTopCard`；每批只选其中一对，不运行整个类，也不混用同名主题gfx输出。分段参数`kclashNaturalMotion=true`、`kclashMotionProfile=true`、`kclashCapturePng=false`、`kclashMotionInlineFrames=true`及唯一`kclashMotionRun`；不同时录屏。非Debug包禁止依赖run-as，帧表由Instrumentation内联回传并核验SHA。方法采样仅用于独立诊断，不与性能对照合计。完整保护、环境重置后的空库门禁及复测记录见`docs/menu-rendering-performance-defect.md`，前组历史见`docs/motion-performance-defect.md`。

菜单独立ART方法采样只选`VisualMotionNaturalInstrumentedTest#darkMenuOpenAndCloseMethodSamples`，参数`kclashMotionMethodTrace=true`、`kclashMotionInlineTrace=true`及唯一安全名称`kclashMotionRun`，不要启用PNG。每轮分别回传open/close，共6份trace；`kclash_method_chunk`分片必须核验name/index/count/bytes/sha256再解码。设备文件在完整发送后核对原字节并删除。Perfetto59.1只导入dual-clock墙钟，线程CPU需另解析原字段并交叉验证，不能把slice.dur称为CPU；见`docs/menu-content-compositing-defect.md`与`docs/acceptance/menu-methods-redmi-20261109.json`。

gfxinfo离线分窗口报告使用`python3 tool/android_menu_frame_report.py --expected-window 弹出式窗口 --output report.json input.gfx`。保留进程与各Window的原始计数，不相加；RTAnimation/Skipped位可重叠，区间只使用各窗口重置后的Flags=0行。关闭后Popup缺席必须报告不可观测，不可记零耗时。解析器验证：`python3 -m unittest discover -s tool -p test_android_menu_frame_report.py -v`。边界见`docs/menu-source-preparation-investigation.md`。

当前重置后的Redmi连Debug包也拒绝`run-as`的SELinux上下文，Debug自然视觉同样启用内联帧表。中断采集只执行`removeExactOwnInterruptedFrameArtifacts`，显式`kclashMotionCleanup=true`、原run及JSON `kclashMotionCleanupManifest`（name/bytes/sha256）；先核对清单全部文件再精确删除，空清单只断言采集缓存为空。正式配置当前0份，不能用旧两份配置门禁或静默导入补齐测试前提。

## Go core

```zsh
go version
cd core
go test -race ./...
go test -race github.com/metacubex/mihomo/hub/route
```

`core` 是 Go module 根目录，Mihomo 是嵌套 module；父模块 `./...` 不运行控制器缺陷回归，必须另执行 route 包。核心修复来源、固定提交和同步复查条件见 `docs/upstream-source-defects.md`。

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
