package com.lonnnnnng.flclash.data

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AGE_ARMOR_HEADER = "-----BEGIN AGE ENCRYPTED FILE-----".toByteArray(Charsets.US_ASCII)

fun isAgeEncryptedProfile(bytes: ByteArray): Boolean =
    bytes.size >= AGE_ARMOR_HEADER.size && AGE_ARMOR_HEADER.indices.all { bytes[it] == AGE_ARMOR_HEADER[it] }

class AgeSecretKeyRequiredException : IllegalArgumentException("这是 age 加密配置，请输入解密密钥")

fun interface ProfileContentDecoder {
    suspend fun decode(bytes: ByteArray, ageSecretKey: String): ByteArray
}

/** 作者 long：明文导入不依赖核心；未接入解密器的宿主必须拒绝密文，不能把密文当作可运行配置保存。 */
object PlainProfileContentDecoder : ProfileContentDecoder {
    override suspend fun decode(bytes: ByteArray, ageSecretKey: String): ByteArray {
        if (isAgeEncryptedProfile(bytes)) {
            if (ageSecretKey.isBlank()) throw AgeSecretKeyRequiredException()
            error("age 解密器不可用")
        }
        return bytes
    }
}

class AgeProfileContentDecoder(
    private val cacheDir: File,
    private val decrypt: suspend (path: String, secretKey: String) -> String,
) : ProfileContentDecoder {
    override suspend fun decode(bytes: ByteArray, ageSecretKey: String): ByteArray = withContext(Dispatchers.IO) {
        require(bytes.size <= MAX_PROFILE_BYTES) { "配置文件超过 8 MiB" }
        // 作者 long：沿用原版从第零字节识别 armor 的规则；明文即使填写了密钥也不应触发解密或密钥校验。
        if (!isAgeEncryptedProfile(bytes)) return@withContext bytes
        val key = ageSecretKey.trim()
        if (key.isEmpty()) throw AgeSecretKeyRequiredException()
        cacheDir.mkdirs()
        val encrypted = File.createTempFile("age-import-", ".tmp", cacheDir)
        try {
            encrypted.writeBytes(bytes)
            val plaintext = try {
                decrypt(encrypted.absolutePath, key).toByteArray(Charsets.UTF_8)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 作者 long：identity 解析错误可能包含用户输入，不把原错误或 cause 传到通知、日志和页面。
                throw IllegalArgumentException("age 解密失败，请检查密钥与配置文件")
            }
            require(plaintext.isNotEmpty() && plaintext.size <= MAX_PROFILE_BYTES) { "解密后的配置为空或超过 8 MiB" }
            plaintext
        } finally {
            // 作者 long：只删除本次独占临时密文；明文始终留在内存，验证通过后才由仓库原子写入正式缓存。
            encrypted.delete()
        }
    }

    private companion object {
        const val MAX_PROFILE_BYTES = 8 * 1024 * 1024
    }
}
