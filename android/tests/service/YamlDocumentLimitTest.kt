package com.lonnnnnng.flclash.service

import org.junit.Assert.*
import org.junit.Test

class YamlDocumentLimitTest {
    @Test fun validCommentPaddedDocumentAcceptsEightMiBAndRejectsOneExtraByte() {
        // 作者 long：字节上限与解析器字符预算必须一致，合法的大量注释不能在声明的8MiB以内被默认预算拒绝。
        val prefix = "rules: []\n# "
        val text = prefix + "x".repeat(8 * 1024 * 1024 - prefix.length)
        assertTrue(YamlConfigCodec.toJson(text).contains("rules"))
        assertTrue(runCatching { YamlConfigCodec.toJson(text + "x") }.isFailure)
    }
}
