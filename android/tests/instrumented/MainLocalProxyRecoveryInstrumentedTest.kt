package com.lonnnnnng.flclash.ui

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 作者 long：仅恢复外部失败记录指定的一份测试副本；精确核对行摘要、原文及新增节点定义，不按前缀批量删除。 */
class MainLocalProxyRecoveryInstrumentedTest {
    @Test fun recoverExactInterruptedFormalLocalProxyFixture() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assertEquals("true", args.getString("kclashMainLocalRecovery"))
        assertEquals("true", args.getString("kclashRecoveryLocalKeyWasAbsent"))
        val temporary = requireNotNull(args.getString("kclashRecoveryProfileId")).toLong()
        val selected = requireNotNull(args.getString("kclashRecoverySelectedId")).toLong()
        val expectedRows = requireNotNull(args.getString("kclashRecoveryRowsSha256"))
        val expectedBody = requireNotNull(args.getString("kclashLocalYamlSha256"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entry = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val repository = entry.profileRepository(); repository.profilesLoaded.first { it }
        val runtime = entry.runtimeManager()
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        assertNull(entry.localProxyDraftStore().read())
        val rows = entry.database().profiles().getAll().sortedBy { it.id }
        val own = rows.single { it.id == temporary }
        check(own.type == ProfileType.FILE && Regex("正式代理验收-[a-f0-9]{32}-原节点").matches(own.label))
        check(temporary != selected && rows.any { it.id == selected })
        val formal = rows.filterNot { it.id == temporary }
        assertEquals(expectedRows, sha(Gson().toJson(formal).toByteArray()))
        assertEquals(expectedBody, sha(repository.profileFile(temporary).readBytes()))
        val nodes = repository.localProxies()
        assertEquals(1, nodes.size)
        assertEquals(temporary, repository.currentProfileId.value)
        val definition = runtime.getProxyDefinition(temporary, nodes.single().name)
        val plan = planLocalProxyAddition(definition, emptyList(), emptySet())
        assertTrue("只回收该原节点导入产生的记录", plan.row.fields == nodes.single().fields)
        val preferences = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        runtime.selectProfile(selected, repository::selectProfile)
        repository.deleteProfile(temporary)
        repository.saveLocalProxies(nodes, emptyList())
        // 作者 long：本轮安装前证据明确目录键不存在；只移除此键，其他偏好不得恢复为旧快照。
        entry.database().appPreferences().delete(LocalProxies.KEY)
        assertEquals(expectedRows, sha(Gson().toJson(entry.database().profiles().getAll().sortedBy { it.id }).toByteArray()))
        val after = entry.database().appPreferences().getByPrefix("").associate { it.key to it.value }
        assertTrue("非本轮修改偏好保持", preferences - setOf(LocalProxies.KEY, "current_profile_id") == after - "current_profile_id")
        assertEquals(selected, repository.currentProfileId.value)
        assertTrue(repository.localProxies().isEmpty())
        assertEquals(CoreRuntimeStatus.STOPPED, runtime.state.value)
        instrumentation.sendStatus(2, Bundle().apply { putString("kclash_main_local_recovered", "exact-fixture-removed=true;formal-row-digest-matched=true;owned-node-removed=true") })
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
