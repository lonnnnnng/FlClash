package com.lonnnnnng.flclash.update

import android.content.Context
import android.util.AtomicFile
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lonnnnnng.flclash.service.CoreRuntimeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class AppUpdateRepository @Inject constructor(@ApplicationContext internal val context: Context, private val runtime: CoreRuntimeManager) {
    private val lock = Mutex()
    internal val actionLock=Mutex()
    private val downloadLock=Mutex()
    @Volatile internal var installationInProgress=false
    private val directory = File(context.noBackupFilesDir,"app-update").apply { mkdirs() }
    private val disk = AtomicFile(File(directory,"state.json"))
    private val mutable = MutableStateFlow(readState())
    val state = mutable.asStateFlow()
    @Volatile private var checkingToken:String?=null
    internal var pendingConfirmation: android.content.Intent? = null
        private set
    val supported get() = ApkUpdateVerifier.canUpdate(context)
    val currentVersion get() = ApkUpdateVerifier.installed(context).versionName.orEmpty()
    private fun readState(): UpdateState = try {
        if (!disk.baseFile.exists()) UpdateState() else disk.openRead().use { UpdateStateCodec.decode(it.bufferedReader().readText()) }
    } catch (_: Exception) { UpdateState(UpdatePhase.FAILED,error="更新状态无法读取，请重新检查版本") }
    private fun save(next:UpdateState) {
        val output=disk.startWrite()
        try { output.write(UpdateStateCodec.encode(next).toByteArray());disk.finishWrite(output);mutable.value=next }
        catch(failure:Throwable){disk.failWrite(output);throw failure}
    }
    private fun file(token:String,extension:String):File {
        require(UUID.fromString(token).toString()==token&&extension in setOf("apk","part"))
        return File(directory,"$token.$extension")
    }
    internal fun apk(token:String)=file(token,"apk")
    private fun current(token:String)=mutable.value.token==token&&mutable.value.phase !in setOf(UpdatePhase.CANCELED,UpdatePhase.INSTALLED)
    private suspend fun change(token:String,block:(UpdateState)->UpdateState)=lock.withLock {
        if(current(token)) save(block(mutable.value))
    }
    suspend fun check()=withContext(Dispatchers.IO) {
        val token=UUID.randomUUID().toString()
        actionLock.withLock { downloadLock.withLock { lock.withLock {
            check(!mutable.value.busy&&mutable.value.phase !in setOf(UpdatePhase.READY,UpdatePhase.NEEDS_PERMISSION,UpdatePhase.NEEDS_CONFIRMATION)) { "请先处理或取消当前更新" }
            mutable.value.token?.let { old -> apk(old).delete();file(old,"part").delete() }
            save(UpdateState(UpdatePhase.CHECKING,token=token))
            checkingToken=token
        } } }
        try {
            val releases=UpdateTransport(runtime.downloadRoute()).use { it.text("https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=100",2*1024*1024) }
            val latest=ReleaseCatalog.parse(releases).firstOrNull()?:error("没有找到可验证的KClash正式版本")
            val installed=ApkUpdateVerifier.code(ApkUpdateVerifier.installed(context))
            change(token){UpdateState(if(latest.versionCode>installed)UpdatePhase.AVAILABLE else UpdatePhase.LATEST,latest)}
        } catch(canceled:CancellationException){withContext(NonCancellable){change(token){it.copy(phase=UpdatePhase.CANCELED,error=null)}};throw canceled}
        catch(_:Exception){change(token){it.copy(phase=UpdatePhase.FAILED,error="检查更新失败，请检查网络后重试")}}
        finally {if(checkingToken==token)checkingToken=null}
    }
    suspend fun download()=actionLock.withLock { withContext(Dispatchers.IO) {
        check(supported) { "当前安装包不是同签名正式版，无法覆盖更新" }
        val previous=mutable.value
        check(!previous.busy&&previous.phase in setOf(UpdatePhase.AVAILABLE,UpdatePhase.FAILED,UpdatePhase.CANCELED)) { "当前更新正在处理" }
        previous.token?.let { WorkManager.getInstance(context).cancelUniqueWork(workName(it)).result.get(10,java.util.concurrent.TimeUnit.SECONDS) }
        val token=downloadLock.withLock { lock.withLock {
            val old=mutable.value;check(!old.busy&&old.phase !in setOf(UpdatePhase.INSTALLED,UpdatePhase.LATEST))
            val release=checkNotNull(old.release);check(release.versionCode>ApkUpdateVerifier.code(ApkUpdateVerifier.installed(context)))
            // 作者 long：失败后的显式重试复用本任务私有部分文件，校验清单已固定；换发布版本则分配新任务。
            val id=UUID.randomUUID().toString()
            val resuming=old.phase==UpdatePhase.FAILED&&old.sha256!=null&&old.token!=null
            if(resuming) {
                val part=file(checkNotNull(old.token),"part")
                if(part.isFile)check(part.renameTo(file(id,"part"))) { "无法恢复未完成下载" }
            }
            old.token?.let { apk(it).delete();file(it,"part").delete() }
            save(old.copy(phase=UpdatePhase.DOWNLOADING,token=id,error=null,installerSession=null,
                installerCommitted=false,sha256=old.sha256.takeIf { resuming },etag=old.etag.takeIf { resuming },
                downloaded=file(id,"part").takeIf { it.isFile }?.length()?:0));id
        } }
        enqueue(token)
    } }
    private fun enqueue(token:String) {
        val request=OneTimeWorkRequestBuilder<AppUpdateWorker>().setInputData(workDataOf("token" to token))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(token),ExistingWorkPolicy.KEEP,request).result.get(10,java.util.concurrent.TimeUnit.SECONDS)
    }
    suspend fun refresh()=withContext(Dispatchers.IO) {
        lock.withLock {
            val old=mutable.value
            if(old.release!=null&&ApkUpdateVerifier.code(ApkUpdateVerifier.installed(context))>=old.release.versionCode&&
                old.phase in setOf(UpdatePhase.INSTALLING,UpdatePhase.NEEDS_CONFIRMATION,UpdatePhase.NEEDS_PERMISSION,UpdatePhase.READY,UpdatePhase.INSTALLED)) {
                check(ApkUpdateVerifier.canUpdate(context));save(old.copy(phase=UpdatePhase.INSTALLED,error=null));old.token?.let { apk(it).delete();file(it,"part").delete() };return@withLock
            }
            if(old.phase==UpdatePhase.CHECKING&&checkingToken!=old.token)save(old.copy(phase=UpdatePhase.FAILED,error="上次检查已中断，请重试"))
            if(old.phase==UpdatePhase.NEEDS_PERMISSION&&android.os.Build.VERSION.SDK_INT>=26&&context.packageManager.canRequestPackageInstalls())
                save(old.copy(phase=UpdatePhase.READY,error=null))
            if(old.phase in setOf(UpdatePhase.READY,UpdatePhase.NEEDS_PERMISSION,UpdatePhase.NEEDS_CONFIRMATION)&&old.token?.let { apk(it).isFile }!=true)
                save(old.copy(phase=UpdatePhase.FAILED,error="已下载的安装包已不存在，请重新下载"))
            if(old.phase in setOf(UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING)&&old.token!=null) {
                val work=WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName(old.token)).get(10,java.util.concurrent.TimeUnit.SECONDS)
                if(work.isEmpty())enqueue(old.token)
                else if(work.all { it.state.isFinished })save(old.copy(phase=UpdatePhase.FAILED,error="下载已中断，可继续下载"))
            }
            if(old.phase==UpdatePhase.INSTALLING&&old.installerSession!=null&&!installationInProgress) {
                val session=context.packageManager.packageInstaller.getSessionInfo(old.installerSession)
                if(session==null)save(old.copy(phase=UpdatePhase.READY,installerSession=null,installerCommitted=false,error="安装未完成，请重试"))
                else if(!old.installerCommitted) {
                    context.packageManager.packageInstaller.abandonSession(old.installerSession)
                    save(old.copy(phase=UpdatePhase.READY,installerSession=null,installerCommitted=false,error="安装准备已中断，请重新安装"))
                } else save(old.copy(phase=UpdatePhase.NEEDS_CONFIRMATION))
            }
        }
    }
    suspend fun cancel()=actionLock.withLock { withContext(Dispatchers.IO) {
        val old=lock.withLock {mutable.value.also { save(it.copy(phase=UpdatePhase.CANCELED,error=null));pendingConfirmation=null }}
        old.token?.let { WorkManager.getInstance(context).cancelUniqueWork(workName(it)).result.get(10,java.util.concurrent.TimeUnit.SECONDS) }
        old.installerSession?.let { runCatching { context.packageManager.packageInstaller.abandonSession(it) } }
        downloadLock.withLock { old.token?.let { apk(it).delete();file(it,"part").delete() } }
    } }
    internal suspend fun performDownload(token:String):Boolean=downloadLock.withLock { withContext(Dispatchers.IO) {
        val initial=lock.withLock { mutable.value.takeIf { current(token)&&it.phase in setOf(UpdatePhase.DOWNLOADING,UpdatePhase.VERIFYING) } }?:return@withContext false
        val release=checkNotNull(initial.release)
        try {
            UpdateTransport(runtime.downloadRoute()).use { transport ->
                val checksum=initial.sha256?:ReleaseCatalog.checksum(transport.text(release.checksumsUrl,64*1024),release)
                change(token){it.copy(sha256=checksum)}
                val part=file(token,"part");val ready=apk(token)
                if(!ready.isFile) {
                    downloadUpdateApk(part, release.apkBytes, initial.etag,
                        request = { headers, read -> transport.response(release.apkUrl, headers, read) },
                        progress = { bytes, etag ->
                            if (!current(token)) throw CancellationException("下载任务已取消")
                            change(token) { it.copy(downloaded = bytes, etag = etag) }
                        })
                    change(token){it.copy(phase=UpdatePhase.VERIFYING,downloaded=release.apkBytes)}
                    ApkUpdateVerifier.verify(context,part,release,checksum)
                    check(part.renameTo(ready)) { "无法保存已验证安装包" }
                } else ApkUpdateVerifier.verify(context,ready,release,checksum)
                change(token){it.copy(phase=UpdatePhase.READY,downloaded=release.apkBytes,error=null)}
            }
            true
        } catch(canceled:CancellationException){throw canceled}
        catch(failure:Exception){
            val message=if(failure is java.io.IOException)"下载连接中断，可继续下载" else failure.message?.takeIf { it.length<180&&!it.contains("https:") }?:"更新下载或校验失败，请重试"
            if(failure !is java.io.IOException){apk(token).delete();file(token,"part").delete()}
            change(token){it.copy(phase=UpdatePhase.FAILED,error=message,downloaded=if(failure is java.io.IOException)file(token,"part").takeIf { file -> file.isFile }?.length()?:0 else 0)};false
        } finally {
            if(mutable.value.token==token&&mutable.value.phase==UpdatePhase.CANCELED){apk(token).delete();file(token,"part").delete()}
        }
    } }
    internal suspend fun verifyForInstallation(state: UpdateState): Boolean {
        val token = checkNotNull(state.token)
        return try {
            ApkUpdateVerifier.verify(context, apk(token), checkNotNull(state.release), checkNotNull(state.sha256))
            true
        } catch (failure: Exception) {
            // 作者 long：安装前发现私有APK已损坏时退出READY，保留发布信息给重下载入口，不能反复提交同一坏包。
            apk(token).delete()
            file(token, "part").delete()
            state.installerSession?.let { runCatching { context.packageManager.packageInstaller.abandonSession(it) } }
            pendingConfirmation = null
            change(token) { it.copy(phase = UpdatePhase.FAILED, downloaded = 0, sha256 = null, etag = null,
                installerSession = null, installerCommitted = false, error = "安装包已失效，请重新下载") }
            false
        }
    }
    internal suspend fun setInstaller(token:String,phase:UpdatePhase,session:Int?,error:String?=null,committed:Boolean=false)=change(token){it.copy(phase=phase,installerSession=session,error=error,installerCommitted=committed)}
    internal suspend fun markInstallerCommitted(token:String,session:Int)=change(token){
        if(it.installerSession==session)it.copy(installerCommitted=true) else it
    }
    internal suspend fun failTask(token:String,error:String)=change(token){it.copy(phase=UpdatePhase.FAILED,error=error)}
    internal suspend fun installerResult(token:String,session:Int,status:Int,confirmation:android.content.Intent?)=lock.withLock {
        val old=mutable.value;if(!current(token)||old.installerSession!=session)return@withLock
        when(status){
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {pendingConfirmation=confirmation;save(old.copy(phase=UpdatePhase.NEEDS_CONFIRMATION,installerCommitted=true))}
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> {
                if(old.release!=null&&ApkUpdateVerifier.canUpdate(context)&&ApkUpdateVerifier.code(ApkUpdateVerifier.installed(context))>=old.release.versionCode)
                    {save(old.copy(phase=UpdatePhase.INSTALLED,error=null));apk(token).delete();file(token,"part").delete()}
                else save(old.copy(phase=UpdatePhase.FAILED,error="安装结果尚未确认，请返回应用检查"))
            }
            else -> save(old.copy(phase=UpdatePhase.READY,installerSession=null,installerCommitted=false,error=if(status==android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED)"安装已取消" else "系统安装未完成，请重试"))
        }
    }
    internal suspend fun takeConfirmation():android.content.Intent?=lock.withLock {pendingConfirmation.also { pendingConfirmation=null }}
    internal companion object { const val WORK_NAME="kclash-app-update";fun workName(token:String)="$WORK_NAME-$token" }
}
