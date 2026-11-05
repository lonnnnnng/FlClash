package com.lonnnnnng.flclash.data

import org.junit.Assert.*
import org.junit.Test

class IpQualityCacheTest {
    @Test fun onlyFreshValidRecordsSurviveAndCorruptNeighboursDoNotHideValidEntry() {
        val record = IpQualityRecord(IpQuality("8.8.8.8", IpQualitySource.IDENT_ME, IpType.HOSTING), 1_000)
        val encoded = IpQualityCacheCodec.encode(listOf(record), 1_000)
        assertEquals(record, IpQualityCacheCodec.decode(encoded, 1_000)["8.8.8.8"])
        assertTrue(IpQualityCacheCodec.decode(encoded, 999).isEmpty())
        assertTrue(IpQualityCacheCodec.decode(encoded, 1_000 + IP_QUALITY_MAX_AGE).isEmpty())
        assertTrue(IpQualityCacheCodec.decode(encoded.replace("IDENT_ME", "unknown"), 1_000).isEmpty())
        assertTrue(IpQualityCacheCodec.decode(encoded.replace("8.8.8.8", "127.0.0.1"), 1_000).isEmpty())
        val mixed = encoded.replace("\"entries\":[", "\"entries\":[{},")
        assertEquals(record, IpQualityCacheCodec.decode(mixed, 1_000)["8.8.8.8"])
    }

    @Test fun persistedSetIsBoundedAndPrunedByOriginalCheckTime() {
        val records = (1..70).map { IpQualityRecord(IpQuality("8.8.8.$it", IpQualitySource.IP_QUERY, IpType.RESIDENTIAL), it.toLong()) }
        val decoded = IpQualityCacheCodec.decode(IpQualityCacheCodec.encode(records, 100), 100)
        assertEquals(64, decoded.size); assertFalse("8.8.8.1" in decoded); assertTrue("8.8.8.70" in decoded)
        assertTrue(IpQualityCacheCodec.decode(" ".repeat(128 * 1024 + 1), 100).isEmpty())
        assertTrue(IpQualityCacheCodec.decode("{truncated", 100).isEmpty())
    }
}
