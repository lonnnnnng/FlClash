# 应用更新：检查版本并跳转GitHub

2026-11-11用户明确简化范围：读取当前版本、检查本仓库是否发布新版；有新版时展示版本和说明，点击“前往 GitHub 下载”后由浏览器打开对应发布页。

## 当前行为

- 入口为工具→应用更新；打开页面检查一次，也可手动重新检查。
- 当前版本使用安装包的VERSION_NAME/VERSION_CODE。只比较`lonnnnnng/FlClash`中的稳定`kclash-vMAJOR.MINOR.PATCH`，并确认发布中有本仓库Kotlin arm64 APK；忽略Flutter历史、草稿与预发布。
- 没有更高版本显示“已是最新正式版本”；查询失败保留重试按钮。
- 有新版本显示版本和发布说明，点击按钮以ACTION_VIEW打开该GitHub发布页。浏览器负责后续下载。
- 查询GitHub时沿用当前核心的实际HTTP入站；核心停止时使用直连。查询没有放宽TLS校验，也没有改用加速地址。

## 已移除的旧范围

应用内APK下载、断点续传、下载WorkManager、SHA/签名/降级校验、未知来源授权、PackageInstaller会话、安装回调与通知、更新状态磁盘协议及相应测试均删除；安装权限和更新dataSync前台服务声明删除。它们不再属于待办，不继续做进程死亡/安装恢复边界。

当前实现只含页面、内存检查状态、GitHub查询和发布目录解析。没有后台自动更新、安装包缓存或兼容旧下载任务的迁移逻辑。此前完整升级流程的验收仅保留为[历史证据](acceptance/app-update-redmi-20261111.json)，不能据此扩大当前功能范围。

## 验证

发布筛选及新/相等/旧版本比较由4项单测覆盖。构建、Lint、Redmi真实查询结果与安装身份记录见[简化版验收](acceptance/app-update-simple-redmi-20261111.json)。

903项JVM零失败/错误/跳过（删除旧下载5项测试），Debug、签名Release/R8、motion/Test及双Lint零错误；扫码最终DEX 3/3。Redmi独立页真实检查0.1.2成功；签名0.1.3覆盖0.1.2后，UID/schema6/四业务表和两正文SHA-size-mtime保持，正式页面显示当前0.1.3与无新版状态。首轮Lint生成源码解析异常保留，先构建再单独Lint复验通过。公开0.1.3已发布为Latest：标签/分支CI通过（含Go race、全部JVM、R8、双Lint/DEX及签名核验）。公开APK SHA`2e1ccec265fca81cbd660564c5c8ba5a5110839eabfd7a117a447acb22373963`与GitHub digest/SHA256SUMS及Redmi回拉一致。候选到公开包安装边界四表/正文/UID/schema保持；0.1.2独立验收包实际发现0.1.3，点击按钮打开Via浏览器并读到对应GitHub发布标题。公开0.1.3页面重新检查显示最新；完整网页正文渲染与APK下载由浏览器负责。本例三个测试/探针包及五个SHA匹配文件已清，正式服务停止，原1.yaml保持。见[发布证据](acceptance/release-0.1.3-redmi-20261111.json)。
