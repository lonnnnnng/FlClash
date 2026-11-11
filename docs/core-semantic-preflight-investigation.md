# 导入前核心语义预检

2026-11-11，生产依赖注入与整份 FILE/URL/CUSTOM 导入、正文替换和订阅更新入口已接入独立 UID 的同版本 `config.ParseRawConfig`，不再只用 RawConfig/YAML 解码判断核心语义。本次扩展核心提交`a2136a6d34ac7c6144ffb75f0e13c0b8735dfca2`覆盖当前20种入站分支、共享传输参数校验和数字名称，资源竞态4例已验。906 JVM、Debug/Release-R8/motion/Test、双Lint与扫码DEX3/3通过；同源Redmi12/12及正式签名R8真实SAF导入通过，见[最新证据](acceptance/core-semantic-extended-redmi-20261111.json)。前组8/8独立保留，不能与本批累计；整个功能收尾目标尚未完成。

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
- 本组已扩展当前全部20种入站分支；不是所有协议的每个参数和实际流量均已覆盖。端口占用、TUN权限、当前接口存在性、远端登录/握手属于运行环境可用性，离线预检不作保证。
- API24～29当前仅静态/Lint合同，未有低API真机运行证据。正式签名R8本轮已完成原配置真实文件导入，不推广全部取消/竞态和运行态场景。

原始批次和失败保存在外层 `acceptance-artifacts/semantic-preflight-20261111/`。本轮不创建上游 Issue/PR，不更换同步来源，不发布新版。

## 前组集中验收（核心5fd3564f）

Redmi API36独立motion/Test回拉SHA匹配，1748份源码快照在构建/验收后保持。原配置完整解析报告90个外部资源延期；节点/组/规则/DNS/TLS五类候选经正式Repository拒绝且无正文/资源/数据库写入；CA文件有效/坏PEM、堵塞管道取消、三次任务取消后后继校验、CUSTOM错误零暂存与原FILE稳定引用导入均通过。运行中前后2次真实HTTPS200、同socket归因，epoch/运行起点/选择/源正文和Geo文件保持。

正式公开0.1.2本批四表/两配置/UID/schema和正文SHA/大小/mtime前后保持，本批测速表初始与结束均0行；此前238→0的具体删除写者没有本轮历史审计证据，已记录45秒缓存TTL候选，未追认为完整归因。结束无代理服务及校验进程，原1.yaml保持；独立测试包保留用于剩余边界。

前序API30 Lint错误、Activity导入编译失败、Go测试缺必填字段和新JVM同定义节点去重夹具失败均保留；该组源码Go父模块及ca/config/geodata/fakeip/rule-provider race、嵌套route/http/updater race通过。当时未验的入站扩展、资源竞态与正式R8文件导入由下组补齐，原证据不回改。

## 扩展与正式R8集中验收（核心a2136a6d）

- `listener/parse.go`当前20种类型均映射到只读检查。新增Snell、ShadowQuic、TrustTunnel、Hysteria2 Realm和Sudoku，HTTP/Socks/Mixed补Reality/证书/客户端认证组合；Tunnel复用真实地址解析，Mieru已由Store校验，Redir/TProxy权限属于运行环境。未来新增类型默认报未处理，避免静默放行。
- 入站检查放到`parseListeners`解码后执行，避免弱类型数字名称在原map字符串查询时漏检；数字坏AnyTLS和合法HTTP正反例通过。Sudoku在正式Bind前共用`PrepareProtocol`与ProtocolConfig.Validate；MKCP/Mekya危险参数提前拒绝，单列[KCORE-008](transport-config-validation-defect.md)。
- 绑定后的QUIC构造已追读固定quic-go依赖的`validateConfig`：版本拒绝条件由ShadowQuic既有ParseQUICVersions覆盖，其余相关窗口值默认化/钳制；未创建伪UDP监听器，未改依赖缓存。不把静态检查等同各协议实网通过。
- `ConfigSemanticResourceRaceInstrumentedTest`使用授权正文内存投影、独占Room/目录与实际隔离验证器，确定控制原候选检查后规则目录变化、最终检查后代理文件变化、按URL复用坏缓存、未引用缺失资源四条路径。前两条拒绝提交且既有内容保持，坏缓存在落盘前拒绝，未使用资源不误阻合法导入。无生产测试开关或正式库注入。
- 最终同源Redmi12/12（50.927秒）、1751份源码哈希保持；包含前组8项与新增4项，运行前后两次HTTPS200、epoch/选择/源正文/Geo保持。906 JVM、Debug/Release-R8/双Lint/motion-Test及DEX3/3通过。
- 正式包同签名R8候选SHA`992c48db3b4f7b43ab5123a1123b14631873253ff00889749ce8506a3b58a09f`已安装回拉一致，仍显示0.1.2/code1002，**不是公开Release字节，未新发版**。安装边界四表/两配置/UID/schema/正文SHA-size-mtime保持。实际DocumentsUI选择原始1.yaml后预览核心校验通过、90资源延期，确认导入2→3配置；新增正文SHA一致，页面仅删除新副本回到2份，原四表/正文/UID/schema保持。
- 更早候选首次打开首页前后的偏好差异，键摘要明确匹配`ip_quality_cache_v1`，源码成功查询确有缓存写入入口；Release没有该次写事件审计，不能断言唯一写者或追认旧未知偏好变化。最终R8导入前后四表完全一致。Download本批副本按SHA删除，工作区原1.yaml SHA/大小/mtime保持，结束无代理服务/校验进程；独立包暂留。
- Go父模块与mkcp/mekya/config/ca/route/http/updater race通过；Sudoku、VMess MKCP/Mekya、SS HTTP obfs定向`-parallel 1` race通过。额外全入站测试分别出现VLESS Wss2/mTLS+ECH、SS HTTP obfs本机回环正文超时；失败日志保留，定向复测不能代替全套通过，也不认定为本次修改导致。低API真机、外部资源可达性和所有协议实流仍不在本组结论内。

下一组按[功能性清单](functional-closure.md)继续首次联网、长期状态和Provider旧超时调查；应用内更新按用户最新指示暂缓、最后处理。
