# 应用内更新下载与安装状态缺陷

2026-11-11，基线48fa7661之后的未提交更新实现。以下是Kotlin应用层缺陷，不是Mihomo上游问题；不修改Flutter参考实现或内核固定提交。实现记录见[应用内更新](app-update-implementation.md)。

| 缺陷 | 触发与影响 | 修正及证据 |
| --- | --- | --- |
| KUPDATE-001 临时HTTP错误清除部分下载 | 已有.part后续传遇429/502/503，旧代码抛IllegalStateException，统一按内容损坏删除；用户必须重新下载 | HTTP传输失败归IOException保留字节；只有范围/大小/哈希/身份等错误清理。真实503用例保留原字节 |
| KUPDATE-002 合法短段206误判不完整 | Content-Range为N-M/T且M小于T-1，旧范围检查允许，响应结束却要求总长度T，随后删除部分文件 | 每次按声明段边界验证；完整短段继续下一次Range，提前EOF保留字节供重试，200忽略Range则截断重建 |
| KUPDATE-003 READY坏包停留安装态 | APK被截断/损坏，安装前校验在try外抛错，Activity仅显示通用错误，仍READY，无法直接重下载 | 重新校验失败转FAILED，清理自有坏包及session，保留发布信息供下载入口。Redmi真实私有文件截断用例通过，状态落盘回读一致 |

新增`UpdateApkDownload.kt`复用正式OkHttp响应，五项真实HTTP流测试通过，发布目录四项通过；最终四模块902JVM零失败/错误/跳过，Debug/Release-R8及双Lint通过。Redmi最终隔离native/坏SHA及错误包名/坏READY恢复/显式磁盘协议4/4。完整应用内升级、运行代理路由与安装往返仍单独验收，不能由上述结果替代。

本机附件`../acceptance-artifacts/app-update-20261111/`：`download-fixes-build1.log`、`final2/final-checks.log`、`final2/probe-updater-tests-final.log`；前序失败批次继续保留。无上游提交需要移植；后续同步检查本地更新流及状态协议，确保这些保留/清理分支没有被覆盖。
