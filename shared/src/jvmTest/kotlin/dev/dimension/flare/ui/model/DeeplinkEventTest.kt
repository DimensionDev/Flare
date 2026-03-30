package dev.dimension.flare.ui.model

import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DeeplinkEventTest {
    @Test
    fun retryTranslationRoundTrips() {
        val event =
            DeeplinkEvent(
                accountKey = MicroBlogKey("account", "example.com"),
                translationEvent =
                    DeeplinkEvent.TranslationEvent.RetryTranslation(
                        statusKey = MicroBlogKey("status", "example.com"),
                    ),
            )

        val parsed = assertNotNull(DeeplinkEvent.parse(event.toUri()))
        assertEquals(event, parsed)
    }
}
