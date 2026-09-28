package dev.dimension.flare.ui.component.status

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class MediaClickPolicyTest {
    @Test
    fun openingTheSecondIdenticalImageKeepsItsIndex() {
        val image = UiMedia.Image("image", "preview", null, 100f, 100f, false)
        val post =
            UiTimelineV2.Post(
                platformId = "test",
                images = persistentListOf(image, image),
                sensitive = false,
                contentWarning = null,
                user = null,
                content = UiTranslatableText("post".toUiPlainText()),
                actions = persistentListOf(),
                poll = null,
                statusKey = MicroBlogKey("post", "example.com"),
                card = null,
                createdAt = Instant.fromEpochSeconds(1).toUi(),
                clickEvent = ClickEvent.Noop,
                accountType = AccountType.Guest,
            )
        var opened: DeeplinkRoute? = null

        post.openMedia(index = 1, launcher = { opened = DeeplinkRoute.parse(it) })

        val route = assertIs<DeeplinkRoute.Media.StatusMedia>(opened)
        assertEquals(1, route.index)
        assertEquals("preview", route.preview)
        assertEquals(2, post.images.size)
    }
}
