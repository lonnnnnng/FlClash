# KREL-002：正式 R8 包打开扫码页崩溃

2026-11-10 在 Redmi Note 8 Pro（API36）复现。正式 Release `033d6c108c732ed4718af05c714a43ab3d80776f5fd69e35e97b364f96a83fb8` 两次打开扫码入口均在主线程崩溃；相机权限已允许。

## 原因

- `logcat` 的 `ComponentDiscovery` 初始化警告显示 ML Kit 的 `CommonComponentRegistrar`、`BarcodeRegistrar`、`VisionCommonRegistrar` 都因 `NoSuchMethodException: <init> []` 无法实例化。稍后 `BarcodeScanning.getClient()` 因注册组件不完整抛出 NPE。
- 该 Release APK 的 R8 `usage.txt` 确认移除了三个公共无参构造器。mapping 只记录类名仍存在，不能代表反射入口保留。
- APK 清单含 ML Kit `MlKitComponentDiscoveryService`、三个 registrar 字符串元数据和 `MlKitInitProvider`，排除 manifest merge 丢声明。
- 历史扫码真机验收在 Debug 包完成；Debug 不启用 shrink，因此当时成功不覆盖签名 Release/R8。现有证据不能确定用户上次成功使用的具体 APK，也不能确定某一个提交首次引入该组合，故不把根因归到 KCORE-006 或首次发布提交。

脱敏日志、崩溃 APK 和对应 R8 mapping/usage/configuration 存于 `../../acceptance-artifacts/qr-camera-crash-20261110/`。原始取景截图只保留本机。

## 修复

`android/app/proguard-rules.pro` 仅为 ML Kit 包内的 `ComponentRegistrar` 实现保留 public 无参构造器，类原名继续保留以匹配清单元数据；没有关闭 minify/shrinkResources，也没有改变 ML Kit 或 CameraX 版本。

新增 `tool/check_mlkit_release.py` 读取最终 APK manifest 与 DEX，要求三个必需 ML Kit registrar 元数据存在、provider 存在、registrar 是具体类且公共无参构造器留在 APK。CI 在 Release 工件检查中运行它。最终采用实际压缩包门禁和正式真机交互验收；未增加无法覆盖R8裁剪的Debug客户端创建测试。

规则依据为 [Android官方反射保留示例](https://developer.android.com/topic/performance/app-optimization/keep-rule-examples)（2026-11-10抓取）：按类名加载再调用`getDeclaredConstructor().newInstance()`必须保留实现类及无参构造器。Flutter固定参考`be847366`使用`mobile_scanner`控制生命周期，其应用proguard无对应修复规则；未运行或修改Flutter包，也不声称其Release存在同样故障。

## Redmi 验收

签名 Release 修正版 APK SHA-256：`125647a6d1c4d8f65ecc27727ed012ad20905afa149b7c7307fc0c776c01f339`。通过同证书覆盖安装并回读匹配，正式配置/数据未清。

该包仍为0.1.0/code1000本地修复验收版本，用户随后授权发布0.1.1；新版与远端最终APK以独立发布证据为准。签名Release构建及双Lint通过：Debug69警告14提示、Release66警告13提示，均0错误；本轮865项JVM结果全部UP-TO-DATE复用，Go及新的Debug仪器测试未执行。正式卡片仍为原单份配置且选中；没有本轮完整Room/文件mtime快照，不把“没有执行写入”扩大为逐字段数据库保护证明。

- ML Kit Release APK 检查确认三个注册器均保留公共无参构造器，旧 APK 对同一检查 3/3 失败。
- 修正版 Release 首次启动后从“配置→添加→扫描二维码导入”打开 CameraX 取景；相机服务确认应用持有后置相机，进程没有崩溃。
- 手电筒可开关；退后台后相机客户端释放，回到前台再次取得，关闭扫码页后释放。验收期间同一正式包进程保持运行。
- Android 系统相册选择由本轮生成的离线 QR 图片，识别为 `ReleaseQR` / `https://example.com/qr-release.yaml` 并预填确认表单；未提交导入、未请求测试链接、未新增或改动 Profile。
- 用户在真机独立复试也确认现象消失。最终崩溃缓冲区仍含修复前 PID 的历史堆栈；验证以安装后同一 PID 的取景、手电筒和系统相机持有证据为准，不误称清空日志后的零崩溃证明。

取景正常但本轮未对准实体二维码；相册识别已实测。首次前台页面已被关闭导致定位失败、0.8秒解绑观察过早、DocumentsUI预览按钮误点和返回一级菜单后的过早断言属于验收驱动问题，修正后继续真实导航；不记作产品崩溃。两张本轮上传图片按SHA核对后删除，其中无效二维码图片未实际测试，不记无效码通过。

具体构建、安装、设备回读与原始/脱敏日志索引见 [验收JSON](acceptance/qr-camera-r8-redmi-20261110.json)。检查器对旧 APK 的预期失败和修复 APK 的通过输出一并留存。

## 后续复查

更换 ML Kit、Firebase Components、AGP/R8 或 consumer rules 时，重新检查扫描服务的 manifest 元数据及压缩后 DEX 的注册器构造器。只有实际 signed Release APK 检查器通过并在 Redmi 打开扫码取景，才可视为覆盖正式包链路；Debug 仪器测试不能替代该门禁。
