package dev.dimension.flare.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountTypeSerializationTest {
    @Test
    fun accountTypeKeepsPersistedPayloadsReadable() {
        val values =
            listOf(
                AccountType.Specific(MicroBlogKey(id = "id", host = "example.com")) to
                    """{"type":"dev.dimension.flare.model.AccountType.Specific","accountKey":{"id":"id","host":"example.com"}}""",
                AccountType.Guest to
                    """{"type":"dev.dimension.flare.model.AccountType.Guest"}""",
                AccountType.GuestHost("example.com") to
                    """{"type":"dev.dimension.flare.model.AccountType.GuestHost","host":"example.com"}""",
            )

        values.forEach { (value, persistedPayload) ->
            assertEquals(persistedPayload, Json.encodeToString(AccountType.serializer(), value))
            assertEquals(value, Json.decodeFromString(AccountType.serializer(), persistedPayload))
        }
    }
}
