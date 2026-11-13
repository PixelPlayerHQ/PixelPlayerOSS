package com.lostf1sh.pixelplayeross.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLanguageTest {

    private val labels = mapOf(
        AppLanguage.SYSTEM to "System default",
        AppLanguage.ENGLISH to "English",
        AppLanguage.ARABIC to "العربية",
        AppLanguage.GERMAN to "Deutsch",
        AppLanguage.SPANISH to "Español",
        AppLanguage.FRENCH to "Français",
        AppLanguage.INDONESIAN to "Bahasa Indonesia",
        AppLanguage.ITALIAN to "Italiano",
        AppLanguage.KOREAN to "한국어",
        AppLanguage.NORWEGIAN_BOKMAL to "Norsk bokmål",
        AppLanguage.RUSSIAN to "Русский",
        AppLanguage.TURKISH to "Türkçe",
        AppLanguage.ZH_RCN to "简体中文",
        AppLanguage.VIETNAMESE to "Tiếng Việt",
        AppLanguage.PORTUGUESE_BR to "Português (Brasil)"
    )

    private val context = mockk<Context> {
        every { getString(any<Int>()) } answers {
            val resId = firstArg<Int>()
            labels.entries.first { it.key.labelRes == resId }.value
        }
    }

    @Test
    fun `normalize maps null, blank and unsupported tags to the system language`() {
        assertEquals(AppLanguage.SYSTEM.tag, AppLanguage.normalize(null))
        assertEquals(AppLanguage.SYSTEM.tag, AppLanguage.normalize(""))
        assertEquals(AppLanguage.SYSTEM.tag, AppLanguage.normalize("   "))
        assertEquals(AppLanguage.SYSTEM.tag, AppLanguage.normalize("klingon"))
    }

    @Test
    fun `normalize matches supported tags case-insensitively and returns the canonical tag`() {
        assertEquals("zh-CN", AppLanguage.normalize("zh-cn"))
        assertEquals("zh-CN", AppLanguage.normalize("ZH-CN"))
        assertEquals("tr", AppLanguage.normalize(" TR "))
        assertEquals("nb", AppLanguage.normalize("nb"))
    }

    @Test
    fun `every supported tag survives normalize unchanged`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language.tag, AppLanguage.normalize(language.tag))
        }
    }

    @Test
    fun `language options list the system default first and then the rest by label`() {
        val options = AppLanguage.getLanguageOptions(context)

        assertEquals(AppLanguage.SYSTEM.tag, options.keys.first())
        assertEquals("System default", options.getValue(AppLanguage.SYSTEM.tag))
        assertEquals(AppLanguage.supportedLanguageTags, options.keys)

        val otherLabels = options.entries.drop(1).map { it.value }
        assertEquals(otherLabels.sortedBy { it.lowercase() }, otherLabels)
        assertEquals("Türkçe", options.getValue("tr"))
    }
}
