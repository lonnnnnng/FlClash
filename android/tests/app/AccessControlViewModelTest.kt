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
