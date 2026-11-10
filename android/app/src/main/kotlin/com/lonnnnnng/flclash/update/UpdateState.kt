package com.lonnnnnng.flclash.update

import org.json.JSONObject

enum class UpdatePhase { IDLE, CHECKING, LATEST, AVAILABLE, DOWNLOADING, VERIFYING, READY, NEEDS_PERMISSION, INSTALLING, NEEDS_CONFIRMATION, INSTALLED, CANCELED, FAILED }
data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val release: AppRelease? = null, val token: String? = null,
    val sha256: String? = null, val downloaded: Long = 0, val etag: String? = null, val installerSession: Int? = null,
    val error: String? = null, val installerCommitted:Boolean=false) {
    val busy get() = phase in setOf(UpdatePhase.CHECKING,UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING,UpdatePhase.INSTALLING)
}

/** 作者 long：更新回执必须跨进程保留，显式磁盘字段避免R8重命名Gson反射字段破坏恢复。 */
internal object UpdateStateCodec {
    fun encode(state: UpdateState): String = JSONObject().apply {
        put("version",1);put("phase",state.phase.name);put("token",state.token);put("sha256",state.sha256)
        put("downloaded",state.downloaded);put("etag",state.etag);put("installerSession",state.installerSession);put("error",state.error)
        put("installerCommitted",state.installerCommitted)
        state.release?.let { r -> put("release",JSONObject().apply {
            put("version",r.version);put("versionCode",r.versionCode);put("apkName",r.apkName);put("apkUrl",r.apkUrl)
            put("apkBytes",r.apkBytes);put("checksumsUrl",r.checksumsUrl);put("notes",r.notes);put("releaseUrl",r.releaseUrl);put("assetDigest",r.assetDigest)
        }) }
    }.toString()
    fun decode(text: String): UpdateState {
        require(text.toByteArray().size<=128*1024)
        val root=JSONObject(text);require(root.getInt("version")==1)
        fun JSONObject.stringOrNull(key:String)=optString(key).takeIf { has(key)&&!isNull(key) }
        val release=root.optJSONObject("release")?.let { r ->
            val version=r.getString("version");val code=ReleaseCatalog.versionCode(version);require(code==r.getLong("versionCode"))
            val name="KClash-$version-android-arm64-v8a.apk";val prefix="https://github.com/$UPDATE_REPOSITORY/releases/download/kclash-v$version/"
            require(r.getString("apkName")==name&&r.getString("apkUrl")==prefix+name&&r.getString("checksumsUrl")==prefix+"SHA256SUMS")
            val bytes=r.getLong("apkBytes");require(bytes in 1..MAX_APK_BYTES)
            AppRelease(version,code,name,prefix+name,bytes,prefix+"SHA256SUMS",r.optString("notes").take(32768),
                "https://github.com/$UPDATE_REPOSITORY/releases/tag/kclash-v$version",r.stringOrNull("assetDigest"))
        }
        val token=root.stringOrNull("token");require(token==null||runCatching { java.util.UUID.fromString(token).toString()==token }.getOrDefault(false))
        val sha=root.stringOrNull("sha256");require(sha==null||sha.matches(Regex("[0-9a-f]{64}")))
        val downloaded=root.getLong("downloaded");require(downloaded in 0..MAX_APK_BYTES)
        return UpdateState(UpdatePhase.valueOf(root.getString("phase")),release,token,sha,downloaded,root.stringOrNull("etag"),
            root.optInt("installerSession",-1).takeIf { it>=0 },root.stringOrNull("error"),root.optBoolean("installerCommitted",false))
    }
}
