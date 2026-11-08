# KClash 使用独立发布签名身份

状态：accepted

KClash 在发布阶段创建新的 release keystore，不复用 FlClash 的签名材料；keystore、alias 和密码只通过本地未提交配置或 CI 加密 Secret 提供，debug 构建使用默认 debug 签名。

2026-11-09 用户授权创建首个发布身份。证书 SHA-256 为 `3d6cf8aab47ed321b7ca4b3c85960c63168d57ca9f35bd03e739c815444c0f49`，RSA 2048，自签名证书有效期 10000 天。后续版本必须复用该身份，不能为发新版重新生成密钥。

本机密钥库位于 `~/Library/Application Support/KClash/keys/kclash-release.jks`，权限 0600；另有同机备份 `~/.android/kclash-release-backup-20261109/kclash-release.jks`，两者已校验一致。备份不等于异机备份。密码分别存于 macOS Keychain 的 `com.lonnnnnng.flclash.signing.store-password` 与 `com.lonnnnnng.flclash.signing.key-password` 项，账号 `long`；alias 为 `kclash-release`。源码仅保留路径/指纹，不包含秘密。

GitHub Actions secrets 为 `KCLASH_RELEASE_KEYSTORE_BASE64`、`KCLASH_RELEASE_STORE_PASSWORD`、`KCLASH_RELEASE_KEY_ALIAS`、`KCLASH_RELEASE_KEY_PASSWORD`。只有 `kclash-v*` 标签构建注入签名；PR/分支验证不使用发布密码。签名后固定核验证书指纹、包名、版本和 ABI，再生成下载校验文件并发布；runner 结束删除其临时密钥库。

Gradle 本地签名支持同名前缀环境变量（密钥库路径使用 `KCLASH_RELEASE_KEYSTORE`），仍兼容既有未提交 `local.properties` 的签名字段；没有签名配置时只生成 unsigned Release。Debug 与正式签名不同，不能直接覆盖。2026-11-09 用户明确授权在专用 Redmi Note 8 Pro 卸载 KClash 测试包并清除残留应用数据后验收正式包；今后只用该真机验收，不使用模拟器，Flutter 原版和其他应用不在清理范围。
