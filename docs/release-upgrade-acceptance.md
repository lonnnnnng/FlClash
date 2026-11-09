# 0.1.0 → 0.1.1 正式包保留数据升级

2026-11-10，Redmi Note 8 Pro，API36。目标是验证原始 GitHub 正式 APK 之间的覆盖安装，保持已有配置与选择。

## 准备基线

设备原已安装0.1.1。Android16 `PackageManagerServiceUtils.isDowngradePermitted`检查当前安装包或系统是否允许调试，设备`ro.debuggable=0`，因此普通`install -r -d`不允许直接降级正式包。参考源码访问于2026-11-10：
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/pm/PackageManagerServiceUtils.java

本轮先使用独立同签名Instrumentation读取原0.1.1数据库/文件摘要，将databases、files、shared_prefs、no_backup归档到应用自有外部目录并回收到本机，逐项校验归档正文哈希。备份权限0600，不提交Git。快照不启动KClashApplication初始化，但框架ContentProvider初始化仍可能改变WorkManager及SQLite运行文件，不能称快照进程绝对零写入。

随后通过仓库外Gradle init脚本将当前Release临时设为debuggable，使用既有发布证书签名，仅作为允许降级的准备包；不启动其主页面。安装后立即`install -r -d`原始官方0.1.0，再回拉校验原包哈希。整个过程没有卸载KClash、清除数据、修改ROM安全配置或安装Flutter。

0.1.0和0.1.1的数据库schema均为6，配置实体结构一致。本次不证明数据库schema迁移或跨大版本兼容。临时准备包不计为正式版本验收；进入真实0.1.0后单独建立升级基线。

## 结果与边界

- 原始0.1.0 SHA-256：`ed063b9ba59d0825308b2d442da38e5e8ecdcb456cc7c55cd385f11b05ad1082`；原始0.1.1：`02924c48f20fa6885fec061deebcb82bb18ef9c90930c24fab3b25aa8fef4437`。设备分别回拉一致，正式版本均非debuggable，证书沿用`3d6cf8aa…444c0f49`。
- 0.1.0运行后取快照，直接覆盖0.1.1：UID、1份FILE配置全部21个实际数据库字段（含当前组与节点选择）、2条偏好、0份脚本及238条测速缓存全部保持；配置文件正文SHA、大小和mtime保持。
- 0.1.1正常启动及force-stop冷启动后，配置、偏好和正文/mtime仍保持，原配置卡可见；238条测速缓存的键和数量保持，但value及updatedAtMillis全部发生变化。该变化发生在安装后运行窗口，不能当作安装丢数据，也不能未归因就断言全部应用数据静止不变。现有源自动组选组健康检查与CoreEvent.Delay写入路径是候选，尚缺该窗口事件对应证据。
- 私有目录整体字节不变的扩大断言失败，变化包含SQLite数据库/WAL/SHM、WorkManager数据库及ProfileInstaller标记。原始快照和失败记录保留；业务数据使用逐字段摘要核对，配置正文另按SHA/size/mtime检查。
- 本次只覆盖设备既有单FILE配置和既有选择，不推广URL/CUSTOM混合库、未提交草稿、VPN运行中升级、OEM自然回收或所有类型文件。未调用备份恢复冒充升级。

本机原始工具、归档、APK、快照、失败及对比位于`../../acceptance-artifacts/post-release-20261110/`。摘要见对应验收JSON；原始备份包含用户数据，仅保留本机。
