package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Instant

public object WireLimitsV1 {
    public const val MAX_PAGE_SIZE: Int = 100
    public const val MAX_PAGE_ITEMS: Int = 200
    public const val MAX_ID_LENGTH: Int = 512
    public const val MAX_CURSOR_LENGTH: Int = 4_096
    public const val MAX_ENTITY_TOKEN_LENGTH: Int = 8_192
    public const val MAX_ACTION_TOKEN_LENGTH: Int = 8_192
    public const val MAX_PENDING_PAYLOAD_BYTES: Int = 64 * 1_024
    public const val MAX_CREDENTIAL_BYTES: Int = 64 * 1_024
    public const val MAX_ERROR_TEXT_LENGTH: Int = 4_096
    public const val MAX_RICH_TEXT_LENGTH: Int = 1_000_000
}

public fun PageRequestV1.requireValid() {
    require(limit in 1..WireLimitsV1.MAX_PAGE_SIZE) { "Invalid page size" }
    require(cursor == null || cursor.length <= WireLimitsV1.MAX_CURSOR_LENGTH) { "Cursor is too large" }
    require(parameters.size <= 32) { "Too many page parameters" }
    require(parameters.all { (key, value) -> key.isWireName() && value.length <= 4_096 }) {
        "Invalid page parameter"
    }
}

public fun <T> PageV1<T>.requireValid() {
    require(items.size <= WireLimitsV1.MAX_PAGE_ITEMS) { "Too many page items" }
    require(olderCursor == null || olderCursor.length <= WireLimitsV1.MAX_CURSOR_LENGTH) { "Older cursor is too large" }
    require(newerCursor == null || newerCursor.length <= WireLimitsV1.MAX_CURSOR_LENGTH) { "Newer cursor is too large" }
}

public fun <T> PageV1<T>.requireValid(validateItem: (T) -> Unit) {
    requireValid()
    items.forEach(validateItem)
}

public fun ComposeConfigV1.requireValid() {
    text?.let { require(it.maxLength in 1..1_000_000) { "Invalid compose text limit" } }
    media?.let {
        require(it.maxCount in 0..100) { "Invalid compose media count" }
        require(it.minCountForNew in 0..it.maxCount) { "Invalid compose minimum media count" }
        require(it.maxBytes in 1..1_073_741_824) { "Invalid compose media byte limit" }
        require(it.altTextMaxLength in 0..100_000) { "Invalid alt text limit" }
        require(it.supportedMimeTypes.size <= 128) { "Too many MIME types" }
        require(it.supportedMimeTypes.all(MIME_TYPE::matches)) { "Invalid MIME type" }
    }
    visibility?.let {
        require(it.allowed.isNotEmpty()) { "Visibility set is empty" }
        require(it.default in it.allowed) { "Default visibility is not allowed" }
    }
    poll?.let { require(it.maxOptions in 2..100) { "Invalid poll option limit" } }
    language?.let { require(it.maxCount in 1..32) { "Invalid language count" } }
}

public fun ProfileV1.requireValid() {
    key.requireValid()
    require(handle.isNotBlank() && handle.length <= 512 && handle.none(Char::isISOControl)) { "Invalid profile handle" }
    require(displayName.length <= 1_024) { "Profile display name is too long" }
    avatarUrl.requireHttpsUrl()
    bannerUrl.requireHttpsUrl()
    url.requireHttpsUrl()
    description?.requireValid()
    require(fields.size <= 128) { "Too many profile fields" }
    require(fields.map(ProfileFieldV1::name).distinct().size == fields.size) { "Duplicate profile field" }
    fields.forEach { field ->
        require(field.name.length <= 1_024 && field.name.none(Char::isISOControl)) { "Invalid profile field name" }
        field.value.requireValid()
    }
    listOf(followersCount, followingCount, postsCount).filterNotNull().forEach {
        require(it >= 0) { "Invalid profile count" }
    }
    require(entityToken == null || entityToken.length <= WireLimitsV1.MAX_ENTITY_TOKEN_LENGTH) {
        "Profile entity token is too large"
    }
    actions.requireValid()
}

public fun PostV1.requireValid(depth: Int = 0) {
    require(depth <= 4) { "Post nesting is too deep" }
    key.requireValid()
    author.requireValid()
    createdAt.requireTimestamp()
    content.requireValid()
    url.requireHttpsUrl()
    require(media.size <= 100) { "Too many media items" }
    media.forEach(MediaV1::requireValid)
    repost?.requireValid(depth + 1)
    replyTo?.requireValid()
    require(spoilerText == null || spoilerText.length <= WireLimitsV1.MAX_RICH_TEXT_LENGTH) { "Post spoiler is too long" }
    listOf(favouritesCount, repostsCount, repliesCount).filterNotNull().forEach {
        require(it >= 0) { "Invalid post count" }
    }
    require(entityToken == null || entityToken.length <= WireLimitsV1.MAX_ENTITY_TOKEN_LENGTH) {
        "Post entity token is too large"
    }
    actions.requireValid()
}

public fun HashtagV1.requireValid() {
    require(name.isNotBlank() && name.length <= 1_024 && name.none(Char::isISOControl)) { "Invalid hashtag" }
    url.requireHttpsUrl()
}

public fun RelationV1.requireValid() {
    profileKey.requireValid()
    require(actionTokens.size <= SemanticActionV1.entries.size) { "Too many relation action tokens" }
    require(actionTokens.values.all { it.length <= WireLimitsV1.MAX_ACTION_TOKEN_LENGTH }) { "Relation action token is too large" }
}

public fun NotificationV1.requireValid() {
    require(id.isNotBlank() && id.length <= WireLimitsV1.MAX_ID_LENGTH) { "Invalid notification ID" }
    createdAt.requireTimestamp()
    actor?.requireValid()
    post?.requireValid()
    message?.requireValid()
}

public fun SocialListV1.requireValid() {
    require(id.isNotBlank() && id.length <= WireLimitsV1.MAX_ID_LENGTH) { "Invalid list ID" }
    require(title.isNotBlank() && title.length <= 4_096) { "Invalid list title" }
    require(memberCount == null || memberCount >= 0) { "Invalid list member count" }
    require(entityToken == null || entityToken.length <= WireLimitsV1.MAX_ENTITY_TOKEN_LENGTH) { "List entity token is too large" }
}

public fun DirectMessageRoomV1.requireValid() {
    key.requireValid()
    require(title.length <= 4_096) { "Direct-message room title is too long" }
    require(participants.size <= 256) { "Too many direct-message participants" }
    participants.forEach(ProfileV1::requireValid)
    lastMessage?.let { message ->
        message.requireValid()
        require(message.roomKey == key) { "Direct-message room contains a message from another room" }
    }
    require(unreadCount >= 0) { "Invalid direct-message unread count" }
    require(entityToken == null || entityToken.length <= WireLimitsV1.MAX_ENTITY_TOKEN_LENGTH) {
        "Direct-message room entity token is too large"
    }
}

public fun DirectMessageV1.requireValid() {
    key.requireValid()
    roomKey.requireValid()
    sender.requireValid()
    createdAt.requireTimestamp()
    content.requireValid()
    require(entityToken == null || entityToken.length <= WireLimitsV1.MAX_ENTITY_TOKEN_LENGTH) {
        "Direct-message entity token is too large"
    }
}

public fun ArticleV1.requireValid() {
    key.requireValid()
    require(title.isNotBlank() && title.length <= 16_384) { "Invalid article title" }
    author?.requireValid()
    createdAt?.requireTimestamp()
    content.requireValid()
    url.requireHttpsUrl()
    coverUrl.requireHttpsUrl()
}

public fun GalleryV1.requireValid() {
    key.requireValid()
    require(title.length <= 16_384) { "Gallery title is too long" }
    author?.requireValid()
    createdAt.requireTimestamp()
    content?.requireValid()
    url.requireHttpsUrl(required = true)
    require(images.isNotEmpty() && images.size <= 100) { "Invalid gallery image count" }
    images.forEach { media ->
        require(media.type == MediaTypeV1.Image) { "Gallery media must be an image" }
        media.requireValid()
    }
    require(entityToken == null || entityToken.length <= WireLimitsV1.MAX_ENTITY_TOKEN_LENGTH) {
        "Gallery entity token is too large"
    }
    actions.requireValid()
}

public fun TimelineDescriptorV1.requireValid() {
    require(id.isWireName()) { "Invalid timeline ID" }
    title.requireValid()
    require(parameters.size <= 32) { "Too many timeline parameters" }
    require(parameters.all { (key, value) -> key.isWireName() && value.length <= 4_096 }) { "Invalid timeline parameter" }
}

public fun TimelineSectionV1.requireValid() {
    require(id.isWireName()) { "Invalid timeline section ID" }
    title.requireValid()
    timelines.requireValid(TimelineDescriptorV1::requireValid)
}

public fun MutationResultV1.requireValid() {
    when (this) {
        is MutationResultV1.UpdatedPost -> {
            post.requireValid()
        }

        is MutationResultV1.UpdatedProfile -> {
            profile.requireValid()
        }

        is MutationResultV1.UpdatedRelation -> {
            relation.requireValid()
        }

        MutationResultV1.Deleted,
        MutationResultV1.NoChange,
        -> {}

        is MutationResultV1.Invalidate -> {
            require(keys.size <= WireLimitsV1.MAX_PAGE_ITEMS) { "Too many invalidated entities" }
            require(keys.distinct().size == keys.size) { "Duplicate invalidated entity" }
            keys.forEach(EntityKeyV1::requireValid)
        }
    }
}

public fun MutationResultV1.requireDeleteResult() {
    requireValid()
    require(this === MutationResultV1.Deleted || this === MutationResultV1.NoChange) {
        "Delete operation returned an incompatible mutation result"
    }
}

public fun MutationResultV1.requirePostMutationResult(expectedKey: EntityKeyV1) {
    requireValid()
    expectedKey.requireValid()
    when (this) {
        is MutationResultV1.UpdatedPost ->
            require(post.key == expectedKey) { "Post mutation returned a different post" }

        is MutationResultV1.Invalidate ->
            require(keys.isNotEmpty() && keys.all { it == expectedKey }) {
                "Post mutation invalidated an unrelated entity"
            }

        MutationResultV1.Deleted,
        MutationResultV1.NoChange,
        -> Unit

        is MutationResultV1.UpdatedProfile,
        is MutationResultV1.UpdatedRelation,
        -> require(false) { "Post mutation returned an incompatible mutation result" }
    }
}

public fun MutationResultV1.requireRelationMutationResult(expectedKey: EntityKeyV1) {
    requireValid()
    expectedKey.requireValid()
    when (this) {
        is MutationResultV1.UpdatedRelation ->
            require(relation.profileKey == expectedKey) { "Relation mutation returned a different profile" }

        MutationResultV1.NoChange -> Unit
        else -> require(false) { "Relation mutation returned an incompatible mutation result" }
    }
}

public fun ComposeResultV1.requireValid() {
    post.requireValid()
}

public fun CountResultV1.requireValid() {
    require(value >= 0) { "Invalid count" }
}

public fun WireTextV1.requireValid() {
    require((value != null) xor (key != null)) { "Wire text must contain exactly one of value or key" }
    require(value == null || value.isNotBlank()) { "Wire text value is blank" }
    require(value == null || value.length <= WireLimitsV1.MAX_ERROR_TEXT_LENGTH) { "Wire text is too long" }
    if (key != null) {
        require(key.isWireName()) { "Invalid Wire text key" }
        require(!fallback.isNullOrBlank()) { "Localized Wire text requires a fallback" }
        require(fallback.length <= WireLimitsV1.MAX_ERROR_TEXT_LENGTH) { "Wire text fallback is too long" }
    }
    require(args.size <= 16) { "Too many Wire text arguments" }
    require(
        args.all { (name, value) ->
            name.isWireName() &&
                if (value.isString) {
                    value.content.length <= 1_024
                } else {
                    value.booleanOrNull != null || value.doubleOrNull?.isFinite() == true
                }
        },
    ) { "Invalid Wire text arguments" }
}

public fun CookieSnapshotV1.requireValid() {
    require(cookies.size <= 512) { "Too many Cookie values" }
    require(
        cookies.all { cookie ->
            cookie.sourceUrl.length <= 8_192 &&
                COOKIE_NAME.matches(cookie.name) &&
                cookie.value.length <= MAX_COOKIE_VALUE_LENGTH
        },
    ) { "Invalid Cookie snapshot" }
    require(encodedSize(this, CookieSnapshotV1.serializer()) <= WireLimitsV1.MAX_PENDING_PAYLOAD_BYTES) {
        "Cookie snapshot is too large"
    }
}

public fun LoginTransitionV1.requireValid() {
    when (this) {
        LoginTransitionV1.Pending -> {
            return
        }

        is LoginTransitionV1.ExternalBrowser -> {
            require(url.isNotBlank() && url.length <= 8_192) { "Invalid external login URL" }
            require(encodedJsonSize(pendingPayload) <= WireLimitsV1.MAX_PENDING_PAYLOAD_BYTES) {
                "Login pending payload is too large"
            }
        }

        is LoginTransitionV1.WebCookie -> {
            require(startUrl.isNotBlank() && startUrl.length <= 8_192) { "Invalid Cookie login URL" }
        }

        is LoginTransitionV1.Success -> {
            value.requireValid()
        }
    }
}

public fun LoginSuccessV1.requireValid() {
    require(accountId.isNotBlank() && accountId.length <= WireLimitsV1.MAX_ID_LENGTH) { "Invalid login account ID" }
    require(origin.isNotBlank() && origin.length <= 8_192) { "Invalid login origin" }
    require(encodedJsonSize(credential) <= WireLimitsV1.MAX_CREDENTIAL_BYTES) { "Login credential is too large" }
    profile.requireValid()
    require(capabilities.size <= 32) { "Too many negotiated capabilities" }
    require(
        capabilities.all { (capability, operations) ->
            CAPABILITY_ID.matches(capability) &&
                operations.size <= 32 &&
                operations.all(METHOD_NAME::matches)
        },
    ) { "Invalid negotiated capabilities" }
    composeConfig?.requireValid()
}

private fun encodedJsonSize(value: JsonElement): Int =
    PluginJsonV1
        .encodeToString(JsonElement.serializer(), value)
        .encodeToByteArray()
        .size

private fun <T> encodedSize(
    value: T,
    serializer: kotlinx.serialization.KSerializer<T>,
): Int =
    PluginJsonV1
        .encodeToString(serializer, value)
        .encodeToByteArray()
        .size

public fun EntityKeyV1.requireValid() {
    require(id.isNotBlank() && id.length <= WireLimitsV1.MAX_ID_LENGTH) { "Invalid entity id" }
    require(host.isNotBlank() && host.length <= 253) { "Invalid entity host" }
    PluginUrlPolicy.requireOrigin("https://$host")
}

private fun RichTextV1.requireValid() {
    require(value.length <= WireLimitsV1.MAX_RICH_TEXT_LENGTH) { "Rich text is too large" }
}

private fun MediaV1.requireValid() {
    require(id.isNotBlank() && id.length <= WireLimitsV1.MAX_ID_LENGTH) { "Invalid media ID" }
    url.requireHttpsUrl(required = true)
    previewUrl.requireHttpsUrl()
    require(description == null || description.length <= 100_000) { "Media description is too long" }
    require(width == null || width in 1..100_000) { "Invalid media width" }
    require(height == null || height in 1..100_000) { "Invalid media height" }
    require(durationMillis == null || durationMillis in 0..604_800_000) { "Invalid media duration" }
}

private fun List<ActionDescriptorV1>.requireValid() {
    require(size <= SemanticActionV1.entries.size) { "Too many actions" }
    require(map(ActionDescriptorV1::action).distinct().size == size) { "Duplicate action" }
    require(all { it.actionToken == null || it.actionToken.length <= WireLimitsV1.MAX_ACTION_TOKEN_LENGTH }) {
        "Action token is too large"
    }
    require(all { it.count == null || it.count >= 0 }) { "Invalid action count" }
}

private fun String.requireTimestamp() {
    require(length <= 128) { "Timestamp is too long" }
    Instant.parse(this)
}

private fun String?.requireHttpsUrl(required: Boolean = false) {
    if (isNullOrBlank()) {
        require(!required) { "Required HTTPS URL is missing" }
        return
    }
    require(length <= 8_192) { "URL is too long" }
    val parsed = Url(this)
    require(parsed.protocol == URLProtocol.HTTPS && parsed.host.isNotBlank() && parsed.user == null && parsed.password == null) {
        "URL must use HTTPS without credentials"
    }
}

private fun String.isWireName(): Boolean = WIRE_NAME.matches(this)

private val WIRE_NAME = Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")
private val MIME_TYPE = Regex("[A-Za-z0-9!#$&^_.+-]+/(?:[A-Za-z0-9!#$&^_.+-]+|\\*)")
private val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}")
private val CAPABILITY_ID = Regex("[a-z0-9.-]+/[a-z0-9.-]+")
private val METHOD_NAME = Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")
private const val MAX_COOKIE_VALUE_LENGTH = 16 * 1_024
