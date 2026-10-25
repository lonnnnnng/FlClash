# KClash 使用独立发布签名身份

状态：accepted

KClash 在发布阶段创建新的 release keystore，不复用 FlClash 的签名材料；keystore、alias 和密码只通过本地未提交配置或 CI 加密 Secret 提供，debug 构建使用默认 debug 签名。当前阶段不生成、不保存任何签名秘密。

独立签名身份与新的 applicationId 一致，避免把上游发布信任链带入新应用；代价是发布前必须完成一次新的签名创建、备份和 CI 配置。
