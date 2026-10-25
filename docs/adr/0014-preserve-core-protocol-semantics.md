# 保留 Mihomo 核心协议语义并提供 Kotlin 类型封装

状态：accepted

Kotlin Bridge 继续使用 Go Mihomo 现有的 method/event 语义和 JSON envelope，负责编解码、线程切换、错误映射和资源释放；Bridge 之上提供类型安全的 Kotlin API，ViewModel、Repository 和 Service 不直接传递原始方法字符串。上游新增核心能力先扩展 Bridge/API，再接入页面。

保留协议语义可以减少核心升级和功能对齐的重复工作，同时让 Kotlin 应用层摆脱 gomobile 生成类型和字符串协议；代价是 Bridge 必须持续维护协议版本、兼容错误和事件顺序。
