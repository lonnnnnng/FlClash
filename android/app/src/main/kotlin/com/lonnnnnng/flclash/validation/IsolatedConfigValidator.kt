package com.lonnnnnng.flclash.validation

import android.content.*
import android.os.*
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.data.ProfileConfigValidator
import com.lonnnnnng.flclash.data.YamlProfileConfigValidator
import com.lonnnnnng.flclash.service.YamlConfigCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** 作者 long：片段保留YAML检查；整份配置另走隔离核心，不把CUSTOM虚拟路径或代理资源片段误当运行配置。 */
@Singleton
class IsolatedConfigValidator @Inject constructor(@ApplicationContext private val context:Context):ProfileConfigValidator {
    private val mutex=Mutex()
    private var pendingExit:CompletableDeferred<Unit>?=null
    override suspend fun validate(bytes:ByteArray)=YamlProfileConfigValidator().validate(bytes)
    override suspend fun validateFull(bytes:ByteArray):com.lonnnnnng.flclash.data.ProfileValidationSummary {
        validate(bytes)
        return com.lonnnnnng.flclash.data.ProfileValidationSummary(checkFull(bytes).deferredResources)
    }
    internal suspend fun checkFull(bytes:ByteArray):ConfigSemanticResult=mutex.withLock {withContext(Dispatchers.IO) {
        pendingExit?.let { previous ->
            if(withTimeoutOrNull(3_000){previous.await();true}!=true)throw ConfigSemanticException("validation_process_died","config",-1)
            pendingExit=null
        }
        val resources=openResources(bytes)
        val connected=CompletableDeferred<IBinder>();val response=CompletableDeferred<Bundle>();val died=CompletableDeferred<Unit>()
        val death=IBinder.DeathRecipient {died.complete(Unit);response.completeExceptionally(ConfigSemanticException("validation_process_died","config",-1))}
        var binder:IBinder?=null;var bound=false
        val pipe=ParcelFileDescriptor.createReliablePipe()
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName?,service:IBinder?){connected.complete(checkNotNull(service))}
            override fun onServiceDisconnected(name:ComponentName?){died.complete(Unit);response.completeExceptionally(ConfigSemanticException("validation_process_died","config",-1))}
            override fun onNullBinding(name:ComponentName?){connected.completeExceptionally(ConfigSemanticException("validation_service_failed","config",-1))}
            override fun onBindingDied(name:ComponentName?){died.complete(Unit);response.completeExceptionally(ConfigSemanticException("validation_process_died","config",-1))}
        }
        val reply=Messenger(Handler(Looper.getMainLooper()){message ->if(message.what==2)response.complete(message.data);true})
        try {
            bound=context.bindService(Intent(context,ConfigValidationService::class.java),connection,Context.BIND_AUTO_CREATE)
            check(bound){"无法启动配置校验"}
            val result=try {withTimeout(30_000) {
                val remote=connected.await();binder=remote;remote.linkToDeath(death,0)
                Messenger(remote).send(Message.obtain().apply {what=1;replyTo=reply;data=Bundle().apply {
                    putParcelable("input",pipe[0]);putParcelableArrayList("resources",ArrayList(resources.map {resource ->Bundle().apply {
                        putString("name",resource.name);putParcelable("fd",resource.fd);putLong("bytes",resource.bytes)
                    }}))
                }})
                // 作者 long：可靠管道读端要保留至结果回执，否则复制的状态通道可能把合法EOF当作对端死亡。
                val writer=launch {writeValidationPipe(pipe[1],bytes)}
                val bundle=response.await();writer.join()
                check(bundle.getInt("uid")!=Process.myUid()&&bundle.getInt("pid")!=Process.myPid())
                ConfigSemanticResult.decode(checkNotNull(bundle.getString("result")))
            }}catch(failure:TimeoutCancellationException){throw ConfigSemanticException("validation_timeout","config",-1)}
            if(!result.passed)throw ConfigSemanticException(result.code,result.section,result.index)
            result
        } finally {
            // 作者 long：先显式终止一次性服务，不能只依赖系统何时销毁解绑服务；超时未确认退出则阻止后续复用。
            binder?.let { runCatching {Messenger(it).send(Message.obtain().apply {what=2})} }
            if(bound)context.unbindService(connection)
            pipe.forEach {runCatching {it.close()}};resources.forEach {runCatching {it.fd.close()}}
            if(binder!=null)withContext(NonCancellable) {
                val exited=withTimeoutOrNull(3_000){died.await();true}==true
                if(exited)runCatching {binder?.unlinkToDeath(death,0)} else pendingExit=died
            }
        }
    }}
    private data class Resource(val name:String,val fd:ParcelFileDescriptor,val bytes:Long)
    private fun openResources(bytes:ByteArray):List<Resource> {
        val root=File(context.filesDir,"mihomo").canonicalFile
        val resources=mutableListOf<Resource>()
        try {
            for(name in listOf("GEOIP.metadb","GEOIP.dat","GEOSITE.dat","ASN.mmdb")) {
                val current=File(root,name)
                if(current.isFile)resources+=Resource(name,ParcelFileDescriptor.open(current,ParcelFileDescriptor.MODE_READ_ONLY),current.length())
                else {
                    // 作者 long：首次导入只复制APK内置资源到本任务临时文件，不调用会初始化/替换主核心资源的prepare。
                    val scratch=File.createTempFile("config-validation-",".asset",context.cacheDir)
                    try {
                        context.assets.open(name).use {input ->scratch.outputStream().use {input.copyTo(it)}}
                        resources+=Resource(name,ParcelFileDescriptor.open(scratch,ParcelFileDescriptor.MODE_READ_ONLY),scratch.length())
                    }finally {check(scratch.delete())}
                }
            }
            val json=JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8),preserveNulls=true))
            val paths=linkedSetOf<String>()
            fun collect(node:JsonElement) {
                if(node.isJsonArray)node.asJsonArray.forEach(::collect)
                else if(node.isJsonObject)node.asJsonObject.entrySet().forEach { (key,value) ->
                    if(key in setOf("certificate","private-key","client-auth-cert","ech-key","planet")&&value.isJsonPrimitive&&value.asJsonPrimitive.isString) {
                        val raw=value.asString
                        if(raw.length in 1..4096&&!raw.contains('\n')&&!raw.contains("PRIVATE KEY")&&!raw.contains("BEGIN "))paths+=raw
                    }
                    if(key=="custom-certifactes"&&value.isJsonArray)value.asJsonArray.forEach {cert ->
                        if(cert.isJsonPrimitive&&cert.asJsonPrimitive.isString) {
                            val raw=cert.asString
                            if(raw.length in 1..4096&&!raw.contains('\n')&&!raw.contains("BEGIN "))paths+=raw
                        }
                    }
                    if(key=="path"&&node.asJsonObject.get("type")?.takeIf {it.isJsonPrimitive}?.asString=="file"&&value.isJsonPrimitive)paths+=value.asString
                    collect(value)
                }
            }
            collect(json)
            for(path in paths) {
                val file=(if(File(path).isAbsolute)File(path)else File(root,path)).canonicalFile
                // 作者 long：Android上游IsSafePath默认放行，主进程不能沿候选任意路径开放私有文件，只授权核心目录内已有普通资源。
                if(!file.path.startsWith(root.path+File.separator)||!file.isFile||file.length() !in 1..64L*1024*1024)continue
                if(resources.none {it.name==path})resources+=Resource(path,ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY),file.length())
            }
            require(resources.size<=64&&resources.sumOf {it.bytes}<=128L*1024*1024){"配置依赖资源超过校验上限"}
            return resources
        }catch(failure:Throwable){resources.forEach {runCatching{it.fd.close()}};throw failure}
    }
}

data class ConfigSemanticResult(val passed:Boolean,val code:String,val section:String,val index:Int,val deferredResources:Int) {
    companion object {fun decode(text:String):ConfigSemanticResult {
        require(text.length<=4096);val data=JSONObject(text)
        return ConfigSemanticResult(data.getBoolean("passed"),data.getString("code"),data.getString("section"),data.getInt("index"),data.optInt("deferredResources"))
    }}
}
class ConfigSemanticException(val code:String,val section:String,val index:Int):IllegalArgumentException(when(code){
    "resource_unavailable"->"配置依赖的本地资源未提供，已保留原配置"
    "resource_invalid"->"配置依赖的本地资源损坏或格式无效，已保留原配置"
    "validation_timeout"->"核心语义校验超时，输入已保留，请重试"
    "validation_process_died","validation_service_failed"->"核心语义校验未完成，输入已保留，请重试"
    else->"核心语义校验未通过，请检查${sectionLabel(section)}${if(index>=0)"第 ${index+1} 项"else ""}"
}) {
    override fun toString()="ConfigSemanticException(code=$code, section=$section, index=$index)"
}
private fun sectionLabel(section:String)=when(section){
    "proxies"->"节点";"proxy-groups"->"策略组";"proxy-providers"->"代理资源";"rule-providers"->"规则资源"
    "rules","sub-rules"->"规则";"dns"->"DNS";"tls"->"TLS";"listeners"->"入站";"tun"->"TUN";"sniffer"->"嗅探";else->"配置"
}
