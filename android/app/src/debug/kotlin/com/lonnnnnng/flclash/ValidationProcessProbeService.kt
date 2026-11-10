package com.lonnnnnng.flclash

import android.app.Service
import android.content.Intent
import android.os.*
import com.lonnnnnng.flclash.core.Core
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*

/** 作者 long：先实测独立UID、管道输入和native加载，再决定完整语义解析接法；此探针不声称配置有效。 */
class ValidationProcessProbeService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val messenger=Messenger(Handler(Looper.getMainLooper()){message ->
        if(message.what!=1)return@Handler false
        val reply=message.replyTo;val data=message.data
        @Suppress("DEPRECATION") val input=data.getParcelable<ParcelFileDescriptor>("input")
        if(input==null)return@Handler false
        scope.launch {
            val result=Bundle()
            var stage="read-pipe"
            try {
                val bytes=ParcelFileDescriptor.AutoCloseInputStream(input).use { stream ->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val count=stream.read(buffer);if(count<0)break;check(output.size()+count<=8*1024*1024);output.write(buffer,0,count)}
                    output.toByteArray()
                }
                stage="verify-isolation"
                result.putString("sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
                result.putInt("bytes",bytes.size);result.putInt("pid",Process.myPid());result.putInt("uid",Process.myUid())
                result.putBoolean("isolatedProcess",ValidationProcess.isCurrent())
                result.putBoolean("mainFileReadable",runCatching {File(data.getString("canary")!!).readBytes();true}.getOrDefault(false))
                stage="native-bootstrap"
                result.putString("nativeBootstrap",Core.networkBootstrapSnapshot())
                result.putBoolean("passed",true)
            }catch(failure:Throwable){
                // 作者 long：隔离失败必须保留阶段与静态调用点；异常正文可能包含候选路径，不回传原始消息。
                result.putString("failureType",failure.javaClass.name)
                result.putString("failureStage",stage)
                result.putString("failureFrames",failure.stackTrace.take(6).joinToString("\n") {"${it.className}.${it.methodName}:${it.lineNumber}"})
                result.putBoolean("passed",false)
            }
            reply.send(Message.obtain().apply {what=2;setData(result)})
        }
        true
    })
    override fun onBind(intent:Intent?)=messenger.binder
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
