package com.lonnnnnng.flclash.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 作者 long：WebDAV 凭据与 Room 的业务数据边界不同，必须使用 Android Keystore 保护密钥，
 * 再把 AES-GCM 密文写入独立偏好文件；这样备份 ZIP、Room 查询和普通日志都不会拿到密码明文。
 */
data class WebDavCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
    val fileName: String = DEFAULT_WEBDAV_FILE_NAME,
) {
    override fun toString(): String =
        "WebDavCredentials(username=$username, fileName=$fileName, password=[REDACTED_SECRET])"

    fun toEndpoint(): WebDavEndpoint = WebDavEndpoint(
        baseUrl = baseUrl,
        fileName = fileName,
        username = username,
        password = password,
    )
}

interface WebDavCredentialStore {
    fun read(): WebDavCredentials?

    fun save(credentials: WebDavCredentials)

    fun clear()
}

class AndroidWebDavCredentialStore(context: Context) : WebDavCredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    override fun read(): WebDavCredentials? = synchronized(lock) {
        val encodedIv = preferences.getString(IV_KEY, null)
        val encodedCiphertext = preferences.getString(CIPHERTEXT_KEY, null)
        if (encodedIv == null && encodedCiphertext == null) return@synchronized null
        require(!encodedIv.isNullOrEmpty() && !encodedCiphertext.isNullOrEmpty()) {
            "WebDAV 凭据存储不完整"
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val json = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
            gson.fromJson(json, WebDavCredentials::class.java).also(::validate)
        }.getOrElse { error ->
            throw IllegalStateException("无法读取 WebDAV 凭据", error)
        }
    }

    override fun save(credentials: WebDavCredentials) = synchronized(lock) {
        validate(credentials)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(gson.toJson(credentials).toByteArray(Charsets.UTF_8))
        val committed = preferences.edit()
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
        check(committed) { "无法保存 WebDAV 凭据" }
    }

    override fun clear() = synchronized(lock) {
        check(preferences.edit().clear().commit()) { "无法清除 WebDAV 凭据" }
    }

    private fun validate(credentials: WebDavCredentials) {
        require(credentials.baseUrl.isNotBlank()) { "WebDAV 地址不能为空" }
        require(credentials.username.isNotBlank()) { "WebDAV 账号不能为空" }
        require(credentials.password.isNotEmpty()) { "WebDAV 密码不能为空" }
        require(credentials.fileName.matches(FILE_NAME_PATTERN)) {
            "WebDAV 文件名只允许字母、数字、点、下划线和短横线"
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "kclash_webdav_credentials"
        const val IV_KEY = "iv"
        const val CIPHERTEXT_KEY = "ciphertext"
        const val KEY_ALIAS = "com.lonnnnnng.flclash.webdav.credentials.v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        val FILE_NAME_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
