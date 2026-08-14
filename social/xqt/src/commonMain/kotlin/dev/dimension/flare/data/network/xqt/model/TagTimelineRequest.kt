package dev.dimension.flare.data.network.xqt.model

import dev.dimension.flare.data.network.xqt.XQTTimelineQueryIds
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
internal data class TagTimelineRequest(
    val variables: TagTimelineVariables,
    @Required
    val features: JsonObject = TAG_TIMELINE_FEATURES,
    @Required
    val queryId: String = XQTTimelineQueryIds.TAG_TIMELINE,
)

@Serializable
internal data class TagTimelineVariables(
    val count: Long,
    val cursor: String? = null,
    @Required
    val includePromotedContent: Boolean = true,
    @Required
    val requestContext: String = "launch",
    val tag: String,
    @Required
    val withCommunity: Boolean = true,
    @Required
    val seenTweetIds: List<String> = emptyList(),
)

private val TAG_TIMELINE_FEATURES =
    buildJsonObject {
        put("rweb_video_screen_enabled", false)
        put("rweb_cashtags_enabled", true)
        put("profile_label_improvements_pcf_label_in_post_enabled", true)
        put("responsive_web_profile_redirect_enabled", true)
        put("rweb_tipjar_consumption_enabled", false)
        put("verified_phone_label_enabled", false)
        put("creator_subscriptions_tweet_preview_api_enabled", true)
        put("responsive_web_graphql_timeline_navigation_enabled", true)
        put("premium_content_api_read_enabled", false)
        put("communities_web_enable_tweet_community_results_fetch", true)
        put("c9s_tweet_anatomy_moderator_badge_enabled", true)
        put("responsive_web_grok_analyze_button_fetch_trends_enabled", false)
        put("responsive_web_grok_analyze_post_followups_enabled", true)
        put("rweb_cashtags_composer_attachment_enabled", true)
        put("responsive_web_jetfuel_frame", true)
        put("responsive_web_grok_share_attachment_enabled", true)
        put("responsive_web_grok_annotations_enabled", true)
        put("articles_preview_enabled", true)
        put("responsive_web_edit_tweet_api_enabled", true)
        put("rweb_conversational_replies_downvote_enabled", false)
        put("graphql_is_translatable_rweb_tweet_is_translatable_enabled", true)
        put("view_counts_everywhere_api_enabled", true)
        put("longform_notetweets_consumption_enabled", true)
        put("responsive_web_twitter_article_tweet_consumption_enabled", true)
        put("content_disclosure_indicator_enabled", true)
        put("content_disclosure_ai_generated_indicator_enabled", true)
        put("responsive_web_grok_show_grok_translated_post", true)
        put("responsive_web_grok_analysis_button_from_backend", true)
        put("post_ctas_fetch_enabled", false)
        put("freedom_of_speech_not_reach_fetch_enabled", true)
        put("standardized_nudges_misinfo", true)
        put("tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled", true)
        put("longform_notetweets_rich_text_read_enabled", true)
        put("longform_notetweets_inline_media_enabled", false)
        put("responsive_web_grok_image_annotation_enabled", true)
        put("responsive_web_grok_imagine_annotation_enabled", true)
        put("responsive_web_grok_community_note_auto_translation_is_enabled", true)
        put("responsive_web_enhance_cards_enabled", false)
    }
