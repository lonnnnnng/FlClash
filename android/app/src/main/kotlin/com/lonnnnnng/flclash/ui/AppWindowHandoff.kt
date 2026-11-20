package com.lonnnnnng.flclash.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class AppWindowReference { var view: View? = null }

/** 作者 long：等旧View实际移窗再建立下一窗口，避免拆窗和建窗挤在同帧并争抢输入；取消等待不重放旧操作。 */
internal suspend fun awaitAppWindowRemoved(view: View?, remove: () -> Unit) {
    if (view == null || !view.isAttachedToWindow) { remove(); return }
    suspendCancellableCoroutine<Unit> { continuation ->
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        view.addOnAttachStateChangeListener(listener)
        continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { view.removeOnAttachStateChangeListener(listener) } }
        remove()
    }
}
