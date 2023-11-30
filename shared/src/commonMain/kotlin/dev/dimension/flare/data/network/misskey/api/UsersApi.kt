package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Clip
import dev.dimension.flare.data.network.misskey.api.model.EmailAddressAvailable200Response
import dev.dimension.flare.data.network.misskey.api.model.EmailAddressAvailableRequest
import dev.dimension.flare.data.network.misskey.api.model.Following
import dev.dimension.flare.data.network.misskey.api.model.GalleryPost
import dev.dimension.flare.data.network.misskey.api.model.MyAppsRequest
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.NoteReaction
import dev.dimension.flare.data.network.misskey.api.model.Page
import dev.dimension.flare.data.network.misskey.api.model.PinnedUsersRequest
import dev.dimension.flare.data.network.misskey.api.model.User
import dev.dimension.flare.data.network.misskey.api.model.UserDetailed
import dev.dimension.flare.data.network.misskey.api.model.UsernameAvailable200Response
import dev.dimension.flare.data.network.misskey.api.model.UsernameAvailableRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersClipsRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersFollowersRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersGetFrequentlyRepliedUsers200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.UsersGetFrequentlyRepliedUsersRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersReactionsRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersRelation200Response
import dev.dimension.flare.data.network.misskey.api.model.UsersRelationRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersReportAbuseRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersSearchByUsernameAndHostRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersSearchRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersShow200Response
import dev.dimension.flare.data.network.misskey.api.model.UsersShowRequest

interface UsersApi {
    /**
     * email-address/available
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param emailAddressAvailableRequest * @return [EmailAddressAvailable200Response]
     */
    @POST("email-address/available")
    suspend fun emailAddressAvailable(
        @Body emailAddressAvailableRequest: EmailAddressAvailableRequest,
    ): Response<EmailAddressAvailable200Response>

    /**
     * pinned-users
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<UserDetailed>]
     */
    @POST("pinned-users")
    suspend fun pinnedUsers(
        @Body body: PinnedUsersRequest,
    ): Response<kotlin.collections.List<UserDetailed>>

    /**
     * retention
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.Any]
     */
    @POST("retention")
    suspend fun retention(
        @Body body: kotlin.Any,
    ): Response<kotlin.Any>

    /**
     * username/available
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usernameAvailableRequest * @return [UsernameAvailable200Response]
     */
    @POST("username/available")
    suspend fun usernameAvailable(
        @Body usernameAvailableRequest: UsernameAvailableRequest,
    ): Response<UsernameAvailable200Response>

    /**
     * users
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersRequest * @return [kotlin.collections.List<UserDetailed>]
     */
    @POST("users")
    suspend fun users(
        @Body usersRequest: UsersRequest,
    ): Response<kotlin.collections.List<UserDetailed>>

    /**
     * users/clips
     * Show all clips this user owns.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersClipsRequest * @return [kotlin.collections.List<Clip>]
     */
    @POST("users/clips")
    suspend fun usersClips(
        @Body usersClipsRequest: UsersClipsRequest,
    ): Response<kotlin.collections.List<Clip>>

    /**
     * users/followers
     * Show everyone that follows this user.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersFollowersRequest * @return [kotlin.collections.List<Following>]
     */
    @POST("users/followers")
    suspend fun usersFollowers(
        @Body usersFollowersRequest: UsersFollowersRequest,
    ): Response<kotlin.collections.List<Following>>

    /**
     * users/following
     * Show everyone that this user is following.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersFollowersRequest * @return [kotlin.collections.List<Following>]
     */
    @POST("users/following")
    suspend fun usersFollowing(
        @Body usersFollowersRequest: UsersFollowersRequest,
    ): Response<kotlin.collections.List<Following>>

    /**
     * users/gallery/posts
     * Show all gallery posts by the given user.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersClipsRequest * @return [kotlin.collections.List<GalleryPost>]
     */
    @POST("users/gallery/posts")
    suspend fun usersGalleryPosts(
        @Body usersClipsRequest: UsersClipsRequest,
    ): Response<kotlin.collections.List<GalleryPost>>

    /**
     * users/get-frequently-replied-users
     * Get a list of other users that the specified user frequently replies to.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersGetFrequentlyRepliedUsersRequest * @return [kotlin.collections.List<UsersGetFrequentlyRepliedUsers200ResponseInner>]
     */
    @POST("users/get-frequently-replied-users")
    suspend fun usersGetFrequentlyRepliedUsers(
        @Body usersGetFrequentlyRepliedUsersRequest: UsersGetFrequentlyRepliedUsersRequest,
    ): Response<kotlin.collections.List<UsersGetFrequentlyRepliedUsers200ResponseInner>>

    /**
     * users/notes
     * Show all notes that this user created.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersNotesRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("users/notes")
    suspend fun usersNotes(
        @Body usersNotesRequest: UsersNotesRequest,
    ): Response<kotlin.collections.List<Note>>

    /**
     * users/pages
     * Show all pages this user created.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersClipsRequest * @return [kotlin.collections.List<Page>]
     */
    @POST("users/pages")
    suspend fun usersPages(
        @Body usersClipsRequest: UsersClipsRequest,
    ): Response<kotlin.collections.List<Page>>

    /**
     * users/reactions
     * Show all reactions this user made.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersReactionsRequest * @return [kotlin.collections.List<NoteReaction>]
     */
    @POST("users/reactions")
    suspend fun usersReactions(
        @Body usersReactionsRequest: UsersReactionsRequest,
    ): Response<kotlin.collections.List<NoteReaction>>

    /**
     * users/recommendation
     * Show users that the authenticated user might be interested to follow.  **Credential required**: *Yes* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param myAppsRequest * @return [kotlin.collections.List<UserDetailed>]
     */
    @POST("users/recommendation")
    suspend fun usersRecommendation(
        @Body myAppsRequest: MyAppsRequest,
    ): Response<kotlin.collections.List<UserDetailed>>

    /**
     * users/relation
     * Show the different kinds of relations between the authenticated user and the specified user(s).  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersRelationRequest * @return [UsersRelation200Response]
     */
    @POST("users/relation")
    suspend fun usersRelation(
        @Body usersRelationRequest: UsersRelationRequest,
    ): Response<UsersRelation200Response>

    /**
     * users/report-abuse
     * File a report.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersReportAbuseRequest * @return [Unit]
     */
    @POST("users/report-abuse")
    suspend fun usersReportAbuse(
        @Body usersReportAbuseRequest: UsersReportAbuseRequest,
    ): Response<Unit>

    /**
     * users/search
     * Search for users.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersSearchRequest * @return [kotlin.collections.List<User>]
     */
    @POST("users/search")
    suspend fun usersSearch(
        @Body usersSearchRequest: UsersSearchRequest,
    ): Response<kotlin.collections.List<User>>

    /**
     * users/search-by-username-and-host
     * Search for a user by username and/or host.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersSearchByUsernameAndHostRequest * @return [kotlin.collections.List<User>]
     */
    @POST("users/search-by-username-and-host")
    suspend fun usersSearchByUsernameAndHost(
        @Body usersSearchByUsernameAndHostRequest: UsersSearchByUsernameAndHostRequest,
    ): Response<kotlin.collections.List<User>>

    /**
     * users/show
     * Show the properties of a user.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersShowRequest * @return [UsersShow200Response]
     */
    @POST("users/show")
    suspend fun usersShow(
        @Body usersShowRequest: UsersShowRequest,
    ): Response<User>
}
