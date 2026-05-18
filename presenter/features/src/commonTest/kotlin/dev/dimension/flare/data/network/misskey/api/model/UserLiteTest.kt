package dev.dimension.flare.data.network.misskey.api.model

import dev.dimension.flare.common.JSON
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class UserLiteTest {
    @Test
    fun emojisArrayIsTreatedAsEmptyMap() {
        val payload =
            """
            {
                "id": "user-id",
                "username": "sample",
                "emojis": []
            }
            """.trimIndent()

        val userLite = JSON.decodeFromString(UserLite.serializer(), payload)

        assertEquals(emptyMap(), userLite.emojis)
    }
}
