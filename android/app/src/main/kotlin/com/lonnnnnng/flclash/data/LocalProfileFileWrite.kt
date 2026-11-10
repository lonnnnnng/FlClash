package com.lonnnnnng.flclash.data

import java.io.File
import java.io.IOException
import com.lonnnnnng.flclash.common.StateWriteAudit

internal class LocalProfileFileSnapshot(val bytes: ByteArray?, val modified: Long) {
    override fun toString(): String = "LocalProfileFileSnapshot(size=${bytes?.size})"
}

internal fun snapshotLocalProfileFile(target: File): LocalProfileFileSnapshot = LocalProfileFileSnapshot(
    target.takeIf(File::isFile)?.inputStream()?.use { readProfileFileBytes(it, allowEmpty = true) }, target.lastModified(),
)

internal suspend fun <T> replaceLocalProfileFile(
    target: File,
    bytes: ByteArray,
    persist: suspend (commitFile: () -> Unit) -> T,
    rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
    expected: LocalProfileFileSnapshot = snapshotLocalProfileFile(target),
    setModified: (File, Long) -> Boolean = { file, time -> file.setLastModified(time) },
): T {
    val original = expected.bytes
    val modified = expected.modified
    val parent = requireNotNull(target.parentFile)
    parent.mkdirs()
    val temporary = File.createTempFile(".profile-new-", ".tmp", parent)
    var backup: File? = null
    var replaced = false
    var keepBackup = false
    try {
        temporary.writeBytes(bytes)
        if (original != null) {
            backup = File.createTempFile(".profile-old-", ".tmp", parent)
            backup.writeBytes(original)
            // 作者 long：在覆盖前确认备份保留旧 mtime，恢复只需 rename；不能等旧文件被替换后才发现时间戳无法还原。
            if (!setModified(backup, modified)) throw IOException("无法保留配置修改时间，原配置未修改")
        }
        return persist {
            // 作者 long：只允许覆盖准备时的文件；另一保存若先完成，旧会话必须重开而不能默默覆盖新正文。
            val unchanged = if (original == null) !target.exists() else target.isFile &&
                target.lastModified() == modified && target.inputStream().use { readProfileFileBytes(it, allowEmpty = true) }.contentEquals(original)
            require(unchanged) { "配置文件已变化，请重新打开编辑器" }
            // 作者 long：新旧文件位于同一目录，rename 失败直接终止，禁止退化为可能截断旧文件的流式覆盖。
            StateWriteAudit.fileMutation(target, "profile.atomic.replace") {
                if (!rename(temporary, target)) throw IOException("无法替换配置文件，原配置已保留")
            }
            replaced = true
        }
    } catch (error: Throwable) {
        if (replaced) {
            val restored = StateWriteAudit.fileMutation(target, "profile.atomic.rollback") {
                if (backup != null) rename(backup, target) else target.delete()
            }
            if (!restored) {
                // 作者 long：回滚失败时保留独占旧文件用于恢复，不能在 finally 中删除最后一份原正文。
                keepBackup = true
                throw IOException("配置保存失败，旧文件备份已保留")
            }
        }
        throw error
    } finally {
        temporary.delete()
        if (!keepBackup) backup?.delete()
    }
}
