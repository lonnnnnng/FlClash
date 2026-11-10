# 导入前完整核心语义预检（进行中）

2026-11-11，核心gitlink仍为464552e4；本轮尚未修改Go或内核fork。生产导入仍使用`YamlProfileConfigValidator`，只检查非空和YAML转JSON；Bridge `validateConfig`经`core/config.go:handleValidateConfig`只解码RawConfig。完整预检尚未接入，不得写成导入成功即核心可用。

## 已验证的隔离基础

Redmi独立motion包通过真实授权1.yaml字节的可靠PFD管道，独立UID收到同SHA/35165字节；无法读取主进程cache私有canary，native bootstrap正常。Application在Hilt构造前按`Process.isIsolated`及完整进程名分流。首次进程名后缀遗漏和后续提前关闭可靠管道读端两次失败均保留，最终4项联合用例通过；见`docs/acceptance/app-update-redmi-20261111.json`。此实验未执行配置解析、不证明完整语义。

## 必须保留的完整性与副作用边界

- 复用同版本`executor.ParseWithBytes`/`config.ParseRawConfig`检查节点、组DAG、规则及目标、DNS、TUN、Sniffer等；不能通过删字段、清空资源或替换DIRECT让测试通过。
- ParseRawConfig通过temporaryUpdateGeneral修改进程全局，另有Geo缓存、DNS overlay注册等；不能在正在运行的主核心上调用它并假定回滚完整。校验独立进程按任务结束释放，不进入Apply/Listener/Provider.Initial。
- `component/geodata/init.go`的InitGeoSite/InitGeoIP/InitASN会下载缺文件、删除坏文件再下载。只替换Geo loader不足以阻止这些入口；需明确只读资源模式，缺依赖/坏依赖返回类型化结果。
- Geo数据可由主进程以只读PFD授予，standard loader已有Bytes解析；memconservative不支持Bytes。MMDB现有LoadFromBytes坏输入调用Fatal，ASN缺同类安全入口，需要可返回error的只读加载，不能将主目录伪装为`/proc/self/fd`目录。
- fakeIP持久模式构造会打开cache.db，坏库可能删除重建。预检专用解析上下文必须用内存存储后端，同时保留原配置语义检查，不能修改候选StoreFakeIP值伪装普通解析。
- CA证书路径会读文件并创建watcher；SSH/ZeroTier planet等也有路径依赖。只允许已授予资源，缺失明确报依赖，不隐式遍历主应用目录。内联证书/私钥仍按真实解析器校验，错误不输出原密钥、路径或配置正文。
- Provider定义解析不读远程正文；Initial会下载、写缓存、Chtimes。外部资源未提供时必须明确“资源内容未校验”，不能伪造空Provider作为全部通过。已有Proxy bytes parser可复用；Rule classical策略会警告后忽略坏规则，需要strict字节入口才能声称逐条有效。
- 全局/inbound TLS部分字段仅在Listen阶段加载，DNS代理引用也有现有解析未查的边界；需要只读后检，不能为校验启动监听。隔离UID接口视角不能使parseIPV6提前清掉待校验IPv6输入。

## 下一组实现与验收

1. 在自己的内核fork上增加预检专用只读资源/内存后端上下文，默认运行路径保持；记录新增本地适配和上游同步检查点。
2. 导出仅供隔离服务使用的byte入口与结构化错误；Android导入在写Profile/文件前等待结果，取消/超时杀死本任务隔离进程，不能干扰主核心。
3. 授权原配置真实通过；节点/组/规则/DNS错误有明确定位且不落盘；Geo/证书/Provider缺依赖与坏资源分开；主核心运行状态、选择、文件内容/mtime保持。受控非法数据用于单元/独立字节边界测试，真机配置基线仍只用授权原1.yaml/唯一授权源。
4. 同版本完整内核构建、Go定向与race、JVM/双Lint、Redmi集中验收后再记完成。节点可达性、远端内容变化和登录握手属于运行可用性，不能由离线语义检查保证。
