package com.lonnnnnng.flclash.ui

import java.util.UUID

/** 作者 long：预览正文、MRS原字节及选区保存树同属独立密文；提交身份在目录登记前落盘。 */
data class RuleResourceSession(
    val draft: RuleResourceDraft,
    val fileBytes: ByteArray? = null,
    val uiDraft: EditorSaveableState? = null,
    val intent: RuleResourceWrite? = null,
    val error: String? = null,
    val version: Int = 1,
) {
    fun validate() {
        require(version == 1 && UUID.fromString(draft.session).toString() == draft.session)
        require(fileBytes == null || fileBytes.size <= 8 * 1024 * 1024)
        uiDraft?.restoredValues()
        intent?.let { require(UUID.fromString(it.token).toString() == it.token) }
    }
    override fun toString() = "RuleResourceSession([REDACTED])"
}
data class RuleResourceWrite(
    val token: String = UUID.randomUUID().toString(), val label: String, val url: String, val content: String,
    val targetId: Long?, val committed: Boolean = false,
) { override fun toString() = "RuleResourceWrite(committed=$committed, [REDACTED])" }
