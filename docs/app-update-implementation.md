# 应用更新：读取官方发布页版本并弹框

2026-11-23，按用户确认简化为仅检查版本、结果弹框和浏览器跳转。

## 当前行为

- 工具→应用更新直接在工具页上方弹框，显示“正在检查更新”；可取消，关闭后取消未完成请求。
- 每次点击只向 `https://github.com/lonnnnnng/FlClash/releases/latest` 发一次HEAD请求；不调用REST API、不读取更新说明、网页正文或安装包。
- 不跟随跳转，只读取Location并校验HTTPS、GitHub域名、本仓库和正式 `kclash-vMAJOR.MINOR.PATCH` 标签，拒绝Flutter标签、预发布或无效响应。版本与安装包VERSION_CODE比较。
- 相同或更低版本提示“已是最新正式版本”，点确定返回工具页。
- 有新版显示版本号，点“前往 GitHub 下载”以ACTION_VIEW打开对应发布页；用户可取消。
- 查询失败提示重试，并保留脱敏的路线/阶段诊断码。找不到浏览器时在弹框中提示。
- 核心运行时沿用核心实际HTTP入站和代理认证，停止时直连；不使用加速地址，不降低TLS校验。

## VPN下403的原因与修复

POCO公开0.1.17的现场为 `CORE_HTTP_PROXY/HTTP/403`。同一手机核心入站探针确认CONNECT200、TLS验证成功，GitHub REST API返回403，limit=60、used=60、remaining=0，正文明确API rate limit exceeded。匿名API额度按来源公网IP统计，共享代理出口可能由其他使用者耗尽。

同一代理访问GitHub最新发布网页正常302。参考上游 `afe7136002271fd64f098b154e7fff40be721eb8:lib/common/request.dart` 中发布页跳转识别方式，最终实现直接只查网页，删除API列表解析及更新说明。该查询不消耗匿名REST API配额；不代表网站没有独立防滥用限制或所有网络问题都不会发生。

## 验证

632项App JVM全通过；正式签名Release/R8、Motion/Test构建及Release Lint通过（0错误、11警告、1提示），扫码DEX检查通过。POCO两项定向测试通过：生产核心入口下只有一次真实HEAD、REST API请求为0；等待/最新版/新版/失败弹框的动作正确。原位安装本地正式签名候选后，在VPN运行中真实工具入口显示“已是最新正式版本”，安装APK回拉SHA一致。

本地候选使用0.1.17/code1017，不等于GitHub原公开0.1.17字节；此修复现已随0.1.18正式发布，0.1.18公开APK尚未安装。配置未修改，VPN恢复运行，独立测试包与ADB转发已清理。详见[验收](acceptance/app-update-web-poco-20261123.json)与[溯源](runtime-log-and-update-check-defects.md)。

## 已取消范围

详细更新页面、REST API版本列表、更新说明解析、应用内APK下载/安装、后台自动升级均不在当前实现内。历史下载/安装测试不作为现行功能。

## 历史验收（0.1.3）

903项JVM零失败/错误/跳过（删除旧下载5项测试），Debug、签名Release/R8、motion/Test及双Lint零错误；扫码最终DEX 3/3。Redmi独立页真实检查0.1.2成功；签名0.1.3覆盖0.1.2后，UID/schema6/四业务表和两正文SHA-size-mtime保持，正式页面显示当前0.1.3与无新版状态。首轮Lint生成源码解析异常保留，先构建再单独Lint复验通过。公开0.1.3已发布为Latest：标签/分支CI通过（含Go race、全部JVM、R8、双Lint/DEX及签名核验）。公开APK SHA`2e1ccec265fca81cbd660564c5c8ba5a5110839eabfd7a117a447acb22373963`与GitHub digest/SHA256SUMS及Redmi回拉一致。候选到公开包安装边界四表/正文/UID/schema保持；0.1.2独立验收包实际发现0.1.3，点击按钮打开Via浏览器并读到对应GitHub发布标题。公开0.1.3页面重新检查显示最新；完整网页正文渲染与APK下载由浏览器负责。本例三个测试/探针包及五个SHA匹配文件已清，正式服务停止，原1.yaml保持。见[发布证据](acceptance/release-0.1.3-redmi-20261111.json)。
