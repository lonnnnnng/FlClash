package com.lonnnnnng.flclash.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString

/** 作者 long：URI显示含不可断行标记，业务断言读取原始输入；不能通过删除显示字符掩盖草稿污染。 */
internal fun SemanticsNodeInteraction.assertRawInputEquals(expected: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.InputText, AnnotatedString(expected)))
