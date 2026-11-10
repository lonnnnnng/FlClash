package com.lonnnnnng.flclash.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal object ApkUpdateVerifier {
    private fun flags() = if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION") fun installed(context:Context):PackageInfo = context.packageManager.getPackageInfo(context.packageName,flags())
    @Suppress("DEPRECATION") fun code(info:PackageInfo):Long = if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") private fun signatures(info:PackageInfo):Set<String> {
        val certificates=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
        return certificates.orEmpty().map { sha(it.toByteArray()) }.toSet()
    }
    fun canUpdate(context:Context):Boolean = context.packageName==UPDATE_PACKAGE&&signatures(installed(context))==setOf(UPDATE_CERTIFICATE)
    @Suppress("DEPRECATION") fun verify(context:Context,file:File,release:AppRelease,expectedSha:String) {
        require(file.isFile&&file.length()==release.apkBytes) { "安装包大小不符合发布记录" }
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer=ByteArray(64*1024);while(true){val count=stream.read(buffer);if(count<0)break;digest.update(buffer,0,count)} }
        require(hex(digest.digest())==expectedSha) { "安装包SHA256校验失败，请重新下载" }
        val archive=context.packageManager.getPackageArchiveInfo(file.path,flags()) ?: error("无法读取或验证安装包签名")
        require(archive.packageName==UPDATE_PACKAGE) { "安装包包名不匹配" }
        require(code(archive)==release.versionCode&&archive.versionName==release.version) { "安装包版本与发布记录不一致" }
        val current=installed(context)
        require(code(archive)>code(current)) { "安装包版本不高于当前版本" }
        // 作者 long：当前发布身份固定，拒绝异签名、额外签名及未授权轮换；系统安装器还会执行最终升级校验。
        require(signatures(archive)==setOf(UPDATE_CERTIFICATE)&&signatures(current)==setOf(UPDATE_CERTIFICATE)) { "安装包签名与当前正式版本不一致" }
        require(context.packageName==UPDATE_PACKAGE) { "请在正式版中使用应用内更新" }
    }
    private fun sha(bytes:ByteArray)=hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes:ByteArray)=bytes.joinToString("") { "%02x".format(it) }
}
