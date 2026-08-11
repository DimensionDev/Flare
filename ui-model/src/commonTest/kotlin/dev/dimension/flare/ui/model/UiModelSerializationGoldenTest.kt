package dev.dimension.flare.ui.model

import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
class UiModelSerializationGoldenTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun migrationPayloadsStayBinaryCompatible() {
        val accountKey = MicroBlogKey(id = "account", host = "example.com")
        val statusKey = MicroBlogKey(id = "status", host = "example.com")
        val timeline =
            UiTimelineV2.Message(
                statusKey = statusKey,
                icon = UiIcon.Notification,
                type =
                    UiTimelineV2.Message.Type.Localized(
                        data = UiTimelineV2.Message.Type.Localized.MessageId.Mention,
                        args = persistentListOf("alice", "post"),
                    ),
                createdAt = Instant.fromEpochMilliseconds(1_700_000_000_123).toUi(),
                clickEvent = ClickEvent.Noop,
                accountType = AccountType.Specific(accountKey),
            )
        val translatableText =
            UiTranslatableText(
                original = "Original".toUiPlainText(listOf("en")),
                translation = "译文".toUiPlainText(listOf("zh-CN")),
            )
        val deeplinkEvent =
            DeeplinkEvent(
                accountKey = accountKey,
                postEvent =
                    PostEvent.Mastodon.Bookmark(
                        postKey = statusKey,
                        bookmarked = true,
                        accountKey = accountKey,
                    ),
            )

        assertGolden(
            serializer = UiTimelineV2.serializer(),
            value = timeline,
            expectedJson = TIMELINE_JSON,
            expectedProto = TIMELINE_PROTO,
        )
        assertGolden(
            serializer = UiTranslatableText.serializer(),
            value = translatableText,
            expectedJson = TEXT_JSON,
            expectedProto = TEXT_PROTO,
        )
        assertGolden(
            serializer = DeeplinkEvent.serializer(),
            value = deeplinkEvent,
            expectedJson = EVENT_JSON,
            expectedProto = EVENT_PROTO,
        )
    }

    private fun <T> assertGolden(
        serializer: KSerializer<T>,
        value: T,
        expectedJson: String,
        expectedProto: String,
    ) {
        assertEquals(expectedJson, json.encodeToString(serializer, value))
        assertEquals(value, json.decodeFromString(serializer, expectedJson))
        assertEquals(expectedProto, ProtoBuf.encodeToHexString(serializer, value))
        assertEquals(value, ProtoBuf.decodeFromHexString(serializer, expectedProto))
    }

    private companion object {
        const val TIMELINE_JSON: String =
            "{\"type\":\"dev.dimension.flare.ui.model.UiTimelineV2.Message\",\"user\":null,\"statusKey\":{\"id\":\"status\",\"host\":\"example.com\"},\"icon\":\"Notification\",\"messageType\":{\"type\":\"dev.dimension.flare.ui.model.UiTimelineV2.Message.Type.Localized\",\"data\":\"Mention\",\"args\":[\"alice\",\"post\"]},\"createdAt\":1700000000123,\"clickEvent\":{\"type\":\"dev.dimension.flare.ui.model.ClickEvent.Noop\"},\"accountType\":{\"type\":\"dev.dimension.flare.model.AccountType.Specific\",\"accountKey\":{\"id\":\"account\",\"host\":\"example.com\"}},\"searchText\":null}"
        const val TIMELINE_PROTO: String =
            "0a316465762e64696d656e73696f6e2e666c6172652e75692e6d6f64656c2e556954696d656c696e6556322e4d65737361676512f30112150a06737461747573120b6578616d706c652e636f6d180122530a406465762e64696d656e73696f6e2e666c6172652e75692e6d6f64656c2e556954696d656c696e6556322e4d6573736167652e547970652e4c6f63616c697a6564120f08001205616c6963651204706f737428fbd095ffbc3132300a2c6465762e64696d656e73696f6e2e666c6172652e75692e6d6f64656c2e436c69636b4576656e742e4e6f6f7012003a4a0a2e6465762e64696d656e73696f6e2e666c6172652e6d6f64656c2e4163636f756e74547970652e537065636966696312180a160a076163636f756e74120b6578616d706c652e636f6d"
        const val TEXT_JSON: String =
            "{\"original\":{\"renderRuns\":[{\"type\":\"dev.dimension.flare.ui.render.RenderContent.Text\",\"runs\":[{\"type\":\"dev.dimension.flare.ui.render.RenderRun.Text\",\"text\":\"Original\",\"style\":{\"link\":null,\"bold\":false,\"italic\":false,\"strikethrough\":false,\"monospace\":false,\"code\":false,\"underline\":false,\"small\":false,\"time\":false}}],\"block\":{\"headingLevel\":null,\"textAlignment\":null,\"isListItem\":false,\"isBlockQuote\":false,\"isFigCaption\":false},\"hasLink\":false,\"hasInlineImage\":false}],\"isRtl\":false,\"raw\":\"Original\",\"innerText\":\"Original\",\"imageUrls\":[],\"isEmpty\":false,\"isLongText\":false,\"truncatedText\":null},\"translation\":{\"renderRuns\":[{\"type\":\"dev.dimension.flare.ui.render.RenderContent.Text\",\"runs\":[{\"type\":\"dev.dimension.flare.ui.render.RenderRun.Text\",\"text\":\"译文\",\"style\":{\"link\":null,\"bold\":false,\"italic\":false,\"strikethrough\":false,\"monospace\":false,\"code\":false,\"underline\":false,\"small\":false,\"time\":false}}],\"block\":{\"headingLevel\":null,\"textAlignment\":null,\"isListItem\":false,\"isBlockQuote\":false,\"isFigCaption\":false},\"hasLink\":false,\"hasInlineImage\":false}],\"isRtl\":false,\"raw\":\"译文\",\"innerText\":\"译文\",\"imageUrls\":[],\"isEmpty\":false,\"isLongText\":false,\"truncatedText\":null}}"
        const val TEXT_PROTO: String =
            "0a88010a700a306465762e64696d656e73696f6e2e666c6172652e75692e72656e6465722e52656e646572436f6e74656e742e54657874123c0a3a0a2c6465762e64696d656e73696f6e2e666c6172652e75692e72656e6465722e52656e64657252756e2e54657874120a0a084f726967696e616c10001a084f726967696e616c22084f726967696e616c1282010a6e0a306465762e64696d656e73696f6e2e666c6172652e75692e72656e6465722e52656e646572436f6e74656e742e54657874123a0a380a2c6465762e64696d656e73696f6e2e666c6172652e75692e72656e6465722e52656e64657252756e2e5465787412080a06e8af91e6968710001a06e8af91e696872206e8af91e69687"
        const val EVENT_JSON: String =
            "{\"accountKey\":{\"id\":\"account\",\"host\":\"example.com\"},\"translationEvent\":null,\"postEvent\":{\"type\":\"dev.dimension.flare.data.datasource.microblog.PostEvent.Mastodon.Bookmark\",\"postKey\":{\"id\":\"status\",\"host\":\"example.com\"},\"bookmarked\":true,\"accountKey\":{\"id\":\"account\",\"host\":\"example.com\"}}}"
        const val EVENT_PROTO: String =
            "0a160a076163636f756e74120b6578616d706c652e636f6d1a7e0a496465762e64696d656e73696f6e2e666c6172652e646174612e64617461736f757263652e6d6963726f626c6f672e506f73744576656e742e4d6173746f646f6e2e426f6f6b6d61726b12310a150a06737461747573120b6578616d706c652e636f6d10011a160a076163636f756e74120b6578616d706c652e636f6d"
    }
}
