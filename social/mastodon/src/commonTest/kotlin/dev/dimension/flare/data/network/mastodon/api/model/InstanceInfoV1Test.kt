package dev.dimension.flare.data.network.mastodon.api.model

import dev.dimension.flare.common.JSON
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class InstanceInfoV1Test {
    @Test
    fun decodesMaxCharactersFromConfiguration() {
        val instance =
            JSON.decodeFromString<InstanceInfoV1>(
                """
                {
                  "uri": "example.com",
                  "configuration": {
                    "statuses": {
                      "max_characters": 1234
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(1_234, instance.configuration?.statuses?.maxCharacters)
    }
}
