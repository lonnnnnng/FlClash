# Kotlin 应用采用全新数据边界

状态：accepted

Kotlin 主线被视为全新的 Android 应用，首次安装使用独立数据目录和全新数据模型，不读取、转换或兼容 Flutter/Drift 的既有数据库。后续版本只为 Kotlin 自身的数据模型编写升级迁移，不以旧应用数据兼容为目标。

这个决定允许数据模型围绕 Android 产品重新设计，避免把 Flutter 历史 schema 和迁移包袱带入新应用；代价是从 Flutter 版本切换到 Kotlin 版本时，用户需要重新配置 Profiles、订阅、规则和其他本地数据。
