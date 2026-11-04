package com.lonnnnnng.flclash.ui

/** 作者 long：草稿保留打开时的正文指纹和元数据；进程恢复不能把旧编辑伪装为对最新版本的确认。 */
internal data class ProfileEditorDraftSnapshot(
    val version: Int = 2,
    val editor: ProfileEditorUiState = ProfileEditorUiState(),
    val remote: RemoteProfileEditorUiState = RemoteProfileEditorUiState(),
    val document: ProfileDocumentEditorState = ProfileDocumentEditorState(),
    val fileBytes: ByteArray? = null,
    val remoteBytes: ByteArray? = null,
    val filePick: Triple<Long, Long, Long>? = null,
    val remotePick: Triple<Long, Long, Long>? = null,
    val nextToken: Long = 0,
    val rules: ProfileRulesEditorUiState? = null,
    val groups: ProfileProxyGroupsEditorUiState? = null,
) {
    override fun toString() = "ProfileEditorDraftSnapshot([REDACTED])"
}
