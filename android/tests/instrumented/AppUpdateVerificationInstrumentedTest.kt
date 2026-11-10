package com.lonnnnnng.flclash.update

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：用实际APK字节验证拒绝分支，不用包名字符串单测冒充Android签名解析。 */
class AppUpdateVerificationInstrumentedTest {
    @Test fun damagedReadyApkLeavesInstallStateAndOffersRedownload() = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"updater-state-${UUID.randomUUID()}").apply {mkdirs()}
        val wrapped=object:android.content.ContextWrapper(context){override fun getNoBackupFilesDir()=directory}
        val task=UUID.randomUUID().toString()
        val release=AppRelease("0.1.3",1003,"KClash-0.1.3-android-arm64-v8a.apk","https://github.com/$UPDATE_REPOSITORY/releases/download/kclash-v0.1.3/KClash-0.1.3-android-arm64-v8a.apk",100,"https://github.com/$UPDATE_REPOSITORY/releases/download/kclash-v0.1.3/SHA256SUMS","","https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v0.1.3",null)
        val storage=File(directory,"app-update").apply {mkdirs()}
        val old=UpdateState(UpdatePhase.READY,release,task,"a".repeat(64),100)
        File(storage,"state.json").writeText(UpdateStateCodec.encode(old))
        val corrupt=File(storage,"$task.apk").apply {writeText("truncated")}
        try {
            val entry=dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint::class.java)
            val repository=AppUpdateRepository(wrapped,entry.runtimeManager())
            assertFalse(repository.verifyForInstallation(old))
            val failed=repository.state.value
            assertEquals(UpdatePhase.FAILED,failed.phase);assertEquals(release,failed.release)
            assertEquals(0L,failed.downloaded);assertNull(failed.sha256);assertFalse(corrupt.exists())
            assertEquals(failed,UpdateStateCodec.decode(File(storage,"state.json").readText()))
        } finally {
            // 作者 long：只清理本用例独占目录，不能删除正在验收的真实更新任务文件。
            storage.listFiles().orEmpty().forEach {check(it.isFile&&it.delete())};check(storage.delete());check(directory.delete())
        }
    }
    @Test fun rejectsCorruptHashAndWrongPackageBeforeInstaller() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val own=File(context.applicationInfo.sourceDir)
        val directory=File(context.cacheDir,"apk-verification-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val copy=File(directory,"candidate.apk");own.copyTo(copy)
            val sha=MessageDigest.getInstance("SHA-256").digest(copy.readBytes()).joinToString("") {"%02x".format(it)}
            val release=AppRelease("0.1.3",1003,"KClash-0.1.3-android-arm64-v8a.apk","https://github.com/$UPDATE_REPOSITORY/releases/download/kclash-v0.1.3/KClash-0.1.3-android-arm64-v8a.apk",copy.length(),"https://github.com/$UPDATE_REPOSITORY/releases/download/kclash-v0.1.3/SHA256SUMS","","",null)
            val wrongHash=runCatching { ApkUpdateVerifier.verify(context,copy,release,"0".repeat(64)) }.exceptionOrNull()
            assertTrue(wrongHash is IllegalArgumentException&&wrongHash.message.orEmpty().contains("SHA256"))
            val wrongPackage=runCatching { ApkUpdateVerifier.verify(context,copy,release,sha) }.exceptionOrNull()
            assertTrue(wrongPackage is IllegalArgumentException&&wrongPackage.message.orEmpty().contains("包名"))
            assertFalse(ApkUpdateVerifier.canUpdate(context))
            assertTrue(copy.delete())
        } finally {
            check(directory.parentFile==context.cacheDir&&directory.name.startsWith("apk-verification-"))
            directory.listFiles().orEmpty().forEach { check(it.isFile&&it.delete()) };check(directory.delete())
        }
    }
    @Test fun explicitStateProtocolSurvivesDiskAndRejectsTamperedPaths() {
        val release=ReleaseCatalog.parse("""[{"tag_name":"kclash-v0.1.3","draft":false,"prerelease":false,"assets":[{"name":"KClash-0.1.3-android-arm64-v8a.apk","size":100,"browser_download_url":"https://github.com/$UPDATE_REPOSITORY/releases/download/kclash-v0.1.3/KClash-0.1.3-android-arm64-v8a.apk"},{"name":"SHA256SUMS","browser_download_url":"https://github.com/$UPDATE_REPOSITORY/releases/download/kclash-v0.1.3/SHA256SUMS"}]}]""").single()
        val state=UpdateState(UpdatePhase.NEEDS_CONFIRMATION,release,UUID.randomUUID().toString(),"a".repeat(64),100,"etag",42,null,true)
        assertEquals(state,UpdateStateCodec.decode(UpdateStateCodec.encode(state)))
        assertTrue(runCatching {UpdateStateCodec.decode(UpdateStateCodec.encode(state).replace(state.token!!,"../bad"))}.isFailure)
        val tampered=org.json.JSONObject(UpdateStateCodec.encode(state)).apply {getJSONObject("release").put("apkUrl","https://evil.example/update.apk")}
        assertTrue(runCatching {UpdateStateCodec.decode(tampered.toString())}.isFailure)
    }
}
