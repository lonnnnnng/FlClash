package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class GeoUpdateFailureMessageTest {
    @Test fun incompleteBodyRemainsAnExplicitFailure() {
        val message = geoUpdateFailureMessage("can't download GeoSite database file: unexpected EOF")
        assertTrue(message.contains("文件未完整接收")); assertTrue(message.contains("旧资源未改动"))
    }
    @Test fun gatewayStatusIsDistinguishedFromTransferFailure() {
        assertTrue(geoUpdateFailureMessage("can't download GeoIP database file: 504 Gateway Time-out").contains("HTTP 504"))
        assertTrue(geoUpdateFailureMessage("can't download ASN database file: 403 Forbidden").contains("HTTP 403"))
    }
    @Test fun signedUrlsAndServerMessagesAreNeverEchoed() {
        val error = "can't download MMDB database file: Get \"https://example.test/data?token=private-token\": context deadline exceeded"
        val message = geoUpdateFailureMessage(error)
        assertTrue(message.contains("超时")); assertFalse(message.contains("example.test")); assertFalse(message.contains("private-token"))
    }
    @Test fun writeFailureCannotClaimOldFileWasPreserved() {
        val message = geoUpdateFailureMessage("can't save MMDB database file: disk failure")
        assertFalse(message.contains("旧资源未改动")); assertTrue(message.contains("更新失败"))
    }
}
