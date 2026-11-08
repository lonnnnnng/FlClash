# KREL-001：正式 R8 包首次解析 YAML 崩溃

2026-11-09，准备首个 `0.1.0` 正式发布时，在 Redmi Note 8 Pro（API36）干净安装签名包，通过系统文件选择器导入用户授权的原始 `1.yaml`，应用退回桌面。发布被阻断，未把该 APK 上传为 Release。

## 原始证据与影响

- 修复前应用源码：`39233a280aa088982a22c6afbd8f0f24136ab465`；该提交与本地构建之间仅有 CI/文档差异，产品输入一致。
- 修复前 APK SHA-256：`0377f152b9a02482ecb4e0be30037108e1bdc8c98e716dffc535d026fb9f1026`。
- `01:40:08` 崩溃：`ExceptionInInitializerError`，根因 `NullPointerException: java.lang.Package.getName()`。
- R8 mapping：`org.yaml.snakeyaml.TypeDescription -> jv5`。该类被移入默认包，Android `Class.getPackage()` 返回 null。
- 调用链：配置导入 → `YamlProfileConfigValidator` → `YamlConfigCodec` → `SafeConstructor` / `BaseConstructor` → `TypeDescription.<clinit>`。
- 影响所有首次进入该 YAML 解析链路的混淆 Release；此前 JVM/Debug 和无配置首页冒烟不覆盖此问题。不能将这些历史绿测视为正式配置流程通过。

证据在 `../acceptance-artifacts/release-0.1.0-20261109/`：`redmi-crash.log`、`redmi-crash-retraced.log`、`pre-fix-mapping.txt`、`pre-fix-release.apk`。这些本机附件不包含进 Git 的配置正文。

## 来源核对与修复

先核对 Flutter 参考：`lib/models/clash_config.dart` / `lib/common/task.dart` 使用 Dart YAML 解析，不经过 Java SnakeYAML/R8，不能从上游应用提交直接合入修复。Mihomo 子模块不受影响。

当前实际依赖为 `org.yaml:snakeyaml:2.3`。检查依赖 JAR 字节码，只有 `TypeDescription` 与 `introspector.PropertySubstitute` 引用了 `getPackage`；两者静态初始化均执行 `Class.getPackage().getName()` 后构造日志器。对应 `javap` 输出随本机证据保存。

在 `android/app/proguard-rules.pro` 精确保留这两个类的完整名字，其他成员仍允许优化，不关闭 R8、不替换安全 YAML 构造器、不捕获并吞掉初始化错误。CI 在实际 Release 的 mapping 中检查类名保持原名，或在 usage 中确认该类已完全删除；当前 `PropertySubstitute` 属于后者。最终还必须用签名混淆 APK 在 Redmi 重走真实文件选择器、解析和启动。

## 回归与后续同步

状态：已修并通过 Redmi 签名 R8 真机回归。原始文件经相同 SAF 路径校验/导入成功，VPN 授权、双前台服务、tun0、正向双向流量与浏览器 HTTPS 页面均已观察；停止后真实 force-stop 冷进程仍保留配置，修复后无新增应用崩溃。APK 回拉 SHA-256 为 `23d7f1996de1a9e4a12b2d85ef4fe68df0919fcb46183560d2beefa21c3391bb`。远端最终制品回验以 `docs/acceptance/release-0.1.0-20261109.json` 为准。

后续升级 SnakeYAML 时重新检查这两个静态初始化与混淆产物。仅当新实现不再依赖非空包名，且不带这两条规则的签名 APK 在 Redmi 通过导入与运行后，才能撤掉规则。Flutter/Mihomo 更新不会自动修复 Kotlin 的 R8 配置。
