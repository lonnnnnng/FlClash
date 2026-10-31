package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.lonnnnnng.flclash.core.CoreConnection
import com.lonnnnnng.flclash.core.CoreConnectionMetadata

// 作者 long：只保存一条已打开详情的快照，不保存整个连接列表；字段显式写入 Bundle，避免依赖反射和 Release 混淆后的字段名。
internal val ConnectionDetailSaver = Saver<CoreConnection?, Bundle>(
    save = { connection ->
        connection?.let {
            Bundle().apply {
                putString("id", it.id)
                putLong("upload", it.upload)
                putLong("download", it.download)
                putString("start", it.start)
                putStringArrayList("chains", ArrayList(it.chains))
                putString("rule", it.rule)
                putString("rulePayload", it.rulePayload)
                with(it.metadata) {
                    putString("network", network)
                    putString("sourceIp", sourceIp)
                    putString("sourcePort", sourcePort)
                    putString("destinationIp", destinationIp)
                    putString("destinationPort", destinationPort)
                    putString("host", host)
                    putString("process", process)
                    putString("processPath", processPath)
                    putString("remoteDestination", remoteDestination)
                    putLong("uid", uid)
                    putString("specialProxy", specialProxy)
                    putString("specialRules", specialRules)
                    putStringArrayList("sourceGeoIp", ArrayList(sourceGeoIp))
                    putStringArrayList("destinationGeoIp", ArrayList(destinationGeoIp))
                    putString("sourceIpAsn", sourceIpAsn)
                    putString("destinationIpAsn", destinationIpAsn)
                    putString("dnsMode", dnsMode)
                }
            }
        }
    },
    restore = { saved ->
        CoreConnection(
            id = saved.getString("id").orEmpty(),
            upload = saved.getLong("upload"),
            download = saved.getLong("download"),
            start = saved.getString("start").orEmpty(),
            chains = saved.getStringArrayList("chains").orEmpty(),
            rule = saved.getString("rule").orEmpty(),
            rulePayload = saved.getString("rulePayload").orEmpty(),
            metadata = CoreConnectionMetadata(
                network = saved.getString("network").orEmpty(),
                sourceIp = saved.getString("sourceIp").orEmpty(),
                sourcePort = saved.getString("sourcePort").orEmpty(),
                destinationIp = saved.getString("destinationIp").orEmpty(),
                destinationPort = saved.getString("destinationPort").orEmpty(),
                host = saved.getString("host").orEmpty(),
                process = saved.getString("process").orEmpty(),
                processPath = saved.getString("processPath").orEmpty(),
                remoteDestination = saved.getString("remoteDestination").orEmpty(),
                uid = saved.getLong("uid"),
                specialProxy = saved.getString("specialProxy").orEmpty(),
                specialRules = saved.getString("specialRules").orEmpty(),
                sourceGeoIp = saved.getStringArrayList("sourceGeoIp").orEmpty(),
                destinationGeoIp = saved.getStringArrayList("destinationGeoIp").orEmpty(),
                sourceIpAsn = saved.getString("sourceIpAsn").orEmpty(),
                destinationIpAsn = saved.getString("destinationIpAsn").orEmpty(),
                dnsMode = saved.getString("dnsMode").orEmpty(),
            ),
        )
    },
)
