package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.bindMediaProxy
import dev.dimension.flare.ui.model.withProxy
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// Agent history is account-local; shared profile caches must continue excluding viewer identity.
internal object AgentProfileSnapshotSerializer : KSerializer<UiProfile> {
    private const val PROXY = "_agentMediaProxy"
    private val delegate = UiProfile.serializer()
    override val descriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: UiProfile,
    ) {
        val json = (encoder as JsonEncoder).json
        val fields = json.encodeToJsonElement(delegate, value).jsonObject
        val scopes =
            buildSet {
                value.avatar?.let { add(it.proxy) }
                value.banner?.let { add(it.proxy) }
                val texts =
                    listOfNotNull(value.name, value.description) +
                        when (val bottom = value.bottomContent) {
                            is UiProfile.BottomContent.Fields -> bottom.fields.values
                            is UiProfile.BottomContent.Iconify -> bottom.items.values
                            null -> emptyList()
                        }
                for (text in texts) {
                    addAll(text.imageMedias.map { it.proxy })
                    text.renderRuns
                        .filterIsInstance<RenderContent.Text>()
                        .flatMap { it.runs }
                        .filterIsInstance<RenderRun.Text>()
                        .mapNotNull { it.style.imageLink }
                        .forEach { add(it.proxy) }
                }
            }
        val proxy = scopes.singleOrNull()?.takeUnless { it == ProxyRef.Unbound }
        encoder.encodeJsonElement(
            JsonObject(fields + if (proxy == null) emptyMap() else mapOf(PROXY to json.encodeToJsonElement(ProxyRef.serializer(), proxy))),
        )
    }

    override fun deserialize(decoder: Decoder): UiProfile {
        val input = decoder as JsonDecoder
        val fields = input.decodeJsonElement().jsonObject
        val profile = input.json.decodeFromJsonElement(delegate, JsonObject(fields - PROXY))
        val proxy = fields[PROXY]?.let { input.json.decodeFromJsonElement(ProxyRef.serializer(), it) }
        return if (proxy == null || proxy == ProxyRef.Unbound) profile else profile.withProxy(proxy)
    }
}

internal object AgentPostSnapshotSerializer : KSerializer<UiTimelineV2.Post> {
    private val delegate = UiTimelineV2.Post.serializer()
    override val descriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: UiTimelineV2.Post,
    ) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): UiTimelineV2.Post = delegate.deserialize(decoder).bindMediaProxy()
}
