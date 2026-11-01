package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.lonnnnnng.flclash.core.CoreDnsQuery

// 作者 long：DNS 历史会继续实时变化，详情保存所点中的完整快照，避免 Activity 重建后按索引选中另一条记录。
internal val DnsQueryDetailSaver = Saver<CoreDnsQuery?, Bundle>(
    save = { query ->
        query?.let {
            Bundle().apply {
                putString("domain", it.domain)
                putString("type", it.type)
                putString("initiator", it.initiator)
                putString("upstream", it.upstream)
                putBoolean("cached", it.cached)
                putStringArrayList("answers", ArrayList(it.answers))
                putString("rcode", it.rcode)
                putString("error", it.error)
                putLong("delay", it.delay)
                putString("time", it.time)
            }
        }
    },
    restore = { saved ->
        CoreDnsQuery(
            domain = saved.getString("domain").orEmpty(),
            type = saved.getString("type").orEmpty(),
            initiator = saved.getString("initiator").orEmpty(),
            upstream = saved.getString("upstream").orEmpty(),
            cached = saved.getBoolean("cached"),
            answers = saved.getStringArrayList("answers").orEmpty(),
            rcode = saved.getString("rcode").orEmpty(),
            error = saved.getString("error").orEmpty(),
            delay = saved.getLong("delay"),
            time = saved.getString("time").orEmpty(),
        )
    },
)
