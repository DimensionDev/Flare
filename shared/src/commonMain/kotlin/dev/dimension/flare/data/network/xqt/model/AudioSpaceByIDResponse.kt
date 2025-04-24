package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AudioSpaceByIDResponse(
    val data: Data? = null,
)

@Serializable
internal data class Data(
    val audioSpace: AudioSpace? = null,
)

@Serializable
internal data class AudioSpace(
    val metadata: Metadata? = null,
    @SerialName("is_subscribed")
    val isSubscribed: Boolean? = null,
    val participants: Participants? = null,
    val sharings: Sharings? = null,
)

@Serializable
internal data class Metadata(
    @SerialName("rest_id")
    val restID: String? = null,
    val state: String? = null,
    val title: String? = null,
    @SerialName("media_key")
    val mediaKey: String? = null,
    @SerialName("created_at")
    val createdAt: Long? = null,
    @SerialName("started_at")
    val startedAt: Long? = null,
    @SerialName("updated_at")
    val updatedAt: Long? = null,
    @SerialName("ended_at")
    val endedAt: Long? = null,
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("creator_results")
    val creatorResults: RResults? = null,
    @SerialName("conversation_controls")
    val conversationControls: Long? = null,
    @SerialName("disallow_join")
    val disallowJoin: Boolean? = null,
    @SerialName("is_employee_only")
    val isEmployeeOnly: Boolean? = null,
    @SerialName("is_locked")
    val isLocked: Boolean? = null,
    @SerialName("is_muted")
    val isMuted: Boolean? = null,
    @SerialName("is_space_available_for_clipping")
    val isSpaceAvailableForClipping: Boolean? = null,
    @SerialName("is_space_available_for_replay")
    val isSpaceAvailableForReplay: Boolean? = null,
    @SerialName("mentioned_users")
    val mentionedUsers: List<MentionedUser>? = null,
    @SerialName("narrow_cast_space_type")
    val narrowCastSpaceType: Long? = null,
    @SerialName("no_incognito")
    val noIncognito: Boolean? = null,
    @SerialName("total_replay_watched")
    val totalReplayWatched: Long? = null,
    @SerialName("total_live_listeners")
    val totalLiveListeners: Long? = null,
    @SerialName("tweet_results")
    val tweetResults: MetadataTweetResults? = null,
    @SerialName("max_guest_sessions")
    val maxGuestSessions: Long? = null,
    @SerialName("max_admin_capacity")
    val maxAdminCapacity: Long? = null,
)

internal typealias RResults = UserResults

@Serializable
internal class SliceInfo

@Serializable
internal data class URL(
    @SerialName("display_url")
    val displayURL: String? = null,
    @SerialName("expanded_url")
    val expandedURL: String? = null,
    val url: String? = null,
    val indices: List<Long>? = null,
)

@Serializable
internal data class Professional(
    @SerialName("rest_id")
    val restID: String? = null,
    @SerialName("professional_type")
    val professionalType: String? = null,
    val category: List<Category>? = null,
)

@Serializable
internal data class Category(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("icon_name")
    val iconName: String? = null,
)

@Serializable
internal data class MentionedUser(
    @SerialName("rest_id")
    val restID: String? = null,
)

@Serializable
internal data class MetadataTweetResults(
    val result: PurpleResult? = null,
)

@Serializable
internal data class PurpleResult(
    @SerialName("__typename")
    val typename: String? = null,
    @SerialName("rest_id")
    val restID: String? = null,
    val core: Core? = null,
    val card: Card? = null,
    @SerialName("unmention_data")
    val unmentionData: SliceInfo? = null,
    @SerialName("edit_control")
    val editControl: EditControl? = null,
    @SerialName("is_translatable")
    val isTranslatable: Boolean? = null,
    val views: Views? = null,
    val source: String? = null,
    val legacy: FluffyLegacy? = null,
)

@Serializable
internal data class Card(
    @SerialName("rest_id")
    val restID: String? = null,
    val legacy: CardLegacy? = null,
)

@Serializable
internal data class CardLegacy(
    @SerialName("binding_values")
    val bindingValues: List<BindingValue>? = null,
    @SerialName("card_platform")
    val cardPlatform: CardPlatform? = null,
    val name: String? = null,
    val url: String? = null,
//    @SerialName("user_refs_results")
//    val userRefsResults: JsonArray? = null
)

@Serializable
internal data class BindingValue(
    val key: String? = null,
    val value: Value? = null,
)

@Serializable
internal data class Value(
    @SerialName("string_value")
    val stringValue: String? = null,
    val type: String? = null,
    @SerialName("scribe_key")
    val scribeKey: String? = null,
)

@Serializable
internal data class CardPlatform(
    val platform: Platform? = null,
)

@Serializable
internal data class Platform(
    val audience: Audience? = null,
    val device: Device? = null,
)

@Serializable
internal data class Audience(
    val name: String? = null,
)

@Serializable
internal data class Device(
    val name: String? = null,
    val version: String? = null,
)

@Serializable
internal data class Core(
    @SerialName("user_results")
    val userResults: RResults? = null,
)

@Serializable
internal data class EditControl(
    @SerialName("edit_tweet_ids")
    val editTweetIDS: List<String>? = null,
    @SerialName("editable_until_msecs")
    val editableUntilMsecs: String? = null,
    @SerialName("is_edit_eligible")
    val isEditEligible: Boolean? = null,
    @SerialName("edits_remaining")
    val editsRemaining: String? = null,
)

@Serializable
internal data class FluffyLegacy(
    @SerialName("bookmark_count")
    val bookmarkCount: Long? = null,
    val bookmarked: Boolean? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("conversation_id_str")
    val conversationIDStr: String? = null,
    @SerialName("display_text_range")
    val displayTextRange: List<Long>? = null,
    val entities: Entities? = null,
    @SerialName("favorite_count")
    val favoriteCount: Long? = null,
    val favorited: Boolean? = null,
    @SerialName("full_text")
    val fullText: String? = null,
    @SerialName("is_quote_status")
    val isQuoteStatus: Boolean? = null,
    val lang: String? = null,
    @SerialName("possibly_sensitive")
    val possiblySensitive: Boolean? = null,
    @SerialName("possibly_sensitive_editable")
    val possiblySensitiveEditable: Boolean? = null,
    @SerialName("quote_count")
    val quoteCount: Long? = null,
    @SerialName("reply_count")
    val replyCount: Long? = null,
    @SerialName("retweet_count")
    val retweetCount: Long? = null,
    val retweeted: Boolean? = null,
    @SerialName("user_id_str")
    val userIDStr: String? = null,
    @SerialName("id_str")
    val idStr: String? = null,
)

@Serializable
internal data class Views(
    val count: String? = null,
    val state: String? = null,
)

@Serializable
internal data class Participants(
    val total: Long? = null,
    val admins: List<Admin>? = null,
    val speakers: List<Admin>? = null,
    val listeners: List<Admin>? = null,
)

@Serializable
internal data class Admin(
    @SerialName("periscope_user_id")
    val periscopeUserID: String? = null,
    val start: Long? = null,
    @SerialName("twitter_screen_name")
    val twitterScreenName: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarURL: String? = null,
    @SerialName("is_verified")
    val isVerified: Boolean? = null,
    @SerialName("is_muted_by_admin")
    val isMutedByAdmin: Boolean? = null,
    @SerialName("is_muted_by_guest")
    val isMutedByGuest: Boolean? = null,
    @SerialName("user_results")
    val userResults: AudioUserResults? = null,
)

@Serializable
internal data class AudioUserResults(
    @SerialName("rest_id")
    val restID: String? = null,
    val result: FluffyResult? = null,
)

@Serializable
internal data class FluffyResult(
    @SerialName("__typename")
    val typename: String? = null,
    @SerialName("identity_profile_labels_highlighted_label")
    val identityProfileLabelsHighlightedLabel: SliceInfo? = null,
    @SerialName("is_blue_verified")
    val isBlueVerified: Boolean? = null,
    val legacy: SliceInfo? = null,
)

@Serializable
internal data class Sharings(
    val items: List<Item>? = null,
    @SerialName("slice_info")
    val sliceInfo: SliceInfo? = null,
)

@Serializable
internal data class Item(
    @SerialName("sharing_id")
    val sharingID: String? = null,
    @SerialName("created_at_ms")
    val createdAtMS: Long? = null,
    @SerialName("updated_at_ms")
    val updatedAtMS: Long? = null,
    @SerialName("shared_item")
    val sharedItem: SharedItem? = null,
    @SerialName("user_results")
    val userResults: RResults? = null,
)

@Serializable
internal data class SharedItem(
    @SerialName("__typename")
    val typename: String? = null,
    @SerialName("tweet_results")
    val tweetResults: SharedItemTweetResults? = null,
)

@Serializable
internal data class SharedItemTweetResults(
    val result: TentacledResult? = null,
)

@Serializable
internal data class TentacledResult(
    @SerialName("__typename")
    val typename: String? = null,
    @SerialName("rest_id")
    val restID: String? = null,
    val core: Core? = null,
    @SerialName("unmention_data")
    val unmentionData: SliceInfo? = null,
    @SerialName("edit_control")
    val editControl: EditControl? = null,
    @SerialName("is_translatable")
    val isTranslatable: Boolean? = null,
    val views: Views? = null,
    val source: String? = null,
    val legacy: TentacledLegacy? = null,
    @SerialName("quoted_status_result")
    val quotedStatusResult: QuotedStatusResult? = null,
)

@Serializable
internal data class TentacledLegacy(
    @SerialName("bookmark_count")
    val bookmarkCount: Long? = null,
    val bookmarked: Boolean? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("conversation_id_str")
    val conversationIDStr: String? = null,
    @SerialName("display_text_range")
    val displayTextRange: List<Long>? = null,
    val entities: Entities? = null,
    @SerialName("extended_entities")
    val extendedEntities: ExtendedEntities? = null,
    @SerialName("favorite_count")
    val favoriteCount: Long? = null,
    val favorited: Boolean? = null,
    @SerialName("full_text")
    val fullText: String? = null,
    @SerialName("is_quote_status")
    val isQuoteStatus: Boolean? = null,
    val lang: String? = null,
    @SerialName("possibly_sensitive")
    val possiblySensitive: Boolean? = null,
    @SerialName("possibly_sensitive_editable")
    val possiblySensitiveEditable: Boolean? = null,
    @SerialName("quote_count")
    val quoteCount: Long? = null,
    @SerialName("reply_count")
    val replyCount: Long? = null,
    @SerialName("retweet_count")
    val retweetCount: Long? = null,
    val retweeted: Boolean? = null,
    @SerialName("user_id_str")
    val userIDStr: String? = null,
    @SerialName("id_str")
    val idStr: String? = null,
    @SerialName("quoted_status_id_str")
    val quotedStatusIDStr: String? = null,
    @SerialName("quoted_status_permalink")
    val quotedStatusPermalink: QuotedStatusPermalink? = null,
)

@Serializable
internal data class QuotedStatusPermalink(
    val url: String? = null,
    val expanded: String? = null,
    val display: String? = null,
)

@Serializable
internal data class QuotedStatusResult(
    val result: QuotedStatusResultResult? = null,
)

@Serializable
internal data class QuotedStatusResultResult(
    @SerialName("__typename")
    val typename: String? = null,
    @SerialName("rest_id")
    val restID: String? = null,
    val core: Core? = null,
    @SerialName("unmention_data")
    val unmentionData: SliceInfo? = null,
    @SerialName("edit_control")
    val editControl: EditControl? = null,
    @SerialName("is_translatable")
    val isTranslatable: Boolean? = null,
    val views: Views? = null,
    val source: String? = null,
    val legacy: StickyLegacy? = null,
)

@Serializable
internal data class StickyLegacy(
    @SerialName("bookmark_count")
    val bookmarkCount: Long? = null,
    val bookmarked: Boolean? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("conversation_id_str")
    val conversationIDStr: String? = null,
    @SerialName("display_text_range")
    val displayTextRange: List<Long>? = null,
    val entities: Entities? = null,
    @SerialName("extended_entities")
    val extendedEntities: ExtendedEntities? = null,
    @SerialName("favorite_count")
    val favoriteCount: Long? = null,
    val favorited: Boolean? = null,
    @SerialName("full_text")
    val fullText: String? = null,
    @SerialName("is_quote_status")
    val isQuoteStatus: Boolean? = null,
    val lang: String? = null,
    @SerialName("possibly_sensitive")
    val possiblySensitive: Boolean? = null,
    @SerialName("possibly_sensitive_editable")
    val possiblySensitiveEditable: Boolean? = null,
    @SerialName("quote_count")
    val quoteCount: Long? = null,
    @SerialName("reply_count")
    val replyCount: Long? = null,
    @SerialName("retweet_count")
    val retweetCount: Long? = null,
    val retweeted: Boolean? = null,
    @SerialName("user_id_str")
    val userIDStr: String? = null,
    @SerialName("id_str")
    val idStr: String? = null,
)
