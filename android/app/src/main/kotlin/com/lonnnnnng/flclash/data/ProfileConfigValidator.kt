package com.lonnnnnng.flclash.data

import com.lonnnnnng.flclash.service.YamlConfigCodec

/**
 * 作者 long：订阅更新先经过独立校验，再进入 Profile 文件替换流程；接口保留独立边界，
 * 以后可以接入更严格的 Mihomo 校验而不让下载客户端直接修改用户文件。
 */
fun interface ProfileConfigValidator {
    suspend fun validate(bytes: ByteArray)
}

class YamlProfileConfigValidator : ProfileConfigValidator {
    override suspend fun validate(bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "订阅内容为空" }
        YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))
    }
}
