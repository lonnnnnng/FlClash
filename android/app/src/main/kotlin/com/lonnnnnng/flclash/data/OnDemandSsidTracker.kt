package com.lonnnnnng.flclash.data

/** 作者 long：网络能力回调顺序不固定，刷新窗口只保留仍存在的同一Wi-Fi名称，蜂窝先回调不能短暂恢复代理。 */
internal class OnDemandSsidTracker<K> {
    private val wifi = linkedSetOf<K>()
    private val known = mutableMapOf<K, String?>()
    private val pending = mutableSetOf<K>()
    val current: String? get() = wifi.firstNotNullOfOrNull { known[it] }

    fun refresh(existingWifi: Set<K>) {
        wifi.clear(); wifi.addAll(existingWifi)
        known.keys.retainAll(existingWifi)
        pending.clear(); pending.addAll(existingWifi)
    }

    fun changed(key: K, isWifi: Boolean, ssid: String?) {
        pending.remove(key)
        if (isWifi) { wifi.add(key); known[key] = ssid }
        else { wifi.remove(key); known.remove(key) }
    }

    fun expireInitialRead() {
        pending.forEach { known.remove(it) }
        pending.clear()
    }
}
