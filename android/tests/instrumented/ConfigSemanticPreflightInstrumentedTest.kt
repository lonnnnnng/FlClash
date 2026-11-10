package com.lonnnnnng.flclash

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.lonnnnnng.flclash.data.ProfileType
import com.lonnnnnng.flclash.service.YamlConfigCodec
import com.lonnnnnng.flclash.ui.VisualMotionTestActivity
import com.lonnnnnng.flclash.validation.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** 作者 long：真机只复用授权原文件；错误候选仅在内存中编辑，不创建替代YAML或改变已有配置。 */
class ConfigSemanticPreflightInstrumentedTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private val entry get()=EntryPointAccessors.fromApplication(context.applicationContext,ConnectionAcceptanceEntryPoint::class.java)
    private suspend fun original():ByteArray {
        assertEquals("com.lonnnnnng.flclash.motion",context.packageName)
        val row=entry.database().profiles().getAll().single {it.type==ProfileType.FILE}
        val bytes=entry.profileRepository().profileFile(row.id).readBytes()
        val expected=InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256")
        assertEquals(expected,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        return bytes
    }
    private fun files():Map<String,String> {
        val root=context.filesDir
        return root.walkTopDown().filter {it.isFile}.associate {it.relativeTo(root).path to
            "${it.length()}:${it.lastModified()}:${MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString(""){b->"%02x".format(b)}}"}
    }
    @Test fun blockedPipeWriteCancelsWithoutWaitingForRemoteReader()=runBlocking(Dispatchers.IO) {
        val original=original()
        val pipe=android.os.ParcelFileDescriptor.createReliablePipe()
        val bytes=ByteArray(2*1024*1024){original[it%original.size]}
        try {
            val writer=launch {writeValidationPipe(pipe[1],bytes)}
            delay(100)
            assertTrue(writer.isActive)
            withTimeout(2_000){writer.cancelAndJoin()}
            assertTrue(writer.isCancelled)
        }finally {pipe.forEach {runCatching {it.close()}}}
    }

    @Test fun fileCustomTrustCertificateIsGrantedAndInvalidPemIsRejected()=runBlocking(Dispatchers.IO) {
        val bytes=original()
        val root=File(context.filesDir,"mihomo");assertTrue(root.isDirectory)
        val file=File(root,"semantic-own-${java.util.UUID.randomUUID()}.pem")
        assertFalse(file.exists())
        val baseline=files()
        try {
            val caRoots=listOf(File("/apex/com.android.conscrypt/cacerts"),File("/system/etc/security/cacerts"))
            val certificate=caRoots.flatMap {it.listFiles().orEmpty().toList()}.first {it.isFile&&it.canRead()}
            file.writeBytes(certificate.readBytes())
            val json=JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))).asJsonObject
            json.add("tls",com.google.gson.JsonObject().apply {add("custom-certifactes",com.google.gson.JsonArray().apply {add(file.name)})})
            val candidate=YamlConfigCodec.toYaml(json.toString()).toByteArray()
            assertTrue(IsolatedConfigValidator(context).checkFull(candidate).passed)
            file.writeText("not a PEM certificate")
            val failure=runCatching {IsolatedConfigValidator(context).checkFull(candidate)}.exceptionOrNull()
            assertTrue(failure is ConfigSemanticException)
            assertEquals("tls",(failure as ConfigSemanticException).section)
        }finally {assertTrue(file.delete())}
        assertEquals(baseline,files())
    }

    @Test fun canceledValidationReleasesProcessAndFollowingOriginalStillPasses()=runBlocking(Dispatchers.IO) {
        val bytes=original();val validator=IsolatedConfigValidator(context)
        val baseline=files()
        repeat(3) {
            val task=async {validator.checkFull(bytes)}
            delay(25)
            withTimeout(5_000){task.cancelAndJoin()}
        }
        assertTrue(withTimeout(35_000){validator.checkFull(bytes)}.passed)
        assertEquals(baseline,files())
    }

    @Test fun runningCoreKeepsEpochSelectionSourceAndRealNetworkAcrossValidation():Unit=runBlocking(Dispatchers.IO) {
        val bytes=original();val runtime=entry.runtimeManager();val bridge=entry.coreBridge()
        val oldVpn=runtime.vpnSettings.value.enabled
        val target=java.net.URI(checkNotNull(InstrumentationRegistry.getArguments().getString("kclashAcceptanceUrl1")))
        (context.applicationContext as KClashApplication).awaitRuntimeInitialization()
        assertEquals(com.lonnnnnng.flclash.service.CoreRuntimeStatus.STOPPED,runtime.state.value)
        androidx.test.core.app.ActivityScenario.launch(VisualMotionTestActivity::class.java).use {
            try {
                runtime.setVpnEnabled(false).getOrThrow();runtime.startProxy().getOrThrow()
                withTimeout(45_000){runtime.state.first {it==com.lonnnnnng.flclash.service.CoreRuntimeStatus.RUNNING}}
                val beforeRequest=acceptedProxyRequest(entry,target){_,_->}
                assertTrue(beforeRequest.toString(),beforeRequest.getBoolean("passed"))
                val stamp=bridge.ipRouteStamp();val since=runtime.runningSinceElapsedMillis.value
                val rows=entry.database().profiles().getAll();val selected=entry.profileRepository().currentProfileId.value
                val sourceFiles=rows.associate {row->val file=entry.profileRepository().profileFile(row.id);row.id to Triple(file.readBytes().toList(),file.length(),file.lastModified())}
                val geoFiles=listOf("GEOIP.metadb","GEOIP.dat","GEOSITE.dat","ASN.mmdb").associateWith {name->
                    val file=File(context.filesDir,"mihomo/$name");Triple(MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList(),file.length(),file.lastModified())
                }
                assertTrue(IsolatedConfigValidator(context).checkFull(bytes).passed)
                assertEquals(stamp.epoch,bridge.ipRouteStamp().epoch);assertEquals(since,runtime.runningSinceElapsedMillis.value)
                assertEquals(rows,entry.database().profiles().getAll());assertEquals(selected,entry.profileRepository().currentProfileId.value)
                for((id,value)in sourceFiles){val file=entry.profileRepository().profileFile(id);assertEquals(value,Triple(file.readBytes().toList(),file.length(),file.lastModified()))}
                for((name,value)in geoFiles){val file=File(context.filesDir,"mihomo/$name");assertEquals(value,Triple(MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList(),file.length(),file.lastModified()))}
                val afterRequest=acceptedProxyRequest(entry,target){_,_->};assertTrue(afterRequest.toString(),afterRequest.getBoolean("passed"))
                ins.sendStatus(2,android.os.Bundle().apply {putString("kclash_semantic_running",org.json.JSONObject()
                    .put("passed",true).put("epochHeld",true).put("runningSinceHeld",true).put("profileRowsHeld",true)
                    .put("sourceAndGeoFilesHeld",true).put("realHttpsRequests",2).put("sameSocketAttribution",true).toString())})
            }finally {withContext(NonCancellable){runtime.stop().getOrThrow();runtime.setVpnEnabled(oldVpn).getOrThrow()}}
        }
    }

    @Test fun authorizedOriginalUsesFullParserAndLeavesSourceResourcesAndDatabaseUntouched()=runBlocking(Dispatchers.IO) {
        val bytes=original();val before=files();val rows=entry.database().profiles().getAll()
        val result=IsolatedConfigValidator(context).checkFull(bytes)
        assertTrue(result.toString(),result.passed)
        assertEquals(before,files());assertEquals(rows,entry.database().profiles().getAll())
        assertEquals(com.lonnnnnng.flclash.service.CoreRuntimeStatus.STOPPED,entry.runtimeManager().state.value)
        ins.sendStatus(2,android.os.Bundle().apply {putString("kclash_semantic_original",org.json.JSONObject()
            .put("passed",true).put("bytes",bytes.size).put("deferredResources",result.deferredResources).put("filesHeld",true).toString())})
    }
    @Test fun semanticNodeGroupRuleDnsErrorsRejectBeforeAnyProfileOrFileWrite()=runBlocking(Dispatchers.IO) {
        val bytes=original();val before=files();val rows=entry.database().profiles().getAll()
        val mutations=listOf("proxies","proxy-groups","rules","dns","tls")
        for(section in mutations) {
            val json=JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))).asJsonObject
            when(section){
                "proxies"->json.getAsJsonArray("proxies").first().asJsonObject.addProperty("type","kclash-invalid-protocol")
                "proxy-groups"->json.getAsJsonArray("proxy-groups").first().asJsonObject.add("proxies",JsonParser.parseString("[\"kclash-invalid-reference\"]"))
                "rules"->json.add("rules",JsonParser.parseString("[\"MATCH,kclash-invalid-reference\"]"))
                "dns"->json.getAsJsonObject("dns").apply {addProperty("enable",true);add("nameserver",JsonParser.parseString("[\"invalid-protocol://invalid\"]"))}
                "tls"->json.add("tls",JsonParser.parseString("{\"certificate\":\"kclash-missing-certificate\",\"private-key\":\"kclash-missing-private-key\"}"))
            }
            val candidate=YamlConfigCodec.toYaml(json.toString()).toByteArray()
            val failure=runCatching {entry.profileRepository().importFileProfile("semantic-invalid-candidate",candidate,""){true}}.exceptionOrNull()
            assertTrue("$section: ${failure?.javaClass?.simpleName}",failure is ConfigSemanticException)
            assertEquals(before,files());assertEquals(rows,entry.database().profiles().getAll())
            val semantic=failure as ConfigSemanticException
            assertFalse(semantic.toString().contains("private-key"))
            ins.sendStatus(2,android.os.Bundle().apply {putString("kclash_semantic_rejection",org.json.JSONObject()
                .put("case",section).put("reportedSection",semantic.section).put("code",semantic.code).put("noWrite",true).toString())})
        }
    }

    @Test fun customImportRejectsInvalidCandidateBeforeAnyResourceStaging()=runBlocking(Dispatchers.IO) {
        val bytes=original();val repository=entry.profileRepository()
        val initialFiles=files();val originalRows=entry.database().profiles().getAll()
        val custom=repository.addCustomProfile("semantic-own-custom")
        try {
            val baseline=files();val rows=entry.database().profiles().getAll()
            val json=JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))).asJsonObject
            json.add("rules",JsonParser.parseString("[\"MATCH,kclash-invalid-reference\"]"))
            val candidate=YamlConfigCodec.toYaml(json.toString())
            val error=runCatching {repository.replaceCustomProfile(custom.id,candidate,custom,""){true}}.exceptionOrNull()
            assertTrue(error?.javaClass?.simpleName,error is ConfigSemanticException)
            assertEquals("rules",(error as ConfigSemanticException).section)
            assertEquals(baseline,files());assertEquals(rows,entry.database().profiles().getAll())
        }finally {repository.deleteProfile(custom.id)}
        assertEquals(initialFiles,files());assertEquals(originalRows,entry.database().profiles().getAll())
    }

    @Test fun customImportResolvesExistingAuthorizedProfileWithoutCreatingRuntimeProjection()=runBlocking(Dispatchers.IO) {
        val bytes=original();val repository=entry.profileRepository();val database=entry.database()
        val rows=database.profiles().getAll();val beforeFiles=files()
        val preference=database.appPreferences().getByPrefix(com.lonnnnnng.flclash.data.LocalProxies.KEY)
            .singleOrNull {it.key==com.lonnnnnng.flclash.data.LocalProxies.KEY}
        val fileId=rows.single {it.type==ProfileType.FILE}.id
        val custom=repository.addCustomProfile("semantic-own-reference")
        try {
            // 作者 long：仅从授权原正文抽取一个真实组，绑定原FILE的节点；不生成替代订阅或下载新的资源。
            val root=JsonParser.parseString(YamlConfigCodec.toJson(bytes.toString(Charsets.UTF_8))).asJsonObject
            val group=root.getAsJsonArray("proxy-groups").first().asJsonObject.deepCopy()
            val name=group.get("name").asString
            group.addProperty("type","select")
            group.add("proxies",JsonParser.parseString("[\"DIRECT\"]"))
            group.add("use",JsonParser.parseString("[\"authorized\"]"))
            root.add("proxy-groups",com.google.gson.JsonArray().apply {add(group)})
            root.remove("proxies");root.remove("rule-providers")
            root.add("rules",com.google.gson.JsonArray().apply {add("MATCH,$name")})
            root.add("proxy-providers",com.google.gson.JsonObject().apply {
                add("authorized",com.lonnnnnng.flclash.data.ProfileProxyResources.reference(fileId))
            })
            val updated=repository.replaceCustomProfile(custom.id,YamlConfigCodec.toYaml(root.toString()),custom,""){true}
            assertEquals(custom.id,updated.id)
            val stored=repository.readLocalProfileContent(custom.id)
            assertTrue(stored.contains("kclash-profile:$fileId"))
            assertEquals(rows.size+1,database.profiles().getAll().size)
            assertEquals(beforeFiles,files().filterKeys {it!=repository.profileFile(custom.id).relativeTo(context.filesDir).path})
        }finally {
            repository.deleteProfile(custom.id)
            if(preference==null)database.appPreferences().delete(com.lonnnnnng.flclash.data.LocalProxies.KEY)
            else database.appPreferences().put(preference)
        }
        assertEquals(beforeFiles,files());assertEquals(rows,database.profiles().getAll())
    }
}
