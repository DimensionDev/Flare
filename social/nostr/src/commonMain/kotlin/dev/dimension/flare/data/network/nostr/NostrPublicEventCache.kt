package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent

// Read-only archive fallback; it never receives a signer or publishes an event.
internal class NostrPublicEventCache(
    private val request: suspend (String, JsonObject) -> List<QuartzEvent> = ::requestPrimalEvents,
) {
    private val events = MutableStateFlow<Map<String, QuartzEvent>>(emptyMap())

    fun find(filters: List<Filter>): List<QuartzEvent>? {
        val snapshot = events.value
        val complete = filters.all { filter -> filter.ids.isNotEmpty() && filter.ids.all { it in snapshot } }
        return if (complete) select(snapshot.values, filters) else null
    }

    suspend fun fetch(
        filters: List<Filter>,
        homePubkey: String? = null,
    ): List<QuartzEvent> {
        val received =
            coroutineScope {
                filters
                    .map { filter ->
                        async {
                            when {
                                filter.ids.isNotEmpty() -> {
                                    request("events", buildJsonObject { put("event_ids", strings(filter.ids)) })
                                }

                                filter.tags.isNotEmpty() || filter.search != null -> {
                                    emptyList()
                                }

                                filter.kinds == listOf(MetadataEvent.KIND) && filter.authors.isNotEmpty() -> {
                                    request("user_infos", buildJsonObject { put("pubkeys", strings(filter.authors)) })
                                }

                                filter.kinds == listOf(ContactListEvent.KIND) && filter.authors.size == 1 -> {
                                    request("contact_list", buildJsonObject { put("pubkey", filter.authors.single()) })
                                }

                                filter.kinds.isNotEmpty() && filter.kinds.all { it in listOf(1, 6, 16) } &&
                                    (homePubkey != null || filter.authors.size == 1) -> {
                                    val pubkey = homePubkey ?: filter.authors.single()
                                    val feeds =
                                        if (homePubkey != null &&
                                            filter.authors.any { it != homePubkey }
                                        ) {
                                            listOf("follows", "authored")
                                        } else {
                                            listOf("authored")
                                        }
                                    val responses =
                                        feeds
                                            .map { notes ->
                                                async {
                                                    try {
                                                        Result.success(
                                                            request(
                                                                "feed",
                                                                buildJsonObject {
                                                                    put("pubkey", pubkey)
                                                                    put("notes", notes)
                                                                    put("include_replies", true)
                                                                    put("kinds", JsonArray(filter.kinds.map(::JsonPrimitive)))
                                                                    put("limit", filter.limit ?: 20)
                                                                    filter.until?.let { put("until", it) }
                                                                    filter.since?.let { put("since", it) }
                                                                },
                                                            ),
                                                        )
                                                    } catch (error: CancellationException) {
                                                        throw error
                                                    } catch (error: Exception) {
                                                        Result.failure(error)
                                                    }
                                                }
                                            }.awaitAll()
                                    if (responses.all { it.isFailure }) throw requireNotNull(responses.first().exceptionOrNull())
                                    responses.flatMap { it.getOrNull().orEmpty() }
                                }

                                else -> {
                                    emptyList()
                                }
                            }
                        }
                    }.awaitAll()
                    .flatten()
            }.filter { runCatching { it.verify() }.getOrDefault(false) }.distinctBy { it.id }
        // Reuse immutable references bundled with feeds, bounded to this service's session.
        events.update { (it + received.associateBy { event -> event.id }).toList().takeLast(2_000).toMap() }
        return select(received, filters)
    }

    private fun select(
        events: Collection<QuartzEvent>,
        filters: List<Filter>,
    ): List<QuartzEvent> =
        filters
            .flatMap { filter ->
                val query = filter.toQuartz()
                events
                    .filter(query::match)
                    .sortedWith(
                        compareByDescending<QuartzEvent> {
                            it.createdAt
                        }.thenBy { it.id },
                    ).take(filter.limit ?: Int.MAX_VALUE)
            }.distinctBy { it.id }
            .sortedWith(compareByDescending<QuartzEvent> { it.createdAt }.thenBy { it.id })

    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))
}

private suspend fun requestPrimalEvents(
    method: String,
    parameters: JsonObject,
): List<QuartzEvent> =
    ktorClient(ProxyRef.System) { install(WebSockets) }.use { http ->
        requestPrimalEvents(method, parameters, http)
    }

internal suspend fun requestPrimalEvents(
    method: String,
    parameters: JsonObject,
    http: HttpClient,
): List<QuartzEvent> {
    val events = mutableListOf<QuartzEvent>()
    val completed =
        withTimeoutOrNull(8.seconds) {
            http.webSocket(urlString = "wss://cache2.primal.net/v1") {
                try {
                    val subscription = "flare-cache"
                    send(
                        Frame.Text(
                            JsonArray(
                                listOf(
                                    JsonPrimitive("REQ"),
                                    JsonPrimitive(subscription),
                                    buildJsonObject {
                                        put("cache", JsonArray(listOf(JsonPrimitive(method), parameters)))
                                    },
                                ),
                            ).toString(),
                        ),
                    )
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val message = runCatching { JSON.parseToJsonElement(frame.readText()).jsonArray }.getOrNull() ?: continue
                        val type = (message.firstOrNull() as? JsonPrimitive)?.content
                        if (type == "NOTICE") error("Nostr public cache rejected the request")
                        if ((message.getOrNull(1) as? JsonPrimitive)?.content != subscription) continue
                        when (type) {
                            "EVENT" -> {
                                message.getOrNull(2)?.let { raw ->
                                    runCatching { QuartzEvent.fromJson(raw.toString()) }.getOrNull()?.let(events::add)
                                }
                            }

                            "EOSE" -> {
                                return@webSocket
                            }

                            "CLOSED" -> {
                                error("Nostr public cache closed the request")
                            }
                        }
                    }
                    error("Nostr public cache disconnected before completing the request")
                } finally {
                    cancel()
                }
            }
            true
        }
    check(completed == true) { "Nostr public cache request timed out" }
    return events
}
