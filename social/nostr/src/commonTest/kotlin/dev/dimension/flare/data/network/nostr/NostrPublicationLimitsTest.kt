package dev.dimension.flare.data.network.nostr

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NostrPublicationLimitsTest {
    @Test
    fun incompatibleRelaysAreSkippedWithoutReducingThePublicationQuorum() =
        runTest {
            for (limitedRelays in listOf(1, 2)) {
                QuartzTestRelays(backgroundScope).use { network ->
                    val excluded = network.relays.take(limitedRelays).toSet()
                    val excludedHosts = excluded.map { Url(it.url).host }
                    val http =
                        HttpClient(MockEngine) {
                            engine {
                                addHandler { request ->
                                    val limit = if (request.url.host in excludedHosts) 1 else 100
                                    respondOk("""{"limitation":{"max_content_length":$limit}}""")
                                }
                            }
                        }
                    network.service(relayInfoClient = http).use { service ->
                        if (limitedRelays == 1) {
                            val id = service.composeNote("note")
                            assertEquals(network.relays - excluded, network.acceptedPublications.map { it.first }.toSet())
                            assertEquals(setOf(id), network.acceptedPublications.map { it.second.id }.toSet())
                            network.acceptedRelays = network.relays.drop(2).toSet()
                            assertFailsWith<IllegalStateException> { service.composeNote("another note") }
                        } else {
                            assertFailsWith<IllegalArgumentException> { service.composeNote("note") }
                            assertTrue(network.published.isEmpty())
                        }
                    }
                }
            }
        }

    @Test
    fun eventWireLengthIsCheckedBeforePublishingEvenWhenContentFits() =
        runTest {
            QuartzTestRelays(backgroundScope).use { network ->
                val http =
                    HttpClient(MockEngine) {
                        engine { addHandler { respondOk("""{"limitation":{"max_content_length":100,"max_message_length":1}}""") } }
                    }
                network.service(relayInfoClient = http).use { service ->
                    assertFailsWith<IllegalArgumentException> { service.composeNote("note") }
                    assertTrue(network.published.isEmpty())
                }
            }
        }
}
