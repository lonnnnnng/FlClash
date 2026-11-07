package com.lonnnnnng.flclash.ui

/** 作者 long：正文与原文件指纹只进入独立密文；核心绑定属于进程内对象，恢复时必须重新取得。 */
data class ProviderDocumentDraft(
    val profileId: Long,
    val name: String,
    val recoveryKey: String,
    val digest: String,
    val originalContent: String,
    val kind: String,
    val editable: Boolean,
    val content: String,
    val editing: Boolean,
    val session: String = java.util.UUID.randomUUID().toString(),
    val pending: Boolean = false,
    val committed: Boolean = false,
    val error: String? = null,
    val uiDraft: EditorSaveableState? = null,
    val version: Int = 1,
) {
    override fun toString() = "ProviderDocumentDraft([REDACTED])"

    fun validate() {
        require(version == 1 && name.isNotBlank() && recoveryKey.isNotBlank() && digest.isNotBlank() && session.isNotBlank())
        require(content.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024 && '\u0000' !in content)
        require(originalContent.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024)
        require(!committed || pending)
        uiDraft?.restoredValues()
    }
}
