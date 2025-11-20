package dev.dimension.flare.ui.model.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class RssDateParserTest {
    @Test
    fun parsesIsoLocalDateTimeAsUtc() {
        val instant = parseRssDateToInstant("2025-11-20T10:00:00")
        assertEquals(Instant.parse("2025-11-20T10:00:00Z"), instant)
    }

    @Test
    fun fallsBackToRfc2822() {
        val instant = parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00 +0000")
        assertEquals(Instant.parse("2025-11-20T10:00:00Z"), instant)
    }

    @Test
    fun returnsNullForInvalidInput() {
        assertNull(parseRssDateToInstant("not-a-date"))
    }
}
