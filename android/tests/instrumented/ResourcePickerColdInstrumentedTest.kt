package com.lonnnnnng.flclash.ui

import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import com.lonnnnnng.flclash.ConnectionAcceptanceEntryPoint
import com.lonnnnnng.flclash.MainActivity
import com.lonnnnnng.flclash.data.*
import com.lonnnnnng.flclash.service.CoreRuntimeStatus
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 作者 long：真实系统选择器外部终止宿主后交付；输入由独占manifest绑定，正式目录和正文逐场保护。 */
class ResourcePickerColdInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val gson = Gson()
    private fun entry() = EntryPointAccessors.fromApplication(compose.activity.application,ConnectionAcceptanceEntryPoint::class.java)
    private fun marker() = File(compose.activity.cacheDir,"resource-picker-cold.json")
    private fun backup() = EncryptedDraftFile(compose.activity,"resource-picker-fixture.v1","com.lonnnnnng.flclash.resource.picker.fixture.v1")
    private fun case(): String {
        assertEquals("true",InstrumentationRegistry.getArguments().getString("kclashResourceColdAcceptance"))
        return requireNotNull(InstrumentationRegistry.getArguments().getString("kclashResourceColdCase"))
    }
    private fun <T:androidx.lifecycle.ViewModel> vm(type:Class<T>):T {
        lateinit var result:T;compose.runOnIdle { result=ViewModelProvider(compose.activity)[type] };return result
    }
    private fun ruleVm()=vm(RuleResourcesViewModel::class.java)
    private fun iconsVm()=vm(ResourcesViewModel::class.java)
    private fun profileVm()=vm(ProfilesViewModel::class.java)
    private fun exists(tag:String)=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun awaitTag(tag:String)=compose.waitUntil(25_000){exists(tag)}
    private suspend fun originalRows():List<AppPreferenceEntity> = gson.fromJson(checkNotNull(backup().read()).decodeToString(),Array<AppPreferenceEntity>::class.java).toList()
    private suspend fun originalRules()=RuleResources.decode(originalRows().firstOrNull { it.key==RULE_RESOURCES_KEY }?.value)
    private suspend fun originalIcons()=IconSetCodec.decode(originalRows().firstOrNull { it.key==ICON_SETS_KEY }?.value)
    private suspend fun preferences()=sha(gson.toJson(entry().database().appPreferences().getByPrefix("").associate { it.key to it.value }.toSortedMap()))
    private suspend fun scripts()=sha(gson.toJson(entry().database().scripts().getAll().sortedBy { it.id }))
    private fun signatures(ids:List<Long>)=ids.joinToString("|"){"$it:${signature(entry().profileRepository().profileFile(it))}"}
    private fun signature(file:File)=if(file.exists())"${file.length()}:${file.lastModified()}:${sha(file.readBytes())}" else "missing"
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun sha(text:String)=sha(text.toByteArray())
    private suspend fun noImports(){assertEquals(originalRules(),entry().rules().all());assertEquals(originalIcons(),entry().icons().all())}

    @Test fun prepareAndHoldPickerOrPreview():Unit=runBlocking {
        val case=case();val input=ResourceAcceptanceInputs.manifest();val e=entry();val repo=e.profileRepository()
        repo.profilesLoaded.first{it};assertEquals(CoreRuntimeStatus.STOPPED,e.runtimeManager().state.value)
        assertFalse(marker().exists());assertNull(backup().read())
        for(store in listOf(e.ruleFilePickerStore(),e.ruleResourceDraftStore(),e.iconFilePickerStore(),e.groupImagePickerStore(),e.profileDraftStore()))assertNull(store.read())
        val rows=e.database().profiles().getAll().sortedBy{it.id};assertTrue(rows.isNotEmpty())
        val r=JsonObject().apply{
            addProperty("case",case);addProperty("token",UUID.randomUUID().toString());addProperty("inputToken",input.getString("token"))
            addProperty("preparedPid",android.os.Process.myPid());addProperty("rows",sha(gson.toJson(rows)));addProperty("files",signatures(rows.map{it.id}))
            addProperty("preferences",preferences());addProperty("scripts",scripts());addProperty("selected",repo.currentProfileId.value)
            addProperty("ruleFiles",sha(gson.toJson(e.rules().all().associate{it.id to signature(e.rules().file(it))})))
        }
        backup().write(gson.toJson(e.database().appPreferences().getByPrefix("").filter{it.key in setOf(RULE_RESOURCES_KEY,ICON_SETS_KEY,"group_icon_history")}).toByteArray())
        marker().writeText(r.toString())
        val waitSystem=case.endsWith("return")||case.endsWith("cancel")
        val image=case.startsWith("image")
        if(image){
            val expected=requireNotNull(InstrumentationRegistry.getArguments().getString("kclashLocalYamlSha256"))
            val original=rows.single{it.type==ProfileType.FILE && sha(repo.profileFile(it.id).readBytes())==expected}
            val id=System.nanoTime().and(Long.MAX_VALUE);val profile=Profile.custom(id,"resource-cold-${r["token"].asString}").copy(orderIndex=rows.size)
            e.database().profiles().insert(profile.toEntity());repo.profileFile(id).apply{parentFile!!.mkdirs();writeBytes(repo.profileFile(original.id).readBytes())}
            r.addProperty("profileId",id);r.addProperty("profileSignature",signature(repo.profileFile(id)));marker().writeText(r.toString())
            compose.onNodeWithTag("nav_profiles").performClick();val m=profileVm()
            compose.runOnIdle{m.openEditor(profile)};compose.waitUntil(20_000){m.editorState.value.overview!=null}
            compose.runOnIdle{m.openProxyGroupsEditor(profile)};compose.waitUntil(20_000){m.proxyGroupsEditorState.value.profile?.id==id && !m.proxyGroupsEditorState.value.isLoading}
            compose.onNodeWithTag("proxy_group_row_0").performClick();compose.onNodeWithTag("proxy_group_icon").performScrollTo().performClick()
            if(case=="image-reading")EditorCommitProbe.arm(EditorCommitWindow.FILE_PICKER_READING)
            if(!waitSystem)compose.onNodeWithTag("group_icon_local").performClick()
        }else{
            compose.onNodeWithTag("nav_tools").performClick();compose.onNodeWithTag("tools_item_resources_settings").performScrollTo().performClick()
            compose.onNodeWithText(if(case.startsWith("icons"))"图标集" else "规则资源").performClick()
            if(case=="icons-after")EditorCommitProbe.arm(EditorCommitWindow.ICON_FILE_APPLIED)
            val addTag=if(case.startsWith("icons"))"icon_sets_add" else "rule_resources_add"
            compose.waitUntil(20_000){compose.onAllNodes(hasTestTag(addTag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.waitForIdle();SystemClock.sleep(650)
            if(!waitSystem){compose.onNodeWithTag(addTag).performClick();compose.onNodeWithText("从文件导入").performClick()}
        }
        if(!waitSystem)picker()
        val fileKey=when{image->"image";case.startsWith("icons")->"icons";case.contains("mrs")->"mrs";else->"rules"}
        r.addProperty("fileKey",fileKey);r.addProperty("systemPicker",waitSystem)
        if(!waitSystem){
            choose(ResourceAcceptanceInputs.name(input,fileKey))
            if(case=="image-reading"||case=="icons-after"){
                val window=if(image)EditorCommitWindow.FILE_PICKER_READING else EditorCommitWindow.ICON_FILE_APPLIED
                compose.waitUntil(20_000){EditorCommitProbe.visits(window)==1}
            }else{
                awaitTag(if(fileKey=="mrs")"rule_resource_preview" else "rule_resource_content")
                val m=ruleVm();compose.waitUntil(20_000){!m.state.value.busy}
                if(case in setOf("preview-yaml","preview-error")){
                    // 作者 long：规则识别器允许Text回退，破损YAML未必报错；域名和CIDR混排才触发明确的格式冲突。
                    compose.onNodeWithTag("rule_resource_content").performTextReplacement(if(case=="preview-error")INVALID_RULES else PRIVATE)
                    compose.onNodeWithTag("rule_resource_content").performTextInputSelection(TextRange(3,7));closeSoftKeyboard()
                    if(case=="preview-error"){
                        compose.onNodeWithTag("rule_resource_save").performClick();compose.waitUntil(20_000){!m.state.value.busy && m.state.value.error!=null}
                    }
                }
                if(case in setOf("rule-before","rule-after")){
                    val window=if(case=="rule-before")EditorCommitWindow.RESOURCE_INTENT else EditorCommitWindow.RESOURCE_APPLIED
                    EditorCommitProbe.arm(window);compose.onNodeWithTag("rule_resource_save").performClick();compose.waitUntil(20_000){EditorCommitProbe.visits(window)==1}
                    r.addProperty("commitId",checkNotNull(m.state.value.session?.intent).token)
                }
                withTimeout(20_000){while(e.ruleResourceDraftStore().read()?.let{gson.fromJson(it.decodeToString(),RuleResourceSession::class.java).uiDraft}!=m.state.value.session?.uiDraft || m.state.value.session?.uiDraft==null)delay(100)}
            }
        }
        val request=when{image->profileVm().groupImagePicker.state.value.request;case.startsWith("icons")->iconsVm().filePicker.state.value.request;else->ruleVm().filePicker.state.value.request}
        request?.let{r.addProperty("pickerToken",it.token)}
        if(!waitSystem && exists("rule_resource_content")){
            val field=compose.onNodeWithTag("rule_resource_content").fetchSemanticsNode().config
            val range=field[SemanticsProperties.TextSelectionRange];r.addProperty("selectionStart",range.start);r.addProperty("selectionEnd",range.end)
        }
        r.addProperty("ready",true);marker().writeText(r.toString())
        ins.sendStatus(0,Bundle().apply{putString("kclash_resource_cold_ready",JsonObject().apply{
            addProperty("case",case);addProperty("preparedPid",android.os.Process.myPid());addProperty("systemPicker",waitSystem)
            addProperty("cancel",case.endsWith("cancel"));addProperty("filename",ResourceAcceptanceInputs.name(input,fileKey));addProperty("ready",true)
        }.toString())})
        if(waitSystem){
            if(image){val m=profileVm();withTimeout(20_000){while(e.profileDraftStore().read()?.let{gson.fromJson(it.decodeToString(),ProfileEditorDraftSnapshot::class.java).groups?.uiDraft}!=m.proxyGroupsEditorState.value.uiDraft || m.proxyGroupsEditorState.value.uiDraft==null)delay(100)}}
            return@runBlocking
        }
        awaitCancellation()
    }

    @Test fun verifyAutomaticPickerRecoveryAndClean():Unit=runBlocking{
        val case=case();val input=ResourceAcceptanceInputs.manifest();val r=JsonParser.parseString(marker().readText()).asJsonObject
        assertEquals(case,r["case"].asString);assertNotEquals(r["preparedPid"].asInt,android.os.Process.myPid())
        r["verifiedPid"]?.let{assertNotEquals(it.asInt,android.os.Process.myPid())}
        val e=entry();val repo=e.profileRepository();repo.profilesLoaded.first{it}
        val image=case.startsWith("image");val icons=case.startsWith("icons")
        if(image){
            val m=profileVm();m.awaitEditorDraftRestoration()
            compose.waitUntil(20_000){m.proxyGroupsEditorState.value.profile?.id==r["profileId"].asLong && !m.proxyGroupsEditorState.value.isLoading}
            assertEquals(r["profileSignature"].asString,signature(repo.profileFile(r["profileId"].asLong)))
            if(case=="image-return"){
                compose.waitUntil(20_000){!exists("group_icon_search") && exists("proxy_group_form_confirm")}
                compose.onNodeWithTag("proxy_group_form_confirm").performClick();compose.onNodeWithTag("proxy_group_list_save").performClick()
                compose.waitUntil(30_000){m.proxyGroupsEditorState.value.profile==null}
                assertEquals(ProfileGroupIconData.encode(ResourceAcceptanceInputs.bytes(input,"image")),ProxyGroupDrafts.read(repo.profileFile(r["profileId"].asLong).readText()).first().text("icon"))
            }else{
                awaitTag("group_icon_search")
                if(case=="image-reading"){
                    awaitTag("group_icon_error");assertFalse(m.groupImagePicker.state.value.busy)
                    compose.onNodeWithTag("group_icon_local").performClick();choose(ResourceAcceptanceInputs.name(input,"image"))
                    compose.waitUntil(20_000){!exists("group_icon_search") && exists("proxy_group_form_confirm")}
                    assertEquals(r["profileSignature"].asString,signature(repo.profileFile(r["profileId"].asLong)))
                }
            }
        }else if(icons){
            val m=iconsVm();m.filePicker.awaitReady();compose.waitUntil(20_000){!m.iconBusy.value}
            val rows=e.icons().all();val fresh=rows.filter{it.id !in originalIcons().map(ResourceIconSet::id)}
            if(case.endsWith("cancel"))assertTrue(fresh.isEmpty())else{assertEquals(1,fresh.size);assertEquals(input.getString("token"),fresh.single().name)}
            assertNull(m.filePicker.state.value.request)
            // 作者 long：交付已完成的图库无需重开页面才能核对，重复进程仍只能存在同一资源ID。
        }else{
            val m=ruleVm();compose.waitUntil(25_000){m.state.value.ready && !m.state.value.busy}
            if(case=="rule-after"){
                assertNull(m.state.value.editor);assertEquals(1,e.rules().all().count{it.id !in originalRules().map(RuleResource::id)})
            }else if(case.endsWith("cancel")){assertNull(m.state.value.editor);noImports()}
            else{
                val mrs=case.contains("mrs");awaitTag(if(mrs)"rule_resource_preview" else "rule_resource_content")
                if(case in setOf("preview-yaml","preview-error")){
                    val field=compose.onNodeWithTag("rule_resource_content").fetchSemanticsNode().config
                    assertEquals(if(case=="preview-error")INVALID_RULES else PRIVATE,field[SemanticsProperties.EditableText].text)
                    assertEquals(TextRange(r["selectionStart"].asInt,r["selectionEnd"].asInt),field[SemanticsProperties.TextSelectionRange])
                    if(case=="preview-error")compose.onNodeWithTag("rule_resource_editor_error").performScrollTo().assertIsDisplayed()
                    noImports()
                    assertPrivate()
                    val keep=InstrumentationRegistry.getArguments().getString("kclashResourceKeep")=="true"
                    if(keep){r.addProperty("verifiedPid",android.os.Process.myPid());marker().writeText(r.toString());return@runBlocking}
                    compose.runOnIdle{m.closeEditor()};compose.waitUntil(20_000){m.state.value.editor==null}
                }else{
                    noImports()
                    compose.onNodeWithTag("rule_resource_save").performTouchInput{click();advanceEventTime(40);click()}
                    compose.waitUntil(30_000){!m.state.value.busy && m.state.value.editor==null}
                    val fresh=e.rules().all().filter{it.id !in originalRules().map(RuleResource::id)};assertEquals(1,fresh.size)
                    assertArrayEquals(ResourceAcceptanceInputs.bytes(input,if(mrs)"mrs" else "rules"),e.rules().file(fresh.single()).readBytes())
                    if(mrs)assertEquals("mrs",fresh.single().format)
                }
            }
        }
        if(case in setOf("rule-after","icons-after") && InstrumentationRegistry.getArguments().getString("kclashResourceKeep")=="true"){
            r.addProperty("verifiedPid",android.os.Process.myPid());marker().writeText(r.toString());return@runBlocking
        }
        cleanup(r)
        ins.sendStatus(0,Bundle().apply{putString("kclash_resource_cold_verified","case=$case;formal-preserved=true;own-fixture-cleared=true")})
    }
    @Test fun inspectRetainedPickerState():Unit=runBlocking {
        case();val e=entry()
        for((name,store) in listOf("rule" to e.ruleFilePickerStore(),"icons" to e.iconFilePickerStore(),"image" to e.groupImagePickerStore())) {
            val request=store.read()?.let{gson.fromJson(it.decodeToString(),FilePickerRequest::class.java)}
            ins.sendStatus(2,Bundle().apply{putString("kclash_picker_retained","kind=$name;phase=${request?.phase};error=${request?.error};bytes=${request?.bytes?.size};owner=${request?.owner}")})
        }
        val m=ruleVm();ins.sendStatus(2,Bundle().apply{putString("kclash_rule_retained","ready=${m.state.value.ready};editor=${m.state.value.editor!=null};error=${m.state.value.error}")})
    }
    @Test fun recoverExactOwnInterruptedResourceFixture():Unit=runBlocking{
        case();assertEquals("true",InstrumentationRegistry.getArguments().getString("kclashResourceFixtureRecovery"));cleanup(JsonParser.parseString(marker().readText()).asJsonObject)
    }
    private suspend fun cleanup(r:JsonObject){
        val e=entry();val repo=e.profileRepository();repo.profilesLoaded.first{it}
        val rules=ruleVm();val icons=iconsVm();val profiles=profileVm()
        compose.waitUntil(20_000){rules.state.value.ready && !rules.state.value.busy && !icons.iconBusy.value}
        compose.runOnIdle{rules.closePage{};icons.closeIcons{};profiles.closeProxyGroupsEditor()}
        compose.waitUntil(20_000){rules.state.value.session==null && rules.filePicker.state.value.request==null && icons.filePicker.state.value.request==null && profiles.proxyGroupsEditorState.value.profile==null}
        compose.runOnIdle{profiles.closeEditor()};compose.waitUntil(20_000){profiles.editorState.value.profile==null}
        profiles.groupImagePicker.cancel()
        val original=originalRules()
        for(row in e.rules().all().filter{it.id !in original.map(RuleResource::id)}){
            check(row.label.startsWith(r["inputToken"].asString));e.rules().delete(row)
            for(name in setOf(row.fileName,row.coreFileName)){
                val file=File(e.rules().file(row).parentFile,name);check(name.matches(Regex("[a-zA-Z0-9-]+\\.rules(\\.mrs)?")));check(!file.exists()||file.delete())
            }
        }
        for(row in e.icons().all().filter{it.id !in originalIcons().map(ResourceIconSet::id)}){check(row.name==r["inputToken"].asString);e.icons().delete(row.id)}
        r["profileId"]?.asLong?.let{id->e.database().profiles().getById(id)?.let{check(it.label=="resource-cold-${r["token"].asString}");repo.deleteProfile(id)}}
        for(key in setOf(RULE_RESOURCES_KEY,ICON_SETS_KEY,"group_icon_history"))e.database().withTransaction{
            val row=originalRows().firstOrNull{it.key==key};if(row==null)e.database().appPreferences().delete(key)else e.database().appPreferences().put(row)
        }
        assertEquals(original,e.rules().all());assertEquals(originalIcons(),e.icons().all())
        assertEquals(r["ruleFiles"].asString,sha(gson.toJson(e.rules().all().associate{it.id to signature(e.rules().file(it))})))
        val rows=e.database().profiles().getAll().sortedBy{it.id};assertEquals(r["rows"].asString,sha(gson.toJson(rows)));assertEquals(r["files"].asString,signatures(rows.map{it.id}))
        assertEquals(r["preferences"].asString,preferences());assertEquals(r["scripts"].asString,scripts());assertEquals(r["selected"].asLong,repo.currentProfileId.value)
        for(store in listOf(e.ruleFilePickerStore(),e.ruleResourceDraftStore(),e.iconFilePickerStore(),e.groupImagePickerStore(),e.profileDraftStore()))assertNull(store.read())
        assertEquals(CoreRuntimeStatus.STOPPED,e.runtimeManager().state.value);backup().write(null);assertTrue(marker().delete())
    }
    private fun assertPrivate(){
        val b=Bundle();compose.runOnIdle{ins.callActivityOnSaveInstanceState(compose.activity,b)}
        val p=Parcel.obtain();val bytes=try{p.writeBundle(b);p.marshall()}finally{p.recycle()}
        for(cs in listOf(Charsets.UTF_8,Charsets.UTF_16LE,Charsets.UTF_16BE))assertFalse(bytes.toString(cs).contains(PRIVATE))
        val file=File(compose.activity.noBackupFilesDir,"rule-resource-draft.v1");if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(PRIVATE))
    }
    private fun root()=ins.uiAutomation.rootInActiveWindow
    private fun nodes(node:AccessibilityNodeInfo):Sequence<AccessibilityNodeInfo> = sequence{yield(node);for(i in 0 until node.childCount)node.getChild(i)?.let{yieldAll(nodes(it))}}
    private fun click(node:AccessibilityNodeInfo):Boolean{var current:AccessibilityNodeInfo?=node;repeat(6){if(current?.isClickable==true&&current.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;current=current?.parent};return false}
    private fun waitFor(condition:()->Boolean){val until=SystemClock.uptimeMillis()+15_000;while(SystemClock.uptimeMillis()<until){compose.mainClock.advanceTimeByFrame();if(condition())return;SystemClock.sleep(100)};error("系统文件选择超时")}
    private fun picker(){
        waitFor{root()?.packageName?.toString()?.let{it.endsWith("documentsui")||it=="com.android.providers.media.module"}==true}
        if(root()?.packageName?.toString()=="com.android.providers.media.module"){
            check(click(nodes(checkNotNull(root())).first{it.contentDescription?.toString() in setOf("更多选项","More options")}))
            waitFor{nodes(root()?:return@waitFor false).firstOrNull{it.text?.toString() in setOf("浏览…","浏览...","Browse…","Browse...")}?.let(::click)==true}
        }
        waitFor{root()?.packageName?.toString()?.endsWith("documentsui")==true}
        // 作者 long：图片网格隐藏文件名，先使用系统列表视图，后续才能按绑定清单选择原文件。
        root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/sub_menu_list")?.firstOrNull()?.let {
            check(click(it));waitFor{root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/sub_menu_grid")?.isNotEmpty()==true}
        }
    }
    private fun choose(name:String){
        picker();fun select()=root()?.findAccessibilityNodeInfosByText(name)?.firstOrNull{it.text?.toString()==name}?.let(::click)==true
        if(!select()){
            val node=checkNotNull(root());val drawer=nodes(node).firstOrNull{it.contentDescription?.toString()?.contains("根目录")==true}
            check(drawer!=null&&click(drawer))
            waitFor{nodes(root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/roots_list")?.firstOrNull()?:return@waitFor false).firstOrNull{it.viewIdResourceName=="android:id/title"&&it.text?.toString()=="Redmi Note 8 Pro"}?.let(::click)==true}
            waitFor{root()?.findAccessibilityNodeInfosByText("Download")?.firstOrNull{it.text?.toString()=="Download"}?.let(::click)==true}
            waitFor{if(select())true else{root()?.findAccessibilityNodeInfosByViewId("com.android.documentsui:id/dir_list")?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);false}}
        }
        waitFor{root()?.packageName?.toString()=="com.lonnnnnng.flclash"};compose.waitForIdle()
    }
    private companion object{
        const val PRIVATE="payload:\n  - DOMAIN,private-resource-cold.test\n"
        const val INVALID_RULES="payload:\n  - example.test\n  - 192.0.2.0/24\n"
    }
}
