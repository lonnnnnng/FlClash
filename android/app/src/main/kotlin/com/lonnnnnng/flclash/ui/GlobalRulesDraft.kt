package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import java.util.UUID

/** 作者 long：全局目录没有宿主配置，只有应用目标在受理时冻结；事务身份不能随着重试或切配置改变。 */
internal data class GlobalRulesCommit(
    val operationId: String = UUID.randomUUID().toString(),
    val before: List<ExtensionRule>,
    val after: List<ExtensionRule>,
    val targetId: Long?,
    val committed: Boolean = false,
    val abandoned: Boolean = false,
) {
    fun validate() {
        require(UUID.fromString(operationId).toString() == operationId)
        ExtensionRules.validate(before); ExtensionRules.validate(after)
    }
    override fun toString() = "GlobalRulesCommit([REDACTED])"
}

/** 作者 long：规则、表单和批选只进入独立密文，Activity Bundle只保留不含业务正文的导航状态。 */
internal data class GlobalRulesDraft(
    val session: String = UUID.randomUUID().toString(),
    val uiDraft: EditorSaveableState? = null,
    val pending: GlobalRulesCommit? = null,
    val error: String? = null,
    val saveResultVersion: Long = 0,
    val applyPending: Boolean = false,
    val applyTargetId: Long? = null,
    val version: Int = 1,
) {
    fun validate() {
        require(version == 1 && UUID.fromString(session).toString() == session && saveResultVersion >= 0)
        uiDraft?.restoredValues(); pending?.validate()
    }
    override fun toString() = "GlobalRulesDraft([REDACTED])"
}
