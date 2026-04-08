package dev.dimension.flare.ui.presenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AiConfigPresenterTest {
    @Test
    fun normalizeExcludedLanguages_trimsSplitsAndDeduplicatesByCanonicalLanguage() {
        assertEquals(
            listOf("en-US", "zh-CN", "ja"),
            normalizeExcludedLanguages(
                listOf(
                    " en-US ",
                    "en-GB",
                    "zh-CN, zh-Hans",
                    "\nja\n",
                ),
            ),
        )
    }
}
