package com.lonnnnnng.flclash.service

import android.content.Context
import com.lonnnnnng.flclash.core.CoreOutboundMode

interface OutboundModeStore {
    fun read(): CoreOutboundMode?

    fun write(mode: CoreOutboundMode)
}

object NoopOutboundModeStore : OutboundModeStore {
    override fun read(): CoreOutboundMode? = null

    override fun write(mode: CoreOutboundMode) = Unit
}

class SharedPreferencesOutboundModeStore(context: Context) : OutboundModeStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): CoreOutboundMode? {
        val value = preferences.getString(MODE_KEY, null) ?: return null
        return CoreOutboundMode.entries.firstOrNull { it.wireValue == value }
    }

    override fun write(mode: CoreOutboundMode) {
        preferences.edit().putString(MODE_KEY, mode.wireValue).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "kclash_runtime_preferences"
        const val MODE_KEY = "outbound_mode"
    }
}
