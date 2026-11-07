package com.lonnnnnng.flclash.ui

/** 作者 long：发布包不提供校验挂起、布置或计数接口，校验完成后直接进入正式保存。 */
object ProviderValidationProbe {
    suspend fun checkpoint() = Unit
}
