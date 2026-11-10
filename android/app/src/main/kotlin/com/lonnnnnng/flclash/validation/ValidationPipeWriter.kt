package com.lonnnnnng.flclash.validation

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** 作者 long：API24也须能取消堵塞的管道；等待可写后只写POSIX保证的最小原子块，不依赖API30的fcntlInt。 */
internal suspend fun writeValidationPipe(output:ParcelFileDescriptor,bytes:ByteArray) {
    val descriptor=StructPollfd().apply {fd=output.fileDescriptor;events=OsConstants.POLLOUT.toShort()}
    ParcelFileDescriptor.AutoCloseOutputStream(output).use {
        var offset=0
        while(offset<bytes.size) {
            currentCoroutineContext().ensureActive()
            try {
                if(Os.poll(arrayOf(descriptor),50)==0)continue
                currentCoroutineContext().ensureActive()
                if(descriptor.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)!=0)
                    throw IOException("配置校验管道已关闭")
                // 作者 long：此管道只有一个写者，POLLOUT后的512字节不等待读端腾出更多空间，取消延迟受50ms轮询约束。
                val count=Os.write(output.fileDescriptor,bytes,offset,minOf(512,bytes.size-offset))
                if(count>0)offset+=count else delay(5)
            }catch(failure:ErrnoException) {
                if(failure.errno==OsConstants.EAGAIN||failure.errno==OsConstants.EINTR)delay(5) else throw failure
            }
        }
    }
}
