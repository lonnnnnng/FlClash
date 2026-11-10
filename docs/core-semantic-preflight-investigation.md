# 导入前核心语义预检

2026-11-11，本轮代码已接入生产依赖注入与整份 FILE/URL/CUSTOM 导入、正文替换和订阅更新入口。使用独立 UID 的一次性服务执行同版本 `config.ParseRawConfig`，不再只用 RawConfig/YAML 解码判断核心语义。核心修复提交 `5fd3564fc10e3dea681b5928ac4b7b690d4d8a45`。最终906 JVM、Debug/Release-R8/motion/Test、双Lint与扫码DEX3/3通过；同源Redmi精确8/8通过，见[证据](acceptance/core-semantic-preflight-redmi-20261111.json)。整项目标尚未完成。

## 解析与隔离

- `IsolatedConfigValidator.validateFull` 保留 YAML 错误定位，再通过可靠管道、Messenger 和 gomobile 进入 `ValidateConfigurationIsolated`。Native 入口检查隔离 UID、只读 FD、资源数量/大小；主核心不启用预检全局。
- 复用节点、组 DAG、目标、规则、DNS、TUN、Sniffer 真实解析；增加端口、全局/监听器 TLS 与 DNS overlay 类型检查。错误只返回静态 code/section/index，编辑器按候选正文定位，不输出密码、路径或原始错误。
- Geo 只读授权字节，缺失与损坏分开报告；fakeIP 使用内存后端而不改候选开关。证书、ECH、SSH key、ZeroTier planet、文件 Provider 只读取已授予资源，禁止下载/删除坏 Geo 或创建 watcher/cache.db。
- 管道写入使用 API21 的 poll 和单写者 512 字节块；原 API30 `fcntlInt` 在 minSDK24 下的 Lint 错误已移除。取消/30秒超时显式结束服务、关闭 FD、等待 Binder 死亡；退出未确认时阻止复用。
- AnyTLS/VLESS/VMess/Trojan/Shadowsocks/TUIC/Hysteria2 复用 lower 构造器，在绑定前用专用错误停止；补绑定之后才执行的安全模式条件。TUN 复用范围解析并检查 MAC/DNS 参数，不探测/创建网卡。

## 提交边界与 CUSTOM

FILE/URL 在写正文和 Room 前等待完整检查，失败保留原数据；订阅同步失败仍按既有契约记录失败元数据，不宣称失败时所有元数据零变化。远程替换读到新正文后先保留候选，再检查语义，失败仍能打开正文修正。

CUSTOM 分区草稿继续支持不完整编辑；整份导入先投影已有全局节点和稳定资源引用，再预检原始候选。资源准备/映射后再次检查最终投影，包含下载新建及按 URL 复用的缓存、规则暂存版本和 Dialer 覆盖。规则目录在锁内复核版本，引用文件在配置提交时复核字节与 mtime；取消/冲突不覆盖原配置。发现的规则目标节点遗漏单列 [KUI-039](custom-rule-target-projection-defect.md)。

## 证据与保留的边界

- 原始授权输入固定为工作区 `1.yaml` 的 35165 字节/SHA `353e45e66a5932f2939ca1ea68219a359c9047faac121b2c9fc17ef30444c37e`，候选错误仅内存编辑，不创建替代配置文件。
- 前序 motion3 的三项回归已通过：原配置、五类语义错误零写入、堵塞管道取消。最终motion6同源包已独立重验8/8，包含新增CA、取消恢复、运行实网及CUSTOM用例；不把旧3/3合计到最终批次。
- CA 可读无效 PEM 被误接受已在原源码 overlay 复现，修正和 Go 正反例通过；来源和同步条件见 [KCORE-007](upstream-source-defects.md#kcore-007ca文件读取成功掩盖内容无效)。
- HTTP Provider 定义会解析，远端正文与可达性明确延期；原配置 90 个 HTTP 资源不等于 90 个远端正文均验证。文件导入预览显示待检查数量。
- lower 扩展目前覆盖上述八种类型；其他入站仍只有 ParseRawConfig 检查，绑定之后的 QUIC/MKCP/其他协议构造边界需继续审查。端口占用、TUN 权限、当前接口存在性、远端登录/握手属于运行环境可用性，离线预检不作保证。
- API24～29 当前仅静态/Lint 合同，未有低 API 真机运行证据。Debug/motion 通过不能替代正式签名 R8 的运行验收。

原始批次和失败保存在外层 `acceptance-artifacts/semantic-preflight-20261111/`。本轮不创建上游 Issue/PR，不更换同步来源，不发布新版。

## 最终集中验收

Redmi API36独立motion/Test回拉SHA匹配，1748份源码快照在构建/验收后保持。原配置完整解析报告90个外部资源延期；节点/组/规则/DNS/TLS五类候选经正式Repository拒绝且无正文/资源/数据库写入；CA文件有效/坏PEM、堵塞管道取消、三次任务取消后后继校验、CUSTOM错误零暂存与原FILE稳定引用导入均通过。运行中前后2次真实HTTPS200、同socket归因，epoch/运行起点/选择/源正文和Geo文件保持。

正式公开0.1.2本批四表/两配置/UID/schema和正文SHA/大小/mtime前后保持，本批测速表初始与结束均0行；此前238→0的具体删除写者没有本轮历史审计证据，已记录45秒缓存TTL候选，未追认为完整归因。结束无代理服务及校验进程，原1.yaml保持；独立测试包保留用于剩余边界。

前序API30 Lint错误、Activity导入编译失败、Go测试缺必填字段和新JVM同定义节点去重夹具失败均保留；最终源码Go父模块及ca/config/geodata/fakeip/rule-provider race、嵌套route/http/updater race通过。其他入站lower、绑定后QUIC/MKCP、受控资源版本竞态和正式R8运行仍待补，本组不标记完整语义项目全部完成。
