package com.lonnnnnng.flclash.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.AppTheme
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import com.lonnnnnng.flclash.service.ExtensionRule
import com.lonnnnnng.flclash.service.ExtensionRules
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：长规则只写独立空配置库；验证搜索锚点和拖动后的真实Room顺序，不加载YAML或投影到正式VPN。 */
class MainGlobalRulesLongListInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun longListSearchRestoresAnchorAndDragPersistsAcrossRecreation() = runBlocking {
        assertPortraitDefaultFontConfiguration()
        val ins = InstrumentationRegistry.getInstrumentation()
        assertEquals("com.lonnnnnng.flclash.motion", ins.targetContext.packageName)
        assertEquals("true", InstrumentationRegistry.getArguments().getString("kclashGlobalRulesNaturalAcceptance"))
        val entry = EntryPointAccessors.fromApplication(compose.activity.applicationContext, ConnectionAcceptanceEntryPoint::class.java)
        val repo = entry.profileRepository(); repo.profilesLoaded.first { it }
        assertTrue(entry.database().profiles().getAll().isEmpty()); assertNull(repo.currentProfileId.value)
        assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        val appearance = compose.activity.appearanceSettings
        val originalAppearance = appearance.read()
        val prefs = ins.targetContext.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
        val keys = prefs.all.toMap()
        var outerFailure: Throwable? = null
        try {
        appearance.write(originalAppearance.copy(theme = AppTheme.LIGHT, glass = false))
        compose.onNodeWithTag("nav_tools").performClick()
        compose.onNodeWithTag("page_tools").performScrollToNode(hasTestTag("tools_item_advanced"))
        compose.onNodeWithTag("tools_item_advanced").performClick()
        compose.waitForIdle(); android.os.SystemClock.sleep(5500)
        val before = entry.database().appPreferences().getByPrefix("").sortedBy { it.key }
        val original = before.firstOrNull { it.key == ExtensionRules.PREFERENCE_KEY }
        assertTrue(repo.globalExtensionRules().isEmpty())
        val rows = (0..79).map { i -> ExtensionRule("vis094-$i", "DOMAIN,rule-${i.toString().padStart(2, '0')}.invalid,${if (i == 50) "Long_target_name_that_must_not_cover_the_selection_control" else "DIRECT"}") }
        lateinit var controller: GlobalRulesController
        compose.runOnIdle { controller = ViewModelProvider(compose.activity)[ProfilesViewModel::class.java].globalRules }
        var primary: Throwable? = null
        try {
            repo.saveGlobalExtensionRules(rows)
            compose.onNodeWithTag("advanced_item_rules").performClick()
            compose.waitUntil(15_000) { controller.state.value.loaded && !controller.state.value.loading }
            val list = compose.onNodeWithTag("global_rules_list")
            list.performScrollToNode(hasTestTag("global_rule_vis094-50"))
            compose.onNodeWithTag("global_rule_vis094-0").assertDoesNotExist()
            val top = compose.onNodeWithTag("global_rule_vis094-50").fetchSemanticsNode().boundsInRoot.top
            capture("long_list")
            compose.onNodeWithTag("global_rules_search").performTextReplacement("rule-07")
            compose.onNodeWithTag("global_rules_search").performImeAction()
            compose.onNodeWithTag("global_rule_vis094-7").assertIsDisplayed()
            compose.onNodeWithTag("global_rule_vis094-50").assertDoesNotExist()
            compose.onNodeWithTag("global_rules_sort").assertIsNotEnabled()
            capture("filtered")
            compose.onNodeWithTag("global_rules_search").performTextReplacement("no-such-rule")
            compose.onNodeWithText("没有匹配的规则").assertIsDisplayed()
            compose.onNodeWithTag("global_rules_search_cancel").performClick()
            compose.waitForIdle(); android.os.SystemClock.sleep(600)
            compose.onNodeWithTag("global_rule_vis094-50").assertIsDisplayed()
            assertEquals(top, compose.onNodeWithTag("global_rule_vis094-50").fetchSemanticsNode().boundsInRoot.top, 1f)
            capture("anchor_restored")
            compose.onNodeWithTag("global_rules_search").performTextReplacement("rule-")
            compose.onNodeWithTag("global_rules_search").performImeAction()
            list.performScrollToNode(hasTestTag("global_rule_vis094-55"))
            val searchTop = compose.onNodeWithTag("global_rule_vis094-55").fetchSemanticsNode().boundsInRoot.top
            compose.activityRule.scenario.recreate(); compose.waitForIdle()
            compose.onNodeWithTag("global_rule_vis094-55").assertIsDisplayed()
            assertEquals(searchTop, compose.onNodeWithTag("global_rule_vis094-55").fetchSemanticsNode().boundsInRoot.top, 1f)
            capture("search_recreated")
            compose.onNodeWithTag("global_rules_search_cancel").performClick(); compose.waitForIdle()
            compose.onNodeWithTag("global_rules_sort").performClick()
            val order = compose.onNodeWithTag("global_rules_order_list")
            val source = compose.onNodeWithTag("global_rules_order_vis094-0").fetchSemanticsNode().boundsInRoot
            val target = compose.onNodeWithTag("global_rules_order_vis094-1").fetchSemanticsNode().boundsInRoot
            val bounds = order.fetchSemanticsNode().boundsInRoot
            order.performTouchInput {
                down(Offset(source.center.x - bounds.left, source.center.y - bounds.top)); advanceEventTime(700)
                moveTo(Offset(target.center.x - bounds.left, target.center.y - bounds.top), 300); advanceEventTime(200); up()
            }
            compose.waitUntil(15_000) { !controller.state.value.saving && controller.state.value.rules.firstOrNull()?.id == "vis094-1" }
            val expected = listOf(rows[1], rows[0]) + rows.drop(2)
            assertEquals(expected, repo.globalExtensionRules()); capture("sorted")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("global_rules_sort_done").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(expected, repo.globalExtensionRules()); capture("sorted_recreated")
            compose.onNodeWithTag("global_rules_sort_done").performClick()
            list.performScrollToNode(hasTestTag("global_rule_vis094-79"))
            compose.onNodeWithTag("global_rule_vis094-79").assertIsDisplayed(); capture("last_row")
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { try {
            compose.waitUntil(15_000) { !controller.state.value.saving && !controller.state.value.applying }
            compose.runOnIdle { controller.close {} }
            compose.waitUntil(15_000) { controller.state.value.draft == null }
            entry.database().withTransaction {
                if (original == null) entry.database().appPreferences().delete(ExtensionRules.PREFERENCE_KEY)
                else entry.database().appPreferences().put(original)
            }
            assertEquals(before, entry.database().appPreferences().getByPrefix("").sortedBy { it.key })
            assertNull(entry.globalRulesStore().read()); assertTrue(entry.database().profiles().getAll().isEmpty())
            assertNull(repo.currentProfileId.value); assertEquals(CoreRuntimeStatus.STOPPED, entry.runtimeManager().state.value)
        } catch (failure: Throwable) { if (primary != null) primary.addSuppressed(failure) else throw failure } }
        } catch (caught: Throwable) { outerFailure = caught; throw caught }
        finally { try {
            appearance.write(originalAppearance)
            val edit = prefs.edit(); for (key in listOf("theme", "language", "glass")) if (key !in keys) edit.remove(key)
            check(edit.commit()); assertEquals(keys, prefs.all)
        } catch (cleanup: Throwable) { if (outerFailure != null) outerFailure.addSuppressed(cleanup) else throw cleanup } }
    }
    private fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(450)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val stream = java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream); bitmap.recycle()
        recordVisualPng("vis094_long_$name", stream.toByteArray())
    }
}
