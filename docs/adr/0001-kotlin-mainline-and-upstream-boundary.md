# Kotlin 主线与 Flutter 上游追踪边界

状态：accepted

从现有 Flutter `main` 创建长期维护的 `kotlin` 主线。原仓库 `chen08209/FlClash` 配置为 `upstream`，个人 fork `lonnnnnng/FlClash` 配置为 `origin`；Flutter `main` 只作为参考基线和上游追踪来源，Kotlin 主线通过显式同步和功能核对吸收上游变化，不直接跟踪或自动合并上游分支。

这样可以保留上游产品演进的可追溯性，同时避免 Flutter 提交被 Git 自动带入 Android 原生主线；代价是每项上游功能都需要经过 Android 适配和一致性验证。
