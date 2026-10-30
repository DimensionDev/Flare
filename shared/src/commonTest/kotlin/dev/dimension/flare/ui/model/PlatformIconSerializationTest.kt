package dev.dimension.flare.ui.model

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.data.model.IconType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlatformIconSerializationTest {
    @Test
    fun changingPlatformIconsInvalidatesVisiblePostsAndUserCards() {
        val user = createSampleUser()
        val post = createSampleStatus(user)
        val icon = IconType.Url("file:///plugins/new-logo.png")
        val updated = post.copy(platformIcon = icon)
        assertNotEquals(post.renderHash, updated.renderHash)
        val item = UiTimelineV2.TimelinePostItem(post)
        assertNotEquals(item.renderHash, item.copy(post = updated).renderHash)
        val profileItem = UiTimelineV2.User(value = user, createdAt = post.createdAt, statusKey = user.key, accountType = post.accountType)
        assertNotEquals(profileItem.renderHash, profileItem.copy(value = user.copy(platformIcon = icon)).renderHash)
    }

    @Test
    fun platformIconsSurviveProfileAndPostCachesAndChatCards() {
        listOf(UiIcon.Mastodon.asType(), IconType.Url("file:///plugins/platform-logo.png")).forEach { icon ->
            val user = createSampleUser().copy(platformIcon = icon)
            val post = createSampleStatus(user)
            assertEquals(icon, post.platformIcon)
            listOf(post.encodeJson().decodeJson<UiTimelineV2.Post>(), post.encodeProtobuf().decodeProtobuf()).forEach { restored ->
                assertEquals(icon, restored.platformIcon)
                assertEquals(icon, restored.user?.platformIcon)
            }
            assertEquals(icon, user.encodeProtobuf().decodeProtobuf<UiProfile>().platformIcon)
        }
    }
}
