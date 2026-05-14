package dev.dimension.flare.data.network.misskey.api.model

import kotlin.test.Test
import kotlin.test.assertContains

class MisskeyNotificationTypeTest {
    @Test
    fun notificationTypeIncludesLatestMisskeyValues() {
        val values = NotificationType.entries.map { it.value }

        assertContains(values, "scheduledNotePosted")
        assertContains(values, "scheduledNotePostFailed")
        assertContains(values, "chatRoomInvitationReceived")
    }

    @Test
    fun notificationFiltersIncludeLatestMisskeyValues() {
        val includeValues = INotificationsRequest.IncludeTypes.entries.map { it.value }
        val excludeValues = INotificationsRequest.ExcludeTypes.entries.map { it.value }

        listOf(
            "note",
            "scheduledNotePosted",
            "scheduledNotePostFailed",
            "roleAssigned",
            "chatRoomInvitationReceived",
            "exportCompleted",
            "test",
            "login",
            "createToken",
        ).forEach { value ->
            assertContains(includeValues, value)
            assertContains(excludeValues, value)
        }
    }
}
