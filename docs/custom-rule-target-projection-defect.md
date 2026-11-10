# KUI-039：CUSTOM 运行投影遗漏规则目标与 Dialer 节点

2026-11-11，在完整导入语义预检的投影审查中发现。属于 Kotlin 配置投影缺陷，不是 Mihomo 或手机问题；没有把它认定为之前的 TLS EOF、配置时间变化或 Provider 超时原因。

## 来源与触发

父仓库基线 `125309b85d7d1a68b5bfd71c9267369a0dc4225f` 的 `LocalProxies.referenced` 仅扫描策略组 `proxies`，该投影实现可追溯至 `eaa6e4b5`。`planLocalProxyImport` 将内联节点移入全局目录并移除正文 `proxies`；`toActiveProfile` 再用前述集合投影节点。

当节点 A 仅被 `MATCH,A` 引用，或节点 B 仅作为 A 的 Dialer 目标时，原始输入可以合法，但保存后的运行投影缺少节点。该调用链已由源码确认；没有声称已在旧正式 APK 复现。

## 修正与回归

- `LocalProxies.referenced` 增加规则/子规则目标、组默认/空回退成员及正文 Dialer 引用；`project` 同时接受持久化 Dialer 映射，实际运行与预检使用相同投影规则。
- CUSTOM 导入先检查原始候选，代理/规则资源映射完成后再检查最终运行投影；规则目录版本和引用文件在提交时复核，预检不创建运行投影缓存。
- 新增 `LocalProxiesTest.importKeepsRuleOnlyTargetsAndDialerDependenciesInRuntimeProjection`；首轮 A/B 同定义触发正常去重复用，修正为不同定义后重跑，保留 `android-build6.log` 失败。
- 新增 Redmi CUSTOM 原候选错误零写入、原 FILE 稳定引用投影导入回归。906 JVM及Redmi同源8/8通过，见[验收记录](acceptance/core-semantic-preflight-redmi-20261111.json)。规则目标/Dialer新投影由JVM验证，Redmi稳定引用成功导入不扩大成规则目标真实流量命中。

## 后续同步

Flutter 仅作功能参考，不合并其提交。以后重构全局节点或引入新的目标字段时，同时复查引用发现、节点重命名、删除保护、备份引用与运行投影，不能仅看组列表；保留规则目标与 Dialer 的回归。
