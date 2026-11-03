package com.lonnnnnng.flclash.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ProfileDraftStore {
    suspend fun read(): ByteArray?
    suspend fun write(content: ByteArray?)
}

object NoopProfileDraftStore : ProfileDraftStore {
    override suspend fun read(): ByteArray? = null
    override suspend fun write(content: ByteArray?) = Unit
}

/** 作者 long：未确认正文和密钥只能进入本机Keystore密文，不能写Bundle、备份或正式配置。 */
@Singleton
class AndroidProfileDraftStore @Inject constructor(@ApplicationContext context: Context) : ProfileDraftStore by EncryptedDraftFile(
    context, "profile-editor-draft.v1", "com.lonnnnnng.flclash.profile.draft.v1")

/** 作者 long：不同资源的未确认会话分文件加密，清除脚本选择不能覆盖配置正文草稿。 */
internal class EncryptedDraftFile(context: Context, fileName: String, private val keyAlias: String) : ProfileDraftStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val lock = Any()

    override suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
            require(file.baseFile.length() <= MAX_BYTES + 64L) { "配置草稿过大" }
            val bytes = file.readFully()
            require(bytes.size >= 29 && bytes[0] == 1.toByte()) { "配置草稿版本无效" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.doFinal(bytes, 13, bytes.size - 13)
        }
    }

    override suspend fun write(content: ByteArray?) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (content == null) {
                file.delete()
                check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) { "配置草稿清除失败" }
                return@synchronized
            }
            require(content.size <= MAX_BYTES) { "配置草稿过大" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(content)
            // 作者 long：保留上一份完整密文直到新快照提交，后台中断不能留下半份草稿。
            val stream = file.startWrite()
            try {
                stream.write(encrypted)
                file.finishWrite(stream)
            } catch (failure: Exception) {
                file.failWrite(stream)
                throw failure
            }
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private companion object {
        const val MAX_BYTES = 64 * 1024 * 1024
    }
}
