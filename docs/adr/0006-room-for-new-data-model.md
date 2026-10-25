# 使用 Room 管理 Kotlin 应用数据

状态：accepted

Kotlin 应用使用 Room 建立全新的本地数据模型，不复刻 Flutter/Drift 的历史 schema。实体和关系围绕 Android 手机产品重新设计，页面通过 Repository 访问数据；未来只为 Kotlin 自身的版本变化维护 Room Migration。

这样可以使用 Android 原生的类型安全查询和迁移工具，同时避免旧数据库结构限制新产品建模；数据层仍需保持对订阅、规则、代理组、脚本和运行记录的清晰边界。
