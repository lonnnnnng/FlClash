# KCORE-005：Geo 下载 Connection: close 触发截断与连接回收

> 2026-11-10接续：Geo瞬断有界恢复和当前Redmi新网络多出口更新已由[KCORE-006](geo-download-recovery-defect.md)完成本组验收。下文保留2026-11-09原始连接修复及换网失败现场；旧网络的具体远端因果仍未确认。

2026-11-09；现场正式APK a72a955a，父仓库5b0682f0，Mihomo7038231e。来源仍为chen08209/Clash.Meta:FlClash，本组没有同步官方Mihomo提交，也不改资源地址或节点选择。

## 现场和因果边界

用户在VPN运行时，浏览器可访问Google/YouTube/GitHub，但工具→资源→右上角更新失败。原日志三轮12条错误：首轮两项504，其余unexpected EOF。实测重跑四项仍EOF；只更新GEOSITE也EOF，不能解释为仅四并发拥塞。

最近请求显示内部进程mihomo到github.com及release-assets.githubusercontent.com均命中RuleSet(github)，经过代码托管→节点选择→自动选择→非DIRECT叶节点，收到数MB后结束。排除“应用自身没走代理”。同手机通过当前127.0.0.1:7890 SOCKS下载原GitHub GEOSITE两次完整HTTP200、4355962字节；无需加速地址。

随后固定HTTP/1.1，对Connection与Accept-Encoding做交叉对照：keep-alive四次全部完整，close六次仅一次完整，五次断流；identity/gzip都能触发，最终响应没有Content-Encoding，Content-Length统一4355962。同版Go HTTP/TLS库在手机独立进程测试SOCKS与增加内存Pipe两路径：两条close均unexpected EOF（4308735/3507008正文字节）、两条keep均完整且SHA85073a00…一致；失败前底层Read先返回EOF，再触发Transport.Close，context未过期。

已确认的直接触发是Android内部客户端强制Connection:close与当前代理传输链结束处理不兼容，正文未收齐便断开；没有证据证明Kotlin独有绕过代理、gzip解压、90秒超时或内存Pipe单独丢尾。远端节点/CDN哪一层的具体关闭代码尚无服务端日志，不宣称已定位其某行代码，也不把首次504全部追认为同因。

## 固定来源

`component/http/http.go:73` 的 `DisableKeepAlives: runtime.GOOS == "android"` 来自3564e96a0010a415e5dd22fc9deeb7efd381dcb5（2023-10-23，chore: share some code）。固定Flutter be847366所用核心024245dc与当前7038231e在HTTPVehicle、HTTP客户端、inner/tcp与Geo updater文件Git blob一致。

上游每次HttpRequest新建Transport。单纯删除DisableKeepAlives后若无人回收该Transport，重定向和最终响应的空闲连接会保留到超时。因此修正必须同时管理响应正文和连接池所有权。

## 修正

- 取消平台强制Connection:close，调用方明确设置close仍保留。
- 请求及重定向共用本次独立Transport。返回正文包装在requestBody中，Body.Close后回收此Transport所有空闲连接；client.Do失败也立即回收。
- 保持证书验证、代理路由、资源90秒总预算、完整正文和格式验证、原始URL；不自动用镜像、不无条件重试，不将截断内容保存为有效资源。
- Kotlin资源页收到核心错误后展示白名单原因（HTTP状态、正文截断、超时），不原样回显含敏感参数的URL。只有下载阶段失败才提示旧资源未改动，保存阶段失败不作此承诺。

## 验证与同步复查

HTTP新增五个真实本机HTTP/TCP生命周期回归：完整延迟正文+跨源重定向、提前关闭正文、截断错误保持、重定向错误清理、调用方显式close；race通过。旧源码同一测试在重定向连接回收两项失败，保留红测。父模块全量race与控制器route race通过。无网Pipe/terminal-error外围探针不是本次实网根因，原结果只作为候选审计保留。

Android构建、正式Redmi原址更新、安装签名及收尾结果见`acceptance/geo-download-connection-redmi-20261109.json`。原始诊断在`../acceptance-artifacts/resource-update-failure-20261109/`；不提交完整节点配置、私有订阅地址和签名材料。

以后仅对照chen08209/Clash.Meta:FlClash。若上游有等价连接生命周期修复，复验完整读/提前Close/取消/重定向错误和Android原址Geo实网后再撤掉本地补丁，不能只见keep-alive字段变化就认定已等价修复。

## 正式Redmi结果

build1重新执行gomobile核心构建，864项JVM结果零失败/错误/跳过（App582执行，Service213/Core38/Common31复用）；Debug、签名Release/R8及双Lint0错误（Debug69警告14提示、Release66警告13提示）。签名沿用既有身份。Release APK SHA a8c490c16c60c5594f81314f90b01bae4a740c00cc0ddfdd9622faac809249cd，安装回拉一致。

停止旧服务后同签名覆盖安装，原配置不清除，再恢复VPN/ProxyService。首次右上角全部更新四项成功（约51.75秒，包含UI轮询开销）；显示MMDB8.4MB、ASN9.1MB、GEOIP16MB、GEOSITE4.4MB及本轮更新时间。第二轮操作也四项成功（约7秒），请求记录资产流量约5.8KB/条，属于重复校验范围，不冒充四次完整正文重下。四个原GitHub URL原样回读，未换节点、未加速；修复后内部mihomo仍命中同github规则/原叶节点。

两轮新进程日志无Geo更新错误。结束服务保持运行；连接页分页观察未见遗留GitHub下载连接，这仅为该时点观察，连接回收保证另由HTTP生命周期回归支持。公开0.1.0 Release不覆盖，本地修正版交给用户继续使用。

核心提交20bc0fc467cac942bf55009017f87474114fe90c已推送lonnnnnng/mihomo:kclash-fixes。后续仍需不同节点/服务端更多覆盖；首次504及历史Always-on TLS失败不由本组追认根因。

## 换网后的复核（2026-11-09）

用户反馈：资源更新失败时怀疑是网络问题，切换网络后可以更新。该观察与旧现场相符：15:25时MMDB、ASN、GEOSITE、GEOIP均出现`github.com` TLS handshake timeout；其后MMDB/GEOIP有HTTP 504。服务与核心在场，不能据此认定VPN或应用自身没有代理。

同日稍后的Redmi复核保持原四个GitHub地址和正式配置。当前页面URL逐项回读均与原值相同。经`127.0.0.1:7890`核心SOCKS逐项读取完整正文，GEOSITE/MMDB/ASN/GEOIP均收到HTTP 200；传输字节数分别为4,355,962、8,406,197、9,054,248、16,475,153，均与最终Content-Length一致，耗时约3.9–7.6秒。未使用镜像或加速地址。同一窗口GEOSITE的App单项更新成功，大小与更新时间显示正常；最近请求指向`RuleSet(github)`并经`CF-VLESS-WARP-80`，证明该次应用流量走非DIRECT节点。

随后App单项MMDB复测失败，核心日志为`TLS handshake timeout`，最近请求显示本次实际链路经自动选择叶节点`CF-VLESS-WARP-8880`；先前成功请求经过`CF-VLESS-WARP-80`。设备快照显示当前Wi-Fi网络自17:22建立，但没有切网前的SSID/网络接口快照，无法完成受控同节点A/B。故**已验证**换网后的SOCKS正文可以完整传输，且一次Geo App更新成功；**用户报告**换网后整体恢复；**未验证**换网是唯一原因或当前网络对自动组内所有叶节点都稳定。当前最准确结论是网络路径及自动选中叶节点会影响握手结果，KCORE-005的`Connection: close`正文截断修复仍成立，但全部环境稳定性未闭环。

复核元数据与脱敏结果见`acceptance/geo-network-recurrence-redmi-20261109.json`。原始设备日志含签名重定向地址，只保留在仓库外本地验收目录，不纳入Git。
