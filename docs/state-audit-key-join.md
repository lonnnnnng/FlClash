# 状态审计逐键联接

2026-11-11用户决定：近期审计与数据核对通过，旧配置/偏好异常目前无法复现，不再继续追根，按接受历史未归因收口。保留本页实现、原失败和证据，有新可复现问题再打开；下列旧“尚需完成/下一步”属于历史计划，不再作为状态/Provider任务阻塞。网络TLS单列继续，见[当前功能清单](functional-closure.md)。

2026-11-11，基线`9612ebcb`。本组补足状态快照与写入审计之间的可比性，不把新窗口通过认定为旧配置、偏好或网络异常的原因已经明确。应用内更新继续按用户指示暂缓。

## 快照与报告协议

- `StateAuditSnapshots`仅位于Debug源码集。测速以四列复合键标识，字段值复用DAO的JSON标量序列化和会话HMAC；不同组、同名节点或不同测试URL不会并为一条。SharedPreferences复用Editor审计的类型/字节/HMAC格式，避免原SHA快照与HMAC事件不能比较。
- 整份快照、每个Profile的测速查询、每个偏好文件读取分别记录开始/结束单调时间；另用会话HMAC指纹拒绝跨会话比较。读取仍非全局原子，不能把匹配值解释成区间内唯一写者。
- `tool/android_state_key_report.py`仅将实际提交的SQL与对应实体联接。多实体/多语句不能唯一配对时列出歧义，回滚、影响0行及早于读取窗口的旧提交不用于解释新变化。测速更新要求完整行匹配，删除要求完整四列键匹配。
- 偏好`apply`或`commit=false`之后的值可解释内存观察，但单列为没有磁盘提交回执；删除须有同键的空值观察。批量过期删除只有条件摘要，不能逐键证明时继续列为未匹配。
- 已有旧快照保留，不回写格式或重算旧证据来追认因果。新工具输出`historicalFailuresExplained=false`和逐条匹配来源。

## 本组已核验

最终同源Redmi审计5/5，包括真实Room提交/回滚/精确删除、Typed SharedPreferences、压缩日志、容量失败可关闭及70KB大快照完整传输。报告器7项反例通过：回滚、旧提交、同节点不同URL/时间、过期删除条件、apply/commit=false、偏好删除及多实体歧义。全量906 JVM、Debug/Release-R8/motion-Test、双Lint通过；Release DEX无新增快照与长窗宿主。

## 大快照传输失败与修正（KDIAG-001）

逐键快照增至247705字节时，旧`stabilityReport`使用133个logcat分片；实际只读回126个，收集器没有通过SHA门禁，未将该次快照计为成功。此为本项目Debug验收传输缺口，未证明正式配置丢失、历史TLS失败或Provider超时同因。

大于24KiB的结果改写独立motion包的`externalFiles/stability-reports`；只输出token/命令/字节数/SHA/精确文件路径的小回执。收集器限制包目录、文件名和8MiB上限，读回并核对正文SHA，保存在本机后再次核验并删除该精确设备文件。小结果保留原分片。文件只包含脱敏报告，不存授权正文或URL。70KB用例验证实际字节，真实约249KB/270KB快照已完整读回。最初失败批次的三份审计流已完整导出并关闭会话，未删除失败证据。

## 十分钟窗口（source3）

实际605.731秒、15份新格式快照；三次显式启动、一次受控SIGKILL系统恢复、主页面前后台及唯一授权源手动同步。8笔真实请求7成功/1失败，失败原样保留，不能写网络全绿。4份审计流66419条，序号连续/零丢弃；2190项测速整行变化全部匹配已提交`delay.core_event`，没有未匹配项或实体配对歧义。SharedPreferences本窗口没有值变化，不能用零变化代替其真实写入覆盖；类型、apply/commit和删除在5项回归中验证。

第三次启动后7ms进入首请求，TLS握手5003ms后SSLHandshakeException；源端口限定的两笔核心拨号失败均为node-dns，错误端点摘要对应原授权配置第一个VLESS节点的server。相同域名A查询失败约5024ms，AAAA查询成功约3711ms；DNS事件没有socket关联ID，因此该域名/时间窗匹配不能等同独立socket因果。系统DNS已应用、观察器已启动，事件流零丢弃且DNS候选无溢出；后续同会话HTTPS成功。现已缩小到本次节点DNS解析阶段，底层DNS失败原因及旧失败同因仍未确定，未更换节点、加速地址或加入等待重试。

## Provider验收状态发布间隙（KDIAG-002）

source3的保存前死亡复验失败在`ProviderRuntimeAcceptance.kt:307`：等到核心RUNNING后，立即断言服务CONNECTED及appliedEnabled=false；尚未进行该轮网络、密文绑定或正文保存。运行失败事件显示RUNNING，清理回执确认原数据保持、备份已清。原事件没有两个子状态，无法指出究竟哪一个瞬时未满足。

源码`CoreRuntimeManager.startLocked`先发布RUNNING再更新appliedEnabled，不保证多个StateFlow原子；ProxyService也独立发布CONNECTED。本用例改为对完整服务状态作有界Flow等待，记录单独service-ready阶段及失败子状态，不加固定等待。此修改仅验收器；独立首请求宿主仍在RUNNING后立即请求，保留本次真实DNS失败。source4后续重新构建验收，source3窗口不改写成source4运行结果。

source4最终Provider保存前/后两死亡窗口均通过，6笔HTTPS200、源端口归属/正流量/非DIRECT以及防自动重放通过，基线数据恢复/备份清除。相同source4审计5/5再次通过，报告器7项通过；1752份源码哈希保持。source3的断言失败单独保留，不与最终批次合计，也不追认旧final6-before的30秒超时同因。

本窗口配置/Room另核对10项整实体/文件变化和3次偏好删除：原正文SHA保持，lastUpdateAt与mtime匹配profile.sync.manual；current_profile_id、runtime_should_resume和ip_quality_cache_v1各匹配相应提交，删除均有确切键与提交。零未匹配仅限本窗口，不等同全局原子因果。

最终正式本地签名候选未重新安装；前后四业务表/两配置正文SHA-size-mtime/UID/schema保持。原1.yaml保持，无代理服务，本轮外部快照目录为空；独立motion包保留供DNS调查。未发布新版。

结构化证据：[state-key-network-redmi-20261111.json](acceptance/state-key-network-redmi-20261111.json)。本机附件：`../acceptance-artifacts/functional-key-audit-20261111/`。下一步追查已复现的节点DNS失败和首请求关联；三个历史异常仍未全部闭环，应用内更新暂缓。
