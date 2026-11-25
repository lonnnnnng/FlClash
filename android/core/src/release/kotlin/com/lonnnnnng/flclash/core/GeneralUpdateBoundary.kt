package com.lonnnnnng.flclash.core

import org.json.JSONObject

/** 作者 long：正式包没有故障布置入口或状态，常规更新始终原样交给核心。 */
internal object GeneralUpdateBoundary {
    fun arguments(value: JSONObject): JSONObject = value
}
