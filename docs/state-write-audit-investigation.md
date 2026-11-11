# 状态写入来源审计（用户接受收口）

2026-11-11用户决定：近期审计与数据核对通过，旧配置/偏好异常目前无法复现，不再继续追根，按接受历史未归因收口。保留本页实现、原失败和证据，有新可复现问题再打开；下列旧“尚需完成/下一步”属于历史计划，不再作为状态/Provider任务阻塞。网络TLS单列继续，见[当前功能清单](functional-closure.md)。

2026-11-11，初始产品基线`48fa7661`。本组仍属于六项功能收尾中的长期配置/偏好稳定性调查；没有以短窗口通过解释旧未知变化，也没有关闭旧TLS EOF和Provider超时。

最新逐键补验：同源快照增加HMAC字段、会话指纹和读取时间窗；605.731秒真实窗口4流66419条零丢弃，15快照中2190项测速变化全部匹配delay.core_event提交，配置/Room另10项变化及3次删除均有来源。SharedPreferences窗口无值变化，类型/删除/apply与commit边界由真实测试覆盖。大快照logcat丢7片已修为SHA文件传输（KDIAG-001）；报告器7项、source4 Redmi审计5/5和Provider两死亡场景通过。网络本窗口7/8且抓到node-dns失败，不能报全绿。详见[逐键联接与限制](state-audit-key-join.md)及[证据](acceptance/state-key-network-redmi-20261111.json)。

## 实现边界

- `StateWriteAudit`默认无接收器；只有显式开启的独立`.motion`会话才记录。Debug Application在Hilt/Room初始化前读私有会话，Release使用空初始化和原Framework SQLite Factory。无schema变化。
- Profile的38个IO写入口和选择/自动fallback携带协程来源；DAO受理记录逐字段HMAC摘要与原始调用点。嵌套调用保留外层业务来源，直接DAO旁路仍可追到受理调用点。测速明确区分core_event、manual_single、manual_batch和cache_expiry；不能把所有核心事件都推断为自动健康检查。
- SQLite Factory覆盖真实Room2.8.5 suspend/compat驱动的写语句与事务边界。DAO返回只表示操作完成；最外层`endTransaction`正常返回且无失败子事务才标提交。yield、未观察到父事务及end异常标unknown。建库/迁移内部原生事务不在本次覆盖中，本轮既有schema6。
- 全部20处应用自有SharedPreferences获取点改走原Editor委托；apply只记内存受理，commit成功才记commit回执。监听器不作为来源证据。原Profile文件替换、补偿、旧fallback、创建/删除、Backup复制/补偿和两类投影均独立记录文件SHA等价的会话HMAC、大小和mtime；文件完成不代表数据库已提交。
- 私有随机HMAC密钥位于no_backup会话配置，原正文、URL、密钥和SQL绑定原值不输出。调用栈字典和每条独立gzip成员减少重复数据；进程死亡时此前完整成员可解码，残缺尾部须单列。每进程128MiB、会话512MiB，通过跨进程文件锁检查；单记录64KiB。溢出/写入失败留下marker和计数，普通完整导出拒绝；显式不完整导出保留失败证据，关闭不受marker阻止。

官方SQLite接口正文与本机接口核验位于`../acceptance-artifacts/state-write-audit-20261111/sqlite-api.md`，Room行为由固定依赖字节码/生成DAO和实际Redmi测试交叉验证。审计有IO/CPU开销，本组不用于动效或网络延迟性能结论。

## 已执行证据与失败保留

- 编译首轮：`deleteProfile`末尾布尔值经泛型封装后被推断为Any，与Unit接口不符；明确Unit后通过。首次motion Lint Kotlin分析器ClassCastException保留；固定源码后构建成功，不关闭/绕过Lint。
- 初轮SQLite事务/文件补偿/偏好回执1/1；相关保存/同步/测速回归11/11。随后发现Room整行更新SQL为`UPDATE OR ABORT`，旧匹配遗漏。首会话只记录受理、漏事务，立即恢复自动更新设置并关闭；`audit-incomplete-*`保留，不能算完整来源审计。
- 修正SQL匹配并增加真实ProfileDao.update断言后，最终motion6同套包12/12（含溢出可关闭）通过；APK`5371adaf…8c6fbdf`、Test`a8582c2a…d9aa345`安装回拉一致。原1.yaml临时设备副本核验后删除，原文件保持。
- `window2`预定45分钟，实际约383.699秒主动中止。首请求和5分钟HTTPS成功、手动授权源同步成功；主页面产生238个测速键。5分钟时审计已达旧32MiB单进程上限：sequence33364、dropped5996；停止时dropped16523，恢复设置后16543。该窗口必须标不完整，不能用于证明全写入闭环。驱动收到仅针对自身PID的SIGINT，finally执行stop/restore-auto/snapshot，均通过且无服务。
- 容量失败后改为调用栈字典、每条独立gzip、去掉执行回执的重复绑定值，并提高仍有硬上限的诊断配额。motion8最终SQLite/文件/同步/测速/容量/压缩成员回归13/13通过，安装回拉匹配；`window2`提前中止和丢弃记录继续作为不完整失败证据保留。
- `build4`保留1条JVM `UncaughtExceptionsBeforeTest`：前一VM的IO协程在resetMain后返回Main。测试类统一跟踪VM Job，取消并等待完成后才重置Main；定向整类通过，`build5`892JVM与构建/双Lint通过。只改JVM测试清理，不改正在运行的设备包；原XML `build4-profiles-failure.xml`保留。

## 新45分钟窗口（window3）

- motion8 APK SHA`aced1e721c908bc056ede05d2878f5e16b6172618ec7955a01a0d1c334c1744d`。实际2703.223秒、23动作、17快照；包含主页面前后台、授权源手动同步、显式到期扫描及后续实际Worker更新。15分钟受控SIGKILL时原PID4032先消失，verify前外部观察系统新PID4939。10/10正常HTTPS200，均同socket核心归因、正流量及非DIRECT，直接确认RUNNING；不据此解释旧TLS EOF。
- 五份审计流118100258字节，流式导出核对大小/SHA后解码310058条；每PID序号连续、零丢弃，完整模式导出，会话已关闭。44283条最外层db_committed记录；关联到的来源包括手动同步、两次到期同步、自动更新设置、选择、恢复意图持久化、测速过期清理和核心测速事件。core_event不细分自动健康检查或其他核心触发原因。
- 三次远程同步均可关联lastUpdateAt和文件mtime变化、正文SHA保持；本窗口没有currentGroupName变化。停止、还原自动更新设置及最终快照通过，结束无核心服务。原1.yaml保持。
- 首次完整导出把全体Base64装入内存导致OOM，原`audit-export.log`保留，审计文件未丢。只升级测试导出器，按45000字节分片流式导出；运行包仍为motion8，不将导出器变更当作窗口运行代码变更。
- 离线结果的`unexplainedChanges=[]`仅限已覆盖的配置/偏好值/文件匹配；两组app_preferences复合操作各有2实体/2受理/2执行，无法唯一配对，已排除而非认定写入失败。快照非原子，值来源匹配不证明唯一的区间内因果。偏好updatedAt、删除键、SharedPreferences逐键来源仍需补审；Delay仅统计含时间戳的整行变化，未做逐键HMAC联接。不能宣称全持久化变化均已解释。

补充逐项核验（保留原analysis.json）：`composite-preference-review.json`确认两组复合操作均来自自动同步；同一待应用回执键先与正文同事务INSERT，应用完成后另事务DELETE，两次均执行并提交。`preference-boundary-review.json`确认三次SharedPreferences变更都落在`subscription-auto-update-audit/runs`提交窗口，停止时删除的是`runtime_should_resume`，有对应提交；没有Delay键删除。新增`preference-times-review-final.json`将三处偏好updatedAt变化连同整实体HMAC匹配到已提交的选择/恢复意图写入。SharedPreferences旧快照SHA与审计HMAC没有直接值联接，Delay逐键联接限制仍保留；这些结果不倒推历史异常。

本机原始附件`../acceptance-artifacts/state-write-audit-20261111/window3/`中的`terminal.json`、`network-summary.json`、`audit-files.json`与`analysis.json`保留上述结果和限制。阶段汇总见[功能状态证据](acceptance/functional-status-20261111.json)。

## 尚需完成

1. 复合偏好和三处updatedAt已补核；Delay与SharedPreferences同格式联接现已实现并分范围验证，批量过期删除和并发唯一因果不扩大结论。旧11-05/11-07未知来源不能靠新窗口倒推。若有新异常，先留证再修，不回写旧快照掩盖变化。
2. 统一构建/双Lint/Release诊断隔离已复核，902JVM和最终7项真机通过；892为较早批次。本组代码/证据随当前提交保存；整体目标未完成。
3. 核心语义预检已进入生产入口与同源验收；应用内更新真实0.1.1→公开0.1.2及本批数据保留已验，剩余下载/安装中断等边界见[更新记录](app-update-implementation.md)。


## 提交前审查边界

Release采用原Framework驱动和空sink，包装保持原IO调度、返回及取消异常；没有开启审计文件或schema迁移。Debug审查发现“事务唯一写语句失败”只留语句失败、缺最终事务结果，新增failedWrites计数在原endTransaction返回后记录，不把语句失败直接当事务回滚。NOT NULL真实拒绝用例补入原事务用例，首轮混合SQL参数数组编译失败保留，显式Array<Any?>后重验。

SharedPreferences不同Editor之间的before/after快照不具备原子性，不能证明并发同键的唯一写者；commit回执与业务来源可用。中途启用审计未观察到begin的事务标unknown，OOM等Error不在常规异常容错保证内。后续采集逐键值需记录同一HMAC协议和快照读取起止，不能放宽当前分析边界。

追加最终`final2/audit-updater-final7.log` 7/7通过，含真实NOT NULL唯一失败事务的db_write_failed及db_rolled_back（writes=0/failedWrites=1）、原回滚/提交/文件补偿、溢出关闭/压缩、隔离native与更新三项。Release更新运行源码保持，最终motion双包回拉一致；这不改变旧window3的运行身份。

最终追加`audit-final-lint.log` Debug构建/双Lint通过；正式候选清单/DEX无隔离探针、长窗宿主及审计会话文件名。正式Redmi保留实际应用内升级得到的公开0.1.2、授权两配置，核心停止；motion/Test及只读升级探针暂留供未完边界验收，未清其私有证据。新上传的/sdcard/Download/1.yaml按SHA删除，工作区原文件SHA/size/mtime保持。
