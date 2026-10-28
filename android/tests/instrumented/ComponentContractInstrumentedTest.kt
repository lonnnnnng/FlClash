package com.lonnnnnng.flclash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonnnnnng.flclash.common.QuickAction
import com.lonnnnnng.flclash.common.action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 作者 long：外部自动化入口的安全边界由打包后的 Manifest 决定，测试直接读取 PackageManager，
 * 防止组件导出、Intent filter 或 Quick Settings 权限在后续改动中悄悄漂移。
 */
@RunWith(AndroidJUnit4::class)
class ComponentContractInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun externalActionsAndPlatformComponentsKeepTheirExportBoundaries() {
        val packageManager = context.packageManager
        val externalComponent = ComponentName(context, ExternalActionActivity::class.java)
        val quickActionComponent = ComponentName(context, QuickActionActivity::class.java)
        val tileComponent = ComponentName(context, KClashTileService::class.java)

        val externalInfo = packageManager.getActivityInfo(
            externalComponent,
            PackageManager.GET_META_DATA,
        )
        val quickActionInfo = packageManager.getActivityInfo(
            quickActionComponent,
            PackageManager.GET_META_DATA,
        )
        val tileInfo = packageManager.getServiceInfo(
            tileComponent,
            PackageManager.GET_META_DATA,
        )

        assertTrue(externalInfo.exported)
        assertFalse(quickActionInfo.exported)
        assertTrue(tileInfo.exported)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tileInfo.permission)

        QuickAction.entries.forEach { action ->
            val matches = packageManager.queryIntentActivities(
                Intent(action.action),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            assertTrue(
                "missing external action filter for ${action.name}",
                matches.any { it.activityInfo.name == ExternalActionActivity::class.java.name },
            )
        }

        val mainInfo = packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA,
        )
        assertNotNull(mainInfo.metaData)
        assertEquals(R.xml.shortcuts, mainInfo.metaData?.getInt("android.app.shortcuts"))
    }
}
