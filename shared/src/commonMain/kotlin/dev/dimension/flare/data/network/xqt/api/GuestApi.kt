package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.AccountSettingsResponse
import dev.dimension.flare.data.network.xqt.model.ActivateResponse

interface GuestApi {
    @POST("1.1/guest/activate.json")
    suspend fun activate(): ActivateResponse

    @GET("1.1/account/settings.json")
    suspend fun getAccountSettings(
        @Query("include_mention_filter") includeMentionFilter: Boolean = true,
        @Query("include_nsfw_user_flag") includeNsfwUserFlag: Boolean = true,
        @Query("include_nsfw_admin_flag") includeNsfwAdminFlag: Boolean = true,
        @Query("include_ranked_timeline") includeRankedTimeline: Boolean = true,
        @Query("include_alt_text_compose") includeAltTextCompose: Boolean = true,
        @Query("ext") ext: String = "ssoConnections",
        @Query("include_country_code") includeCountryCode: Boolean = true,
        @Query("include_ext_dm_nsfw_media_filter") includeExtDmNsfwMediaFilter: Boolean = true,
        @Query("include_ext_sharing_audiospaces_listening_data_with_followers") includeExtSharingAudiospacesListeningDataWithFollowers:
            Boolean = true,
    ): AccountSettingsResponse
}
