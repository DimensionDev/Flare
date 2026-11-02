package dev.dimension.flare.data.network.nostr

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.kotlinSerialization.BunkerResponseKSerializer
import dev.dimension.flare.common.JSON
import kotlinx.serialization.json.jsonObject

// shortcut: Quartz 1.16 misreads JSON nulls on JVM; remove when its Jackson decoder is fixed.
private val bunkerSerialization by lazy {
    JacksonMapper.mapper.registerModule(
        SimpleModule("FlareNostrBunkerResponses")
            .addDeserializer(
                BunkerResponse::class.java,
                object : StdDeserializer<BunkerResponse>(BunkerResponse::class.java) {
                    override fun deserialize(
                        parser: JsonParser,
                        context: DeserializationContext,
                    ): BunkerResponse =
                        BunkerResponseKSerializer.deserializeFromElement(
                            JSON.parseToJsonElement(parser.codec.readTree<JsonNode>(parser).toString()).jsonObject,
                        )
                },
            ),
    )
}

internal actual fun initializeNostrBunkerSerialization() {
    bunkerSerialization
}
