package com.lonnnnnng.flclash.data

import java.util.UUID

/** 作者 long：回执只包含操作身份和资源ID，与目录同事务；不存URI、正文或远程凭据。 */
internal fun resourceImportReceiptKey(kind: String, token: String): String {
    require(kind in setOf("rule", "icons") && UUID.fromString(token).toString() == token)
    return "resource_import/$kind/$token"
}
