package com.lonnnnnng.flclash.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** 作者 long：输入框测真实键盘/退出，光学测高频灰底的清晰、雾化和分色，不能只用纯色截图证明折射。 */
class PanelOpticsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        compose.waitForIdle(); android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        recordVisualPng("panel_optics_$name",ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray())
    }
    @Test fun floatingRenameKeepsDraftBusyErrorAndFadesOutWithoutExpanding() {
        assertPortraitDefaultFontConfiguration()
        var visible by mutableStateOf(true); var busy by mutableStateOf(false)
        var error by mutableStateOf<String?>(null);var value by mutableStateOf(TextFieldValue("旧名称"))
        var saves=0;var dark by mutableStateOf(false)
        compose.setContent { KClashTheme(darkTheme=dark) {
            if(visible) FloatingInputDialog("重命名配置","rename_fixture",busy,value.text.isNotBlank(),
                {visible=false},{saves++;busy=true},"保存",error=error) { enabled,submit ->
                FloatingInputField(value,{value=it},"rename_fixture_input",enabled,submit,placeholder="名称",invalid=error!=null)
            }
        } }
        capture("rename_light")
        compose.runOnIdle { dark=true };capture("rename_dark")
        compose.onNodeWithTag("rename_fixture_input").performTextReplacement("未提交的名称")
        compose.onNodeWithTag("rename_fixture_input").performImeAction()
        compose.runOnIdle { assertEquals(1,saves) }
        compose.onNodeWithTag("rename_fixture_busy").assertIsDisplayed()
        compose.onNodeWithTag("rename_fixture_confirm").assertIsNotEnabled()
        compose.runOnIdle { busy=false;error="保存失败，请重试" }
        compose.onNodeWithTag("rename_fixture_input").assertTextContains("未提交的名称")
        capture("rename_error")
        val before=compose.onNodeWithTag("rename_fixture").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("rename_fixture_cancel").performClick()
        compose.mainClock.advanceTimeBy(160)
        val midway=compose.onNodeWithTag("rename_fixture").fetchSemanticsNode().boundsInRoot
        assertEquals(before.width,midway.width,1f)
        capture("rename_closing")
        compose.mainClock.advanceTimeBy(700);compose.mainClock.autoAdvance=true
        compose.onNodeWithTag("rename_fixture").assertDoesNotExist()
    }

    @Test fun ageInputRevealLongValueFailureRetryAndCancelKeepSecretLocal() {
        assertPortraitDefaultFontConfiguration()
        var open by mutableStateOf(true);var busy by mutableStateOf(false);var error by mutableStateOf<String?>(null)
        var submitted:String?=null
        compose.setContent { KClashTheme { if(open) AgeSecretKeyDialog({submitted=it;busy=true},{open=false},busy,error) } }
        compose.onNodeWithTag("profile_age_dialog_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("profile_age_required_key").performTextInput("  fixture-".repeat(12))
        compose.onNodeWithTag("profile_age_required_key_visibility").performClick()
        capture("age_revealed_ime")
        compose.onNodeWithTag("profile_age_required_key_visibility").performClick()
        compose.onNodeWithTag("profile_age_required_key").performClick().assertIsFocused()
        compose.onNodeWithTag("profile_age_required_key").assert(SemanticsMatcher("显隐不改变密钥输入") {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.InputText].text.contains("fixture-")
        })
        compose.onNodeWithTag("profile_age_dialog_confirm").assertIsEnabled()
        compose.onNodeWithTag("profile_age_required_key").performImeAction()
        compose.runOnIdle { assertNotNull(submitted);busy=false;error="密钥不匹配，请重试" }
        compose.onNodeWithTag("profile_age_required_error").assertIsDisplayed()
        compose.onNodeWithTag("profile_age_required_key").performTextInput("fixture-corrected")
        capture("age_retry")
        compose.onNodeWithTag("profile_age_dialog_cancel").performClick()
        compose.onNodeWithTag("profile_age_required_key").assertDoesNotExist()
    }

    @Test fun closeFailureRemainsVisibleAndSecondAcknowledgementCloses() {
        assertPortraitDefaultFontConfiguration()
        var busy by mutableStateOf(false);var failure by mutableStateOf<String?>(null)
        var closing by mutableStateOf(false);var open by mutableStateOf(true);var requests=0
        compose.setContent { KClashTheme { if(open) FloatingInputDialog("重命名配置","close_failure",busy,true,
            { requests++;busy=true },{},"保存",error=failure,externallyClosing=closing,
            dismissAfterAcknowledgement=true,onExitComplete={open=false}) { enabled,submit ->
            FloatingInputField(TextFieldValue("保留草稿"),{},"close_failure_input",enabled,submit)
        } } }
        compose.onNodeWithTag("close_failure_cancel").performClick()
        compose.runOnIdle { assertEquals(1,requests);busy=false;failure="草稿清除失败，请重试关闭" }
        compose.onNodeWithTag("close_failure_error").assertIsDisplayed()
        compose.onNodeWithTag("close_failure_cancel").assertIsEnabled()
        capture("close_failure_visible")
        compose.onNodeWithTag("close_failure_cancel").performClick()
        compose.runOnIdle { assertEquals(2,requests);busy=false;closing=true }
        compose.onNodeWithTag("close_failure").assertDoesNotExist()
    }

    @Test fun liveOpticsKeepSharpCenterMixHazeAndSeparateLensChannels() {
        assertPortraitDefaultFontConfiguration()
        val neutral=AppGlassStyle(Color.Transparent,blur=0f,refraction=0f,saturation=1f,rim=0f)
        var material by mutableStateOf(neutral)
        compose.setContent { KClashTheme { CompositionLocalProvider(LocalAppMenuGlass provides true) {
            AppLiveMaterialHost(Modifier.fillMaxSize(),background={
                Canvas(Modifier.fillMaxSize()) { for(i in 0..200) drawRect(if(i%2==0) Color(.2f,.2f,.2f) else Color(.8f,.8f,.8f),
                    Offset(i*6.dp.toPx(),0f),Size(6.dp.toPx(),size.height)) }
            },foreground={ Box(Modifier.align(Alignment.Center).size(280.dp,88.dp)
                .appLiveMaterial(AppSuperellipseShape(44.dp),material).testTag("optical_probe")) })
        } } }
        val node=compose.onNodeWithTag("optical_probe")
        val raw=node.captureToImage().toPixelMap();capture("raw")
        compose.runOnIdle { material=neutral.copy(refraction=60f,bevel=20f) }
        val refracted=node.captureToImage().toPixelMap();capture("refracted")
        val cx=raw.width/2;val cy=raw.height/2
        assertTrue("中心不应被正折射移位",abs(raw[cx,cy].red-refracted[cx,cy].red)<.02f)
        var edgeChanges=0
        for(x in 40 until raw.width-40 step 4) if(abs(raw[x,8].red-refracted[x,8].red)>.08f) edgeChanges++
        // 作者 long：上边法线纵向而灰条纵向不变，改用圆角区域检验横向折射。
        for(y in 12 until raw.height-12 step 4) for(x in 8..70 step 4) if(abs(raw[x,y].red-refracted[x,y].red)>.08f) edgeChanges++
        assertTrue("真实边缘位移应改变条纹",edgeChanges>20)
        compose.runOnIdle { material=neutral.copy(haze=.72f,hazeBlur=15.6f) }
        val mixed=node.captureToImage().toPixelMap();capture("haze_mixed")
        val row=(raw.width/3 until raw.width*2/3).map { mixed[it,cy].red }
        assertTrue("雾化必须仍保留清晰输入的纹理",row.max()-row.min() in .08f.. .35f)
        compose.runOnIdle { material=neutral.copy(refraction=-4.3f,bevel=5.5f,dispersion=6.5f) }
        val lens=node.captureToImage().toPixelMap();capture("lens_dispersion")
        var chroma=0
        for(x in 80 until lens.width-80 step 3) if(abs(lens[x,4].red-lens[x,4].blue)>.1f) chroma++
        assertTrue("镜片在上下边沿分离红蓝通道",chroma>15)
        compose.runOnIdle { material=neutral.copy(refraction=-4.3f,bevel=5.5f) }
        val grey=node.captureToImage().toPixelMap()
        for(x in 80 until grey.width-80 step 8) assertTrue(abs(grey[x,4].red-grey[x,4].blue)<.015f)
    }
}
