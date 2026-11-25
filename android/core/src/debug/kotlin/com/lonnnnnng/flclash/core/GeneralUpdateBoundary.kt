package com.lonnnnnng.flclash.core

import org.json.JSONObject

/** 作者 long：只在显式真机验收时破坏一次请求类型，让真实 Go 解码拒绝更新，随后回滚仍走原生调用。 */
object GeneralUpdateBoundary {
    private var pending = false
    var visits = 0
        private set
    private var observing = false

    @Synchronized fun arm(packageName: String, enabled: Boolean) {
        require(packageName == "com.lonnnnnng.flclash" && enabled)
        check(!observing)
        pending = true
        observing = true
        visits = 0
    }

    @Synchronized fun arguments(value: JSONObject): JSONObject {
        if (!observing) return value
        visits++
        if (!pending) return value
        pending = false
        return JSONObject(value.toString()).put("options", JSONObject().put("delay-test-timeout", "acceptance-invalid-number"))
    }

    @Synchronized fun reset() { pending = false; observing = false; visits = 0 }
}
