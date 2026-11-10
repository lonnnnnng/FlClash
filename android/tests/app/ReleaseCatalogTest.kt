package com.lonnnnnng.flclash.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseCatalogTest {
    private fun row(version: String = "0.1.3", tag: String = "kclash-v$version", draft: Boolean = false, prerelease: Boolean = false,
        repository: String = UPDATE_REPOSITORY, name: String = "KClash-$version-android-arm64-v8a.apk", bytes: Long = 100): String =
        """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"assets":[{"name":"$name","size":$bytes,"browser_download_url":"https://github.com/$repository/releases/download/$tag/$name"},{"name":"SHA256SUMS","browser_download_url":"https://github.com/$repository/releases/download/$tag/SHA256SUMS"}]}"""
    @Test fun choosesOnlyStableKotlinArm64AssetsFromOwnRepository() {
        val json = listOf(row(),row("0.1.2"),row(tag="v0.9.3"),row("0.1.4",draft=true),row("0.1.5",prerelease=true),
            row(repository="chen08209/FlClash"),row(name="FlClash-0.1.3.apk"),row(bytes=MAX_APK_BYTES+1)).joinToString(",","[","]")
        assertEquals(listOf("0.1.3","0.1.2"),ReleaseCatalog.parse(json).map { it.version })
    }
    @Test fun versionOrderAndOverflowCannotOfferDowngradeOrAlias() {
        assertEquals(1002L, ReleaseCatalog.versionCode("0.1.2"))
        assertTrue(ReleaseCatalog.versionCode("1.0.0")>ReleaseCatalog.versionCode("0.999.999"))
        for (value in listOf("0.1000.0","0.1.1000","01.2.3","2147.999.999","1.2.3-beta")) assertTrue(value,runCatching { ReleaseCatalog.versionCode(value) }.isFailure)
    }
    @Test fun checksumRequiresExactUniqueFilenameAndConsistentAssetDigest() {
        val release=ReleaseCatalog.parse("[${row()}]").single();val sha="a".repeat(64)
        assertEquals(sha,ReleaseCatalog.checksum("$sha  ${release.apkName}\n",release.copy(assetDigest=sha)))
        for(text in listOf("$sha  other.apk","$sha  ../${release.apkName}","$sha  ${release.apkName}\n$sha  ${release.apkName}"))
            assertTrue(runCatching { ReleaseCatalog.checksum(text,release) }.isFailure)
        assertTrue(runCatching { ReleaseCatalog.checksum("$sha  ${release.apkName}",release.copy(assetDigest="b".repeat(64))) }.isFailure)
    }
    @Test fun redirectsRequireHttpsKnownReleaseHostsAndNoCredentials() {
        assertTrue(ReleaseCatalog.allowedDownloadUrl("https://release-assets.githubusercontent.com/asset?token=example"))
        for(url in listOf("http://github.com/a","https://github.com.evil.test/a","https://name:secret@github.com/a","https://github.com:444/a","file:///tmp/a.apk"))
            assertFalse(url,ReleaseCatalog.allowedDownloadUrl(url))
    }
}
