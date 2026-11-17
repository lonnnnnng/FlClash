# 在线 age 订阅下载、解密与应用

2026-11-18，POCO F2 Pro（`31936bb6`，API36）。从 `config01` 提交历史取回此前已公开的明文源（35166字节），按用户授权轮换为新 age identity，并只更新当前授权订阅对应的两份镜像文件。密文在本地用 Mihomo age 库加密后立即解密回读，正文 SHA-256 与历史源一致；私钥只保存在仓库外的本地0600文件。

## 真机结果

最终源码构建后安装隔离的 `.motion` 和 `.motion.test` 包，设备回拉 APK SHA 与本地产物一致。真实 `MainActivity` / `ProfilesViewModel` / `ProfileRepository` /正式 HTTP 下载器 / `AgeProfileContentDecoder` / Go Bridge 完成同一 UI 链路，单场景 **1/1**：

1. 正式下载器从当前授权 URL 收到 age armor，字节数与密文 SHA 与新密文吻合。
2. 在“配置→添加→链接”中空密钥提交触发 age 输钥弹窗；此时 Room 行、正文文件、选择均未变化。
3. 输入另一份有效但不匹配的 age identity，正式 Go 解密拒绝，界面提示失败且仍无新增行/文件。
4. 在同一添加表单重试正确 identity，UI 重新下载；Go Bridge 解密、YAML/Mihomo完整预检、原子写正文和Room记录成功。Room保存新 identity，当前选择指向新Profile，保存正文SHA与历史明文完全一致。
5. 关闭VPN/TUN后通过正式 `CoreRuntimeManager.startProxy()` 加载新Profile。真实Mihomo返回35个代理组，运行态为RUNNING。
6. 通过核心本地SOCKS请求同一授权URL，TLS成功、HTTP 200，核心连接有正向双向流量并归因到非DIRECT链。

运行方式为纯代理模式，没有建立 TUN。该场景验证“在线密文→UI补钥→正式解密与导入→Mihomo加载→实际代理请求”，不代表VPN/TUN模式、Worker自然周期或其他ROM都已覆盖。

## 密钥轮换与范围

当前远端原 age identity 无法从密文或明文Git提交恢复。按照用户“从上一提交明文重新加密”的指示，取回其明确指定配置源的历史明文提交 `e44ebc8f6f01b1fc0275aac235e6ab506e4885c0`，生成新的 X25519 identity 与 age armor，并推送 `config01/main` 提交 `b74718ca960dc85e6fd0d2a25a79ed7ceb49c817`。两份镜像文件保持同一正文及SHA；当前授权地址再次下载的密文字节与本地新密文一致。

新 identity 位于本机仓库外的验收材料目录，文件权限0600；没有进入 KClash Git、instrumentation回执或设备。设备端临时输入已按SHA校验后删除。复核时发现，仓库外验收材料中仍保留历史明文副本、错误测试 identity、解密输出及源仓库临时副本；这些没有进入 KClash Git，但本轮尚未清理，不能视为已删除。原配置文件 `1.yaml` 未读取后上传或改写，sha/大小/mtime保持。GitHub 历史中已有的旧明文提交仍然存在，本轮没有重写历史。

验收完成后删除设备输入前核对 SHA，并卸载两个自有验收包；正式Profile行/文件已在测试finally中核对恢复，服务停止且设备无TUN。Flutter参考包未操作。唯一脱敏证据为[验收JSON](acceptance/online-age-subscription-poco-20261118.json)，原始测试与构建材料在仓库外 `../acceptance-artifacts/online-age-20261117/`；密钥不在这些公开记录中。
