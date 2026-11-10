package com.lonnnnnng.flclash

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.data.ProfileType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ValidationProcessProbeInstrumentedTest {
    @Test fun isolatedNativeProcessReadsOnlyPassedPipeAndCannotReadMainFile()=runBlocking(Dispatchers.IO) {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        assertEquals("com.lonnnnnng.flclash.motion",context.packageName)
        val entry=EntryPointAccessors.fromApplication(context.applicationContext,ConnectionAcceptanceEntryPoint::class.java)
        val sha=InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256").orEmpty();require(sha.matches(Regex("[a-f0-9]{64}")))
        val row=entry.database().profiles().getAll().single {it.type==ProfileType.FILE}
        val bytes=entry.profileRepository().profileFile(row.id).readBytes()
        assertEquals(sha,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        val canary=File.createTempFile("validation-own-",".bin",context.cacheDir).apply {writeText("private-canary")}
        val connected=CompletableDeferred<IBinder>();val response=CompletableDeferred<Bundle>()
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName?,service:IBinder?){connected.complete(checkNotNull(service))}
            override fun onServiceDisconnected(name:ComponentName?){if(!response.isCompleted)response.completeExceptionally(IllegalStateException("validation process disconnected"))}
            override fun onNullBinding(name:ComponentName?){connected.completeExceptionally(IllegalStateException("validation binding unavailable"))}
        }
        val reply=Messenger(Handler(Looper.getMainLooper()){message->response.complete(message.data);true})
        val pipe=ParcelFileDescriptor.createReliablePipe()
        var bound=false
        try {
            bound=context.bindService(Intent(context,ValidationProcessProbeService::class.java),connection,Context.BIND_AUTO_CREATE);assertTrue(bound)
            withTimeout(20_000){
                val binder=connected.await()
                Messenger(binder).send(Message.obtain().apply {what=1;replyTo=reply;data=Bundle().apply {putParcelable("input",pipe[0]);putString("canary",canary.path)}})
                // 作者 long：可靠管道的状态通道随PFD一起复制；接收端完成前关闭本地读端会让写端误认对端已退出。
                // 保留读端直到结果回执，仍由finally关闭，不能把有效输入的EOF误报成远端死亡。
                val writer=launch {ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use {it.write(bytes)}}
                val result=response.await();writer.join()
                assertTrue("${result.getString("failureStage")}: ${result.getString("failureType")}\n${result.getString("failureFrames")}",result.getBoolean("passed"));assertTrue(result.getBoolean("isolatedProcess"))
                assertNotEquals(Process.myUid(),result.getInt("uid"));assertNotEquals(Process.myPid(),result.getInt("pid"))
                assertFalse(result.getBoolean("mainFileReadable"));assertEquals(sha,result.getString("sha256"));assertEquals(bytes.size,result.getInt("bytes"))
                assertNotNull(result.getString("nativeBootstrap"))
                ins.sendStatus(2,Bundle().apply {putString("kclash_validation_probe",org.json.JSONObject().put("passed",true)
                    .put("uid",result.getInt("uid")).put("pid",result.getInt("pid")).put("mainUid",Process.myUid()).put("bytes",bytes.size)
                    .put("sha256",sha).put("mainFileReadable",false).put("nativeLoaded",true).toString())})
            }
        } finally {if(bound)context.unbindService(connection);runCatching{pipe[0].close()};runCatching{pipe[1].close()};assertTrue(canary.delete())}
    }
}
