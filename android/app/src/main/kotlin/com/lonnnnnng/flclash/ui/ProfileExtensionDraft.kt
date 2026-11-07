package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.ProfileExtensionValues
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import java.util.UUID

/** 作者 long：只保存扩展字段和规则，不把含订阅地址、密钥的整份Profile放进窗口会话。 */
internal data class ProfileExtensionCommit(
    val operationId: String = UUID.randomUUID().toString(),
    val global: Boolean = false,
    val before: ProfileExtensionValues? = null,
    val after: ProfileExtensionValues? = null,
    val beforeGlobal: List<ExtensionRule> = emptyList(),
    val afterGlobal: List<ExtensionRule> = emptyList(),
    val committed: Boolean = false,
    val abandoned: Boolean = false,
) {
    override fun toString() = "ProfileExtensionCommit(global=$global, committed=$committed, [REDACTED])"
    fun validate() {
        require(UUID.fromString(operationId).toString() == operationId)
        if (global) {
            ExtensionRules.validate(beforeGlobal)
            ExtensionRules.validate(afterGlobal)
        } else {
            listOf(checkNotNull(before), checkNotNull(after)).forEach {
                require(it.mode in setOf("standard", "script"))
                ExtensionRules.validate(it.settings.rules)
            }
        }
    }
}

internal data class ProfileExtensionDraft(
    val profileId: Long,
    val session: String = UUID.randomUUID().toString(),
    val uiDraft: EditorSaveableState? = null,
    val pending: ProfileExtensionCommit? = null,
    val error: String? = null,
    val saveResultVersion: Long = 0,
    val version: Int = 1,
) {
    override fun toString() = "ProfileExtensionDraft(profileId=$profileId, [REDACTED])"
    fun validate() {
        require(version == 1 && session.isNotBlank() && saveResultVersion >= 0)
        uiDraft?.restoredValues()
        pending?.validate()
    }
}
