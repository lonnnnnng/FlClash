package com.lonnnnnng.flclash.ui

import com.lonnnnnng.flclash.data.AppLanguage
import com.lonnnnnng.flclash.data.AppTheme
import org.junit.Assert.*
import org.junit.Test

class AppearanceSettingsTest {
    @Test fun explicitLanguageOverridesSystemAndUnsupportedSystemFallsBackToEnglish() {
        assertFalse(AppLanguage.SYSTEM.english("zh"))
        assertTrue(AppLanguage.SYSTEM.english("en"))
        assertTrue(AppLanguage.SYSTEM.english("ja"))
        assertFalse(AppLanguage.CHINESE.english("en"))
        assertTrue(AppLanguage.ENGLISH.english("zh"))
    }
    @Test fun explicitThemeOverridesBothSystemModes() {
        for (system in listOf(false, true)) {
            assertEquals(system, AppTheme.SYSTEM.dark(system))
            assertTrue(AppTheme.DARK.dark(system))
            assertFalse(AppTheme.LIGHT.dark(system))
        }
    }
}
