package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.DmNew2Response
import dev.dimension.flare.data.network.xqt.model.PostDmNew2Request

internal interface VDmPostJsonPostApi {
    /**
     * POST 1.1/dm/new2.json
     *
     * post new direct message
     * Responses:
     *  - 200: Successful response
     *
     * @param postDmNew2Request
     * @param ext Comma-separated list of extensions (optional, default to "mediaColor,altText,mediaStats,highlightedLabel,voiceInfo,birdwatchPivot,superFollowMetadata,unmentionInfo,editControl,article")
     * @param includeExtAltText  (optional, default to true)
     * @param includeExtLimitedActionResults  (optional, default to true)
     * @param includeReplyCount  (optional, default to 1)
     * @param tweetMode  (optional, default to "extended")
     * @param includeExtViews  (optional, default to true)
     * @param includeGroups  (optional, default to true)
     * @param includeInboxTimelines  (optional, default to true)
     * @param includeExtMediaColor  (optional, default to true)
     * @param supportsReactions  (optional, default to true)
     * @param supportsEdit  (optional, default to true)
     * @return [DmNew2Response]
     */
    @POST("1.1/dm/new2.json")
    public suspend fun postDmNew2(
        @Body postDmNew2Request: PostDmNew2Request,
        @Query("ext") ext: kotlin.String? =
            "mediaColor,altText,mediaStats,highlightedLabel," +
                "voiceInfo,birdwatchPivot,superFollowMetadata,unmentionInfo,editControl,article",
        @Query("include_ext_alt_text") includeExtAltText: kotlin.Boolean? = true,
        @Query("include_ext_limited_action_results") includeExtLimitedActionResults: kotlin.Boolean? = true,
        @Query("include_reply_count") includeReplyCount: kotlin.Int? = 1,
        @Query("tweet_mode") tweetMode: kotlin.String? = "extended",
        @Query("include_ext_views") includeExtViews: kotlin.Boolean? = true,
        @Query("include_groups") includeGroups: kotlin.Boolean? = true,
        @Query("include_inbox_timelines") includeInboxTimelines: kotlin.Boolean? = true,
        @Query("include_ext_media_color") includeExtMediaColor: kotlin.Boolean? = true,
        @Query("supports_reactions") supportsReactions: kotlin.Boolean? = true,
        @Query("supports_edit") supportsEdit: kotlin.Boolean? = true,
        @Header("Content-Type") contentType: String = "application/json",
    ): DmNew2Response
}
