package com.lonnnnnng.flclash.ui

/** 作者 long：发布包不包含布置门闩的能力，保存和退出不会因测试检查点挂起。 */
object EditorCommitProbe {
    suspend fun checkpoint(window: EditorCommitWindow) = Unit
}
