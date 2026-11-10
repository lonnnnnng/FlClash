package com.lonnnnnng.flclash.validation

import android.app.Service
import android.content.Intent
import android.os.*
import com.lonnnnnng.flclash.ValidationProcess
import com.lonnnnnng.flclash.core.Core
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** 作者 long：每次完整解析使用独立UID与一次性进程；解绑时直接结束，避免Go全局/后台任务污染下次或主核心。 */
class ConfigValidationService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val accepted=AtomicBoolean(false)
    private val messenger=Messenger(Handler(Looper.getMainLooper()) { message ->
        if(message.what==2) {
            if(ValidationProcess.isCurrent())Process.killProcess(Process.myPid())
            return@Handler true
        }
        if(message.what!=1||!accepted.compareAndSet(false,true))return@Handler false
        val reply=message.replyTo;val data=message.data
        @Suppress("DEPRECATION") val input=data.getParcelable<ParcelFileDescriptor>("input")
        @Suppress("DEPRECATION") val resources=data.getParcelableArrayList<Bundle>("resources").orEmpty()
        scope.launch {
            val opened=mutableListOf<ParcelFileDescriptor>()
            val result=Bundle().apply {putInt("pid",Process.myPid());putInt("uid",Process.myUid())}
            try {
                check(ValidationProcess.isCurrent())
                val source=checkNotNull(input).also {opened+=it}
                val content=ParcelFileDescriptor.AutoCloseInputStream(source).use { stream ->
                    val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val count=stream.read(buffer);if(count<0)break;check(output.size()+count<=8*1024*1024);output.write(buffer,0,count)}
                    output.toByteArray()
                }
                val descriptors=JSONArray()
                for(resource in resources) {
                    @Suppress("DEPRECATION") val fd=checkNotNull(resource.getParcelable<ParcelFileDescriptor>("fd"))
                    opened+=fd
                    descriptors.put(JSONObject().put("name",resource.getString("name")).put("fd",fd.fd).put("bytes",resource.getLong("bytes")))
                }
                result.putString("result",Core.validateConfigurationIsolated(content,descriptors.toString()))
                result.putInt("pid",Process.myPid());result.putInt("uid",Process.myUid())
            } catch(_:Exception) {
                result.putString("result","""{"passed":false,"code":"validation_service_failed","section":"config","index":-1,"deferredResources":0}""")
            } finally {opened.forEach {runCatching {it.close()}}}
            runCatching {reply.send(Message.obtain().apply {what=2;setData(result)})}
        }
        true
    })
    override fun onBind(intent:Intent?)=messenger.binder
    override fun onDestroy() {
        scope.cancel();super.onDestroy()
        // 作者 long：取消协程无法中断已经进入JNI的Go解析；独立UID结束才真正释放native资源和半途解析。
        if(ValidationProcess.isCurrent())Process.killProcess(Process.myPid())
    }
}
