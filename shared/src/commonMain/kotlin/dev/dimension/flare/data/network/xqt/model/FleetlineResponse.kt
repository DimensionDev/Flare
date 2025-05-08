package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FleetlineResponse(
    val threads: List<Thread>? = null,
//    @SerialName("hydrated_threads")
//    val hydratedThreads: JsonArray? = null,
    @SerialName("refresh_delay_secs")
    val refreshDelaySecs: Long? = null,
)

@Serializable
internal data class Thread(
    @SerialName("fully_read")
    val fullyRead: Boolean? = null,
    @SerialName("live_content")
    val liveContent: LiveContent? = null,
//    val mentions: JsonElement? = null,
    @SerialName("mentions_str")
    val mentionsStr: List<String>? = null,
    val participants: List<Double>? = null,
    @SerialName("participants_str")
    val participantsStr: List<String>? = null,
    @SerialName("thread_id")
    val threadID: String? = null,
    @SerialName("user_id")
    val userID: Long? = null,
    @SerialName("user_id_str")
    val userIDStr: String? = null,
)

@Serializable
internal data class LiveContent(
    val audiospace: Audiospace? = null,
)

@Serializable
internal data class Audiospace(
    @SerialName("broadcast_id")
    val broadcastID: String? = null,
    val id: String? = null,
    val title: String? = null,
    val state: String? = null,
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("is_employee_only")
    val isEmployeeOnly: Boolean? = null,
    @SerialName("narrow_cast_space_type")
    val narrowCastSpaceType: Long? = null,
//    @SerialName("invitees_twitter")
//    val inviteesTwitter: JsonArray? = null,
    @SerialName("creator_user_id")
    val creatorUserID: String? = null,
    @SerialName("creator_twitter_user_id")
    val creatorTwitterUserID: Long? = null,
    @SerialName("primary_admin_user_id")
    val primaryAdminUserID: String? = null,
//    @SerialName("admin_user_ids")
//    val adminUserIDS: JsonElement? = null,
//    @SerialName("pending_admin_user_ids")
//    val pendingAdminUserIDS: JsonElement? = null,
    @SerialName("admin_twitter_user_ids")
    val adminTwitterUserIDS: List<Long>? = null,
//    @SerialName("pending_admin_twitter_user_ids")
//    val pendingAdminTwitterUserIDS: JsonElement? = null,
    @SerialName("max_admin_capacity")
    val maxAdminCapacity: Long? = null,
    @SerialName("max_guest_sessions")
    val maxGuestSessions: Long? = null,
    @SerialName("conversation_controls")
    val conversationControls: Long? = null,
//    @SerialName("created_at")
//    val createdAt: JsonElement? = null,
//    @SerialName("updated_at")
//    val updatedAt: JsonElement? = null,
    @SerialName("ended_at")
    val endedAt: String? = null,
    val start: String? = null,
//    @SerialName("scheduled_start")
//    val scheduledStart: JsonElement? = null,
//    @SerialName("canceled_at")
//    val canceledAt: JsonElement? = null,
    val guests: List<Guest>? = null,
    val listeners: List<Double>? = null,
    @SerialName("social_proof")
    val socialProof: List<SocialProof>? = null,
//    @SerialName("copyright_violations")
//    val copyrightViolations: JsonElement? = null,
    @SerialName("is_locked")
    val isLocked: Boolean? = null,
//    val rules: JsonElement? = null,
//    val topics: JsonElement? = null,
    @SerialName("is_muted")
    val isMuted: Boolean? = null,
    @SerialName("is_space_creator_muted")
    val isSpaceCreatorMuted: Boolean? = null,
    val language: String? = null,
    @SerialName("enable_server_audio_transcription")
    val enableServerAudioTranscription: Boolean? = null,
    @SerialName("enable_grok_summary")
    val enableGrokSummary: Boolean? = null,
    @SerialName("grok_summary")
    val grokSummary: String? = null,
    @SerialName("is_space_available_for_replay")
    val isSpaceAvailableForReplay: Boolean? = null,
//    @SerialName("replay_start_time")
//    val replayStartTime: JsonElement? = null,
    @SerialName("is_trending")
    val isTrending: Boolean? = null,
    @SerialName("vf_safety_level")
    val vfSafetyLevel: Long? = null,
    @SerialName("is_featured")
    val isFeatured: Boolean? = null,
    @SerialName("is_creator_top_host")
    val isCreatorTopHost: Boolean? = null,
    @SerialName("num_tweets_with_space_link")
    val numTweetsWithSpaceLink: Long? = null,
    @SerialName("disallow_join")
    val disallowJoin: Boolean? = null,
    @SerialName("rsvp_count")
    val rsvpCount: Long? = null,
//    @SerialName("nsfw_label_pivot")
//    val nsfwLabelPivot: JsonElement? = null,
//    @SerialName("uk_ru_conflict_label_pivot")
//    val ukRuConflictLabelPivot: JsonElement? = null,
    @SerialName("total_participating")
    val totalParticipating: Long? = null,
    @SerialName("total_participating_public")
    val totalParticipatingPublic: Long? = null,
    @SerialName("total_participated")
    val totalParticipated: Long? = null,
    @SerialName("total_participated_public")
    val totalParticipatedPublic: Long? = null,
)

@Serializable
internal data class Guest(
    @SerialName("user_id")
    val userID: String? = null,
    @SerialName("session_uuid")
    val sessionUUID: String? = null,
    @SerialName("twitter_user_id")
    val twitterUserID: Double? = null,
)

@Serializable
internal data class SocialProof(
    @SerialName("user_id")
    val userID: Double? = null,
    @SerialName("user_id_str")
    val userIDStr: String? = null,
    val role: String? = null,
)
