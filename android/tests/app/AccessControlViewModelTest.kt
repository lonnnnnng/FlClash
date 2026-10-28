package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.common.AccessControlMode
import com.lonnnnnng.flclash.common.AccessSortType
import com.lonnnnnng.flclash.service.models.AccessControlProps
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 作者 long：Access 页面先按用户开启的来源过滤，再按 Flutter 相同的已选置顶和排序规则展示，
 * 这样“全选当前结果”只会作用于用户真正看见的应用，不会误改隐藏应用。
 */
class AccessControlViewModelTest {
    @Test
    fun `access state compares settings with persisted baseline but ignores search text`() {
        val persisted = AccessControlProps(
            enable = true,
            mode = AccessControlMode.REJECT_SELECTED,
            rejectList = listOf("com.example.browser"),
        )

        val searched = AccessControlUiState(
            settings = persisted,
            persistedSettings = persisted,
            searchQuery = "browser",
            isLoading = false,
        )
        assertEquals(false, searched.hasUnsavedChanges)

        val changed = searched.copy(settings = persisted.copy(enable = false))
        assertEquals(true, changed.hasUnsavedChanges)
    }

    @Test
    fun `smart selection separates domestic and other launcher packages`() {
        val apps = listOf(
            InstalledAppInfo("com.tencent.mm", "微信"),
            InstalledAppInfo("com.google.android.gms", "Google Play 服务"),
            InstalledAppInfo("com.mxtech.videoplayer.ad", "MX Player"),
            InstalledAppInfo("org.mozilla.firefox", "Firefox"),
        )

        val chinaPackages = apps.map { it.packageName }
            .filter(ChinaPackageMatcher::isChinaPackage)
            .toSet()

        assertEquals(setOf("com.tencent.mm"), chinaPackages)
        assertEquals(
            SmartSelectionResult(
                acceptList = listOf("com.google.android.gms", "com.mxtech.videoplayer.ad", "org.mozilla.firefox"),
                rejectList = listOf("com.tencent.mm"),
            ),
            buildSmartSelection(apps, chinaPackages),
        )
    }

    @Test
    fun `clipboard export follows the active mode and newline format`() {
        val settings = AccessControlProps(
            enable = true,
            mode = AccessControlMode.ACCEPT_SELECTED,
            acceptList = listOf("com.example.first", "com.example.second"),
            rejectList = listOf("com.example.hidden"),
        )

        assertEquals(
            "com.example.first\ncom.example.second",
            serializeAccessClipboardList(settings),
        )
    }

    @Test
    fun `clipboard import replaces only active mode and preserves first occurrence order`() {
        val settings = AccessControlProps(
            enable = true,
            mode = AccessControlMode.REJECT_SELECTED,
            acceptList = listOf("com.example.keep"),
            rejectList = listOf("com.example.old"),
        )

        val imported = parseAccessClipboardList("com.example.a\n\ncom.example.a\ncom.example.b")
        assertEquals(listOf("com.example.a", "", "com.example.b"), imported)
        assertEquals(
            settings.copy(rejectList = listOf("com.example.a", "", "com.example.b")),
            settings.withImportedCurrentPackageList(imported),
        )
    }

    @Test
    fun `visible apps filter system and no network entries before name sorting`() {
        val state = AccessControlUiState(
            settings = AccessControlProps(
                enable = true,
                mode = AccessControlMode.ACCEPT_SELECTED,
                acceptList = listOf("com.example.video"),
                sort = AccessSortType.NAME,
                isFilterSystemApp = true,
                isFilterNonInternetApp = true,
            ),
            apps = listOf(
                InstalledAppInfo("com.example.zoo", "Zoo", isSystem = false, hasInternetPermission = true),
                InstalledAppInfo("com.example.video", "Video", isSystem = false, hasInternetPermission = true),
                InstalledAppInfo("com.example.system", "Alpha System", isSystem = true, hasInternetPermission = true),
                InstalledAppInfo("com.example.offline", "Beta Offline", isSystem = false, hasInternetPermission = false),
            ),
            isLoading = false,
        )

        assertEquals(
            listOf("com.example.video", "com.example.zoo"),
            state.visibleApps.map { it.packageName },
        )
    }

    @Test
    fun `time sorting keeps selected apps first and newer apps before older apps`() {
        val state = AccessControlUiState(
            settings = AccessControlProps(
                enable = true,
                mode = AccessControlMode.REJECT_SELECTED,
                rejectList = listOf("com.example.old"),
                sort = AccessSortType.TIME,
                isFilterSystemApp = false,
                isFilterNonInternetApp = false,
            ),
            apps = listOf(
                InstalledAppInfo("com.example.new", "New", lastUpdateTime = 300L),
                InstalledAppInfo("com.example.old", "Old", lastUpdateTime = 100L),
                InstalledAppInfo("com.example.middle", "Middle", lastUpdateTime = 200L),
            ),
            isLoading = false,
        )

        assertEquals(
            listOf("com.example.old", "com.example.new", "com.example.middle"),
            state.visibleApps.map { it.packageName },
        )
    }
}
