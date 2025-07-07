package dev.dimension.flare.data.network.vvo.model

import dev.dimension.flare.common.JSON
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents.Companion.Format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.parse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

@Serializable(with = VVOResponseSerializer::class)
internal data class VVOResponse<T>(
    val data: T? = null,
    val ok: Long? = null,
    @SerialName("http_code")
    val httpCode: Long? = null,
)

@OptIn(ExperimentalSerializationApi::class)
private class VVOResponseSerializer<T>(
    private val dataSer: KSerializer<T>,
) : KSerializer<VVOResponse<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("VVOResponse") {
            element("data", dataSer.descriptor.nullable)
            element<Long?>("ok")
            element<Long?>("http_code")
        }

    override fun deserialize(decoder: Decoder): VVOResponse<T> {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: error("VVOResponseSerializer works only with JSON")

        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val dataElt = obj["data"]
        val data =
            dataElt?.let {
                runCatching { jsonDecoder.json.decodeFromJsonElement(dataSer, it) }
                    .getOrNull()
            }

        val ok = obj["ok"]?.jsonPrimitive?.longOrNull
        val httpCode = obj["http_code"]?.jsonPrimitive?.longOrNull

        return VVOResponse(data, ok, httpCode)
    }

    override fun serialize(
        encoder: Encoder,
        value: VVOResponse<T>,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: error("VVOResponseSerializer works only with JSON")

        val json = jsonEncoder.json
        val obj =
            buildJsonObject {
                value.data?.let { put("data", json.encodeToJsonElement(dataSer, it)) }
                value.ok?.let { put("ok", JsonPrimitive(it)) }
                value.httpCode?.let { put("http_code", JsonPrimitive(it)) }
            }
        jsonEncoder.encodeJsonElement(obj)
    }
}

@Serializable
internal data class TimelineData(
    val statuses: List<Status>? = null,
    val advertises: JsonArray? = null,
    val ad: JsonArray? = null,
    @SerialName("filtered_ids")
    val filteredIDS: JsonArray? = null,
    val hasvisible: Boolean? = null,
    @SerialName("previous_cursor")
    val previousCursor: Long? = null,
    @SerialName("next_cursor")
    val nextCursor: Long? = null,
    @SerialName("previous_cursor_str")
    val previousCursorStr: String? = null,
    @SerialName("next_cursor_str")
    val nextCursorStr: String? = null,
    @SerialName("total_number")
    val totalNumber: Long? = null,
    val interval: Long? = null,
    @SerialName("uve_blank")
    val uveBlank: Long? = null,
    @SerialName("since_id")
    val sinceID: Long? = null,
    @SerialName("since_id_str")
    val sinceIDStr: String? = null,
    @SerialName("max_id")
    val maxID: Long? = null,
    @SerialName("max_id_str")
    val maxIDStr: String? = null,
    @SerialName("has_unread")
    val hasUnread: Long? = null,
)

@Serializable
internal data class Status(
    val visible: Visible? = null,
    @SerialName("created_at")
    @Serializable(with = VVODateSerializer::class)
    val createdAt: Instant? = null,
    val id: String,
    val mid: String? = null,
    @SerialName("can_edit")
    val canEdit: Boolean? = null,
    @SerialName("show_additional_indication")
    val showAdditionalIndication: Long? = null,
    val text: String? = null,
    val source: String? = null,
    val favorited: Boolean? = null,
    @SerialName("pic_ids")
    val picIDS: List<String>? = null,
    @SerialName("is_paid")
    val isPaid: Boolean? = null,
    @SerialName("mblog_vip_type")
    val mblogVipType: Long? = null,
    val user: User? = null,
    val pid: Long? = null,
    val pidstr: String? = null,
    @SerialName("retweeted_status")
    val retweetedStatus: Status? = null,
    @SerialName("reposts_count")
    val repostsCount: JsonPrimitive? = null,
    @SerialName("comments_count")
    val commentsCount: Long? = null,
    @SerialName("reprint_cmt_count")
    val reprintCmtCount: Long? = null,
    @SerialName("attitudes_count")
    val attitudesCount: Long? = null,
    @SerialName("pending_approval_count")
    val pendingApprovalCount: Long? = null,
    val isLongText: Boolean? = null,
    @SerialName("show_mlevel")
    val showMlevel: Long? = null,
    @SerialName("darwin_tags")
    val darwinTags: JsonArray? = null,
    @SerialName("ad_marked")
    val adMarked: Boolean? = null,
    val mblogtype: Long? = null,
    @SerialName("item_category")
    val itemCategory: String? = null,
    val rid: String? = null,
    val mlevelSource: String? = null,
    val cardid: String? = null,
    @SerialName("number_display_strategy")
    val numberDisplayStrategy: NumberDisplayStrategy? = null,
    @SerialName("content_auth")
    val contentAuth: Long? = null,
    @SerialName("comment_manage_info")
    val commentManageInfo: CommentManageInfo? = null,
    @SerialName("repost_type")
    val repostType: Long? = null,
    @SerialName("pic_num")
    val picNum: Long? = null,
    @SerialName("hot_page")
    val hotPage: HotPage? = null,
    @SerialName("new_comment_style")
    val newCommentStyle: Long? = null,
    @SerialName("ab_switcher")
    val abSwitcher: Long? = null,
    val mlevel: Long? = null,
    @SerialName("region_name")
    val regionName: String? = null,
    @SerialName("region_opt")
    val regionOpt: Long? = null,
    @SerialName("raw_text")
    val rawText: String? = null,
    val bid: String? = null,
    val textLength: Long? = null,
    @SerialName("thumbnail_pic")
    val thumbnailPic: String? = null,
    @SerialName("bmiddle_pic")
    val bmiddlePic: String? = null,
    @SerialName("original_pic")
    val originalPic: String? = null,
    @SerialName("can_remark")
    val canRemark: Boolean? = null,
    @SerialName("safe_tags")
    val safeTags: Long? = null,
    val pics: JsonElement? = null,
    val picStatus: String? = null,
    @SerialName("attitude_dynamic_members_message")
    val attitudeDynamicMembersMessage: AttitudeDynamicMembersMessage? = null,
    @SerialName("page_info")
    val pageInfo: StatusPageInfo? = null,
    @SerialName("title")
    val title: Title? = null,
) {
    val picsList: List<StatusPic>?
        get() =
            when (pics) {
                is JsonArray -> {
                    JSON.decodeFromJsonElement(pics)
                }

                is JsonObject -> {
                    pics.values.map {
                        JSON.decodeFromJsonElement(it)
                    }
                }
                else -> {
                    null
                }
            }
}

@Serializable
internal data class Title(
    val text: String? = null,
)

@Serializable
internal data class AttitudeDynamicMembersMessage(
    @SerialName("user_grace_setting")
    val userGraceSetting: UserGraceSetting? = null,
    val bgimg: String? = null,
    val colorT: String? = null,
    val portrait: String? = null,
    @SerialName("media_url")
    val mediaURL: String? = null,
    @SerialName("default_media_url")
    val defaultMediaURL: String? = null,
    @SerialName("media_id")
    val mediaID: String? = null,
    val protocol: String? = null,
    @SerialName("scene_show_option")
    val sceneShowOption: Long? = null,
    val scheme: String? = null,
    val type: Long? = null,
)

@Serializable
internal data class UserGraceSetting(
    @SerialName("zh_CN")
    val zhCN: String? = null,
    @SerialName("zh_TW")
    val zhTW: String? = null,
    @SerialName("en_US")
    val enUS: String? = null,
)

@Serializable
internal data class CommentManageInfo(
    @SerialName("comment_permission_type")
    val commentPermissionType: Long? = null,
    @SerialName("approval_comment_type")
    val approvalCommentType: Long? = null,
    @SerialName("comment_sort_type")
    val commentSortType: Long? = null,
    @SerialName("ai_play_picture_type")
    val aiPlayPictureType: Long? = null,
)

@Serializable
internal data class HotPage(
    val fid: String? = null,
    @SerialName("feed_detail_type")
    val feedDetailType: Long? = null,
)

@Serializable
internal data class NumberDisplayStrategy(
    @SerialName("apply_scenario_flag")
    val applyScenarioFlag: Long? = null,
    @SerialName("display_text_min_number")
    val displayTextMinNumber: Long? = null,
    @SerialName("display_text")
    val displayText: String? = null,
)

@Serializable
internal data class StatusPageInfo(
    val type: String? = null,
    @SerialName("object_type")
    val objectType: Long? = null,
    @SerialName("page_pic")
    val pagePic: PagePic? = null,
    @SerialName("page_url")
    val pageURL: String? = null,
    @SerialName("page_title")
    val pageTitle: String? = null,
    val content1: String? = null,
    @SerialName("url_ori")
    val urlOri: String? = null,
    @SerialName("object_id")
    val objectID: String? = null,
    val title: String? = null,
    val content2: String? = null,
    @SerialName("video_orientation")
    val videoOrientation: String? = null,
    @SerialName("play_count")
    val playCount: String? = null,
    @SerialName("media_info")
    val mediaInfo: MediaInfo? = null,
    val urls: Urls? = null,
)

@Serializable
internal data class Urls(
    @SerialName("mp4_720p_mp4")
    val mp4720PMp4: String? = null,
    @SerialName("mp4_hd_mp4")
    val mp4HDMp4: String? = null,
    @SerialName("mp4_ld_mp4")
    val mp4LdMp4: String? = null,
)

@Serializable
internal data class MediaInfo(
    @SerialName("stream_url")
    val streamURL: String? = null,
    @SerialName("stream_url_hd")
    val streamURLHD: String? = null,
    val duration: Double? = null,
)

@Serializable
internal data class PagePic(
    val width: String? = null,
    val pid: String? = null,
    val source: String? = null,
    @SerialName("is_self_cover")
    val isSelfCover: String? = null,
    val type: String? = null,
    val url: String? = null,
    val height: String? = null,
    val scheme: String? = null,
)

@Serializable
internal data class StatusPic(
    val pid: String? = null,
    val url: String? = null,
    val size: String? = null,
//    val geo: StatusPicGeo? = null,
    val geo: JsonElement? = null,
    val large: Large? = null,
    val type: String? = null,
    val videoSrc: String? = null,
) {
    val geoValue: StatusPicGeo?
        get() =
            when (geo) {
                is JsonObject -> {
                    JSON.decodeFromJsonElement(geo)
                }
                else -> {
                    null
                }
            }
}

@Serializable
internal data class Large(
    val size: String? = null,
    val url: String? = null,
//    val geo: StatusPicGeo? = null,
    val geo: JsonElement? = null,
) {
    val geoValue: StatusPicGeo?
        get() =
            when (geo) {
                is JsonObject -> {
                    JSON.decodeFromJsonElement(geo)
                }
                else -> {
                    null
                }
            }
}

@Serializable
internal data class StatusPicGeo(
    val width: JsonPrimitive? = null,
    val height: JsonPrimitive? = null,
    val croped: Boolean? = null,
) {
    val widthValue: Float
        get() = width?.floatOrNull ?: 0f

    val heightValue: Float
        get() = height?.floatOrNull ?: 0f
}

@Serializable
internal data class User(
    val id: Long,
    @SerialName("screen_name")
    val screenName: String,
    @SerialName("profile_image_url")
    val profileImageURL: String? = null,
    @SerialName("profile_url")
    val profileURL: String? = null,
    @SerialName("statuses_count")
    val statusesCount: Long? = null,
    val verified: Boolean? = null,
    @SerialName("verified_type")
    val verifiedType: Long? = null,
    @SerialName("verified_type_ext")
    val verifiedTypeEXT: Long? = null,
    @SerialName("verified_reason")
    val verifiedReason: String? = null,
    @SerialName("close_blue_v")
    val closeBlueV: Boolean? = null,
    val description: String? = null,
    val gender: String? = null,
    val mbtype: Long? = null,
    val svip: Long? = null,
    val urank: Long? = null,
    val mbrank: Long? = null,
    @SerialName("follow_me")
    val followMe: Boolean? = null,
    @SerialName("following")
    val following_: JsonPrimitive? = null,
    @SerialName("follow_count")
    val followCount: Long? = null,
    @SerialName("followers_count")
    val followersCount: String? = null,
    @SerialName("followers_count_str")
    val followersCountStr: String? = null,
    @SerialName("cover_image_phone")
    val coverImagePhone: String? = null,
    @SerialName("avatar_hd")
    val avatarHD: String? = null,
    val like: Boolean? = null,
    @SerialName("like_me")
    val likeMe: Boolean? = null,
    val badge: Map<String, Long>? = null,
    @SerialName("special_follow")
    val specialFollow: Boolean? = null,
) {
    val following: Boolean
        get() = following_?.booleanOrNull ?: following_?.longOrNull?.let { it != 0L } ?: false
}

@Serializable
internal data class Visible(
    // type 0: all,
    // type 10: fans only,
    val type: Long? = null,
    @SerialName("list_id")
    val listID: Long? = null,
)

@Serializable
internal data class Config(
    val login: Boolean? = null,
    val st: String? = null,
    val uid: String? = null,
)

internal object VVODateSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val str = decoder.decodeString()
        return runCatching {
            Instant.parse(
                str,
                format =
                    Format {
                        // EEE MMM dd HH:mm:ss Z yyyy
                        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
                        char(' ')
                        monthName(MonthNames.ENGLISH_ABBREVIATED)
                        char(' ')
                        day(Padding.NONE)
                        char(' ')
                        hour()
                        char(':')
                        minute()
                        char(':')
                        second()
                        char(' ')
                        offset(UtcOffset.Formats.FOUR_DIGITS)
                        char(' ')
                        year()
                    },
            )
        }.getOrElse {
            Instant.parse(str)
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
internal data class StatusExtend(
    val ok: Long? = null,
    val longTextContent: String? = null,
    @SerialName("reposts_count")
    val repostsCount: Long? = null,
    @SerialName("comments_count")
    val commentsCount: Long? = null,
    @SerialName("attitudes_count")
    val attitudesCount: Long? = null,
)

@Serializable
internal data class StatusDetailItem(
    val status: Status? = null,
)

@Serializable
internal data class Comment(
    @SerialName("disable_reply")
    val disableReply: Long? = null,
    @SerialName("created_at")
    @Serializable(with = VVODateSerializer::class)
    val createdAt: Instant? = null,
    val id: String,
    val rootid: String? = null,
    val rootidstr: String? = null,
    @SerialName("floor_number")
    val floorNumber: Long? = null,
    val text: String? = null,
    val restrictOperate: Long? = null,
    val source: String? = null,
    val user: User? = null,
    val mid: String? = null,
    val status: Status? = null,
    val clevel: Long? = null,
    @SerialName("like_count")
    val likeCount: Long? = null,
    @SerialName("reply_count")
    val replyCount: Long? = null,
    val liked: Boolean? = null,
    val gid: Long? = null,
    @SerialName("match_ai_play_picture")
    val matchAIPlayPicture: Boolean? = null,
    val rid: String? = null,
    @SerialName("allow_follow")
    val allowFollow: Boolean? = null,
    @SerialName("feature_type")
    val featureType: Long? = null,
    @SerialName("cut_tail")
    val cutTail: Boolean? = null,
    @SerialName("feedback_menu_type")
    val feedbackMenuType: Long? = null,
    val bid: String? = null,
    val comments: JsonElement? = null,
    val pic: StatusPic? = null,
) {
    val commentList: List<Comment>?
        get() =
            if (comments is JsonArray) {
                JSON.decodeFromJsonElement(comments)
            } else {
                null
            }
}

@Serializable
internal data class Attitude(
    val id: Long? = null,
    val idStr: String? = null,
    @SerialName("created_at")
    @Serializable(with = VVODateSerializer::class)
    val createdAt: Instant? = null,
    val attitude: String? = null,
    @SerialName("attitude_mask")
    val attitudeMask: Long? = null,
    @SerialName("attitude_type")
    val attitudeType: Long? = null,
    @SerialName("last_attitude")
    val lastAttitude: String? = null,
    @SerialName("source_allowclick")
    val sourceAllowclick: Long? = null,
    @SerialName("source_type")
    val sourceType: Long? = null,
    val source: String? = null,
    val user: User? = null,
    val status: Status? = null,
    @SerialName("feedback_menu_type")
    val feedbackMenuType: Long? = null,
    @SerialName("feature_type")
    val featureType: Long? = null,
    @SerialName("cut_tail")
    val cutTail: Boolean? = null,
)

@Serializable
internal data class UploadResponse(
    @SerialName("pic_id")
    val picID: String? = null,
    @SerialName("thumbnail_pic")
    val thumbnailPic: String? = null,
    @SerialName("bmiddle_pic")
    val bmiddlePic: String? = null,
    @SerialName("original_pic")
    val originalPic: String? = null,
)

@Serializable
internal data class UnreadData(
    val cmt: Long? = null,
    val status: Long? = null,
    val follower: Long? = null,
    val dm: Long? = null,
    @SerialName("mention_cmt")
    val mentionCmt: Long? = null,
    @SerialName("mention_status")
    val mentionStatus: Long? = null,
    val attitude: Long? = null,
    val unreadmblog: Long? = null,
    val uid: String? = null,
    val bi: Long? = null,
    val newfans: Long? = null,
    val unreadmsg: Map<String, Long>? = null,
//    val group: Any? = null,
    val notice: Long? = null,
    val photo: Long? = null,
    val msgbox: Long? = null,
)
