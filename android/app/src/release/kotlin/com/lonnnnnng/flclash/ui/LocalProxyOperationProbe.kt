package com.lonnnnnng.flclash.ui

/** 作者 long：发布包没有布置、释放或计数接口，正式操作始终直接执行。 */
object LocalProxyOperationProbe {
    suspend fun checkpoint(window: LocalProxyOperationWindow) = Unit
    fun saveFailed(error: Exception) = Unit
}
