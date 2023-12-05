package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAdListRequest
import dev.dimension.flare.data.network.misskey.api.model.App
import dev.dimension.flare.data.network.misskey.api.model.Blocking
import dev.dimension.flare.data.network.misskey.api.model.BlockingListRequest
import dev.dimension.flare.data.network.misskey.api.model.Clip
import dev.dimension.flare.data.network.misskey.api.model.ClipsAddNoteRequest
import dev.dimension.flare.data.network.misskey.api.model.ClipsNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.EndpointRequest
import dev.dimension.flare.data.network.misskey.api.model.Flash
import dev.dimension.flare.data.network.misskey.api.model.FlashMyLikes200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.GalleryPost
import dev.dimension.flare.data.network.misskey.api.model.IGalleryLikes200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.IGetWordMutedNotesCount200Response
import dev.dimension.flare.data.network.misskey.api.model.INotificationsRequest
import dev.dimension.flare.data.network.misskey.api.model.IPageLikes200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.IReadAnnouncementRequest
import dev.dimension.flare.data.network.misskey.api.model.IUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.MeDetailed
import dev.dimension.flare.data.network.misskey.api.model.MuteCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.Muting
import dev.dimension.flare.data.network.misskey.api.model.MyAppsRequest
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.NoteFavorite
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.data.network.misskey.api.model.Page
import dev.dimension.flare.data.network.misskey.api.model.RenoteMuting
import dev.dimension.flare.data.network.misskey.api.model.SwRegister200Response
import dev.dimension.flare.data.network.misskey.api.model.SwRegisterRequest
import dev.dimension.flare.data.network.misskey.api.model.SwShowRegistration200Response
import dev.dimension.flare.data.network.misskey.api.model.SwUpdateRegistration200Response
import dev.dimension.flare.data.network.misskey.api.model.SwUpdateRegistrationRequest
import dev.dimension.flare.data.network.misskey.api.model.UserDetailedNotMe
import dev.dimension.flare.data.network.misskey.api.model.UsersUpdateMemoRequest

interface AccountApi {
    /**
     * blocking/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:blocks*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [UserDetailedNotMe]
     */
    @POST("blocking/create")
    suspend fun blockingCreate(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<UserDetailedNotMe>

    /**
     * blocking/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:blocks*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [UserDetailedNotMe]
     */
    @POST("blocking/delete")
    suspend fun blockingDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<UserDetailedNotMe>

    /**
     * blocking/list
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:blocks*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param blockingListRequest * @return [kotlin.collections.List<Blocking>]
     */
    @POST("blocking/list")
    suspend fun blockingList(
        @Body blockingListRequest: BlockingListRequest,
    ): Response<kotlin.collections.List<Blocking>>

    /**
     * clips/add-note
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param clipsAddNoteRequest * @return [Unit]
     */
    @POST("clips/add-note")
    suspend fun clipsAddNote(
        @Body clipsAddNoteRequest: ClipsAddNoteRequest,
    ): Response<Unit>

    /**
     * clips/my-favorites
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:clip-favorite*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<Clip>]
     */
    @POST("clips/my-favorites")
    suspend fun clipsMyFavorites(
        @Body body: kotlin.Any,
    ): Response<kotlin.collections.List<Clip>>

    /**
     * clips/notes
     * No description provided.  **Credential required**: *No* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param clipsNotesRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("clips/notes")
    suspend fun clipsNotes(
        @Body clipsNotesRequest: ClipsNotesRequest,
    ): Response<kotlin.collections.List<Note>>

    /**
     * clips/remove-note
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param clipsAddNoteRequest * @return [Unit]
     */
    @POST("clips/remove-note")
    suspend fun clipsRemoveNote(
        @Body clipsAddNoteRequest: ClipsAddNoteRequest,
    ): Response<Unit>

    /**
     * flash/my
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:flash*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<Flash>]
     */
    @POST("flash/my")
    suspend fun flashMy(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<Flash>>

    /**
     * flash/my-likes
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:flash-likes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<FlashMyLikes200ResponseInner>]
     */
    @POST("flash/my-likes")
    suspend fun flashMyLikes(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<FlashMyLikes200ResponseInner>>

    /**
     * i
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [MeDetailed]
     */
    @POST("i")
    suspend fun i(
        @Body body: kotlin.Any,
    ): Response<MeDetailed>

    /**
     * i/favorites
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:favorites*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<NoteFavorite>]
     */
    @POST("i/favorites")
    suspend fun iFavorites(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<NoteFavorite>>

    /**
     * i/gallery/likes
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:gallery-likes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<IGalleryLikes200ResponseInner>]
     */
    @POST("i/gallery/likes")
    suspend fun iGalleryLikes(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<IGalleryLikes200ResponseInner>>

    /**
     * i/gallery/posts
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:gallery*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<GalleryPost>]
     */
    @POST("i/gallery/posts")
    suspend fun iGalleryPosts(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<GalleryPost>>

    /**
     * i/get-word-muted-notes-count
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [IGetWordMutedNotesCount200Response]
     */
    @POST("i/get-word-muted-notes-count")
    suspend fun iGetWordMutedNotesCount(
        @Body body: kotlin.Any,
    ): Response<IGetWordMutedNotesCount200Response>

    /**
     * i/notifications
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:notifications*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param inotificationsRequest * @return [kotlin.collections.List<Notification>]
     */
    @POST("i/notifications-grouped")
    suspend fun iNotifications(
        @Body inotificationsRequest: INotificationsRequest,
    ): Response<kotlin.collections.List<Notification>>

    /**
     * i/page-likes
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:page-likes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<IPageLikes200ResponseInner>]
     */
    @POST("i/page-likes")
    suspend fun iPageLikes(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<IPageLikes200ResponseInner>>

    /**
     * i/pages
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:pages*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<Page>]
     */
    @POST("i/pages")
    suspend fun iPages(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<Page>>

    /**
     * i/pin
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [MeDetailed]
     */
    @POST("i/pin")
    suspend fun iPin(
        @Body ipinRequest: IPinRequest,
    ): Response<MeDetailed>

    /**
     * i/read-all-unread-notes
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [Unit]
     */
    @POST("i/read-all-unread-notes")
    suspend fun iReadAllUnreadNotes(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * i/read-announcement
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ireadAnnouncementRequest * @return [Unit]
     */
    @POST("i/read-announcement")
    suspend fun iReadAnnouncement(
        @Body ireadAnnouncementRequest: IReadAnnouncementRequest,
    ): Response<Unit>

    /**
     * i/unpin
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [MeDetailed]
     */
    @POST("i/unpin")
    suspend fun iUnpin(
        @Body ipinRequest: IPinRequest,
    ): Response<MeDetailed>

    /**
     * i/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param iupdateRequest * @return [MeDetailed]
     */
    @POST("i/update")
    suspend fun iUpdate(
        @Body iupdateRequest: IUpdateRequest,
    ): Response<MeDetailed>

    /**
     * mute/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:mutes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param muteCreateRequest * @return [Unit]
     */
    @POST("mute/create")
    suspend fun muteCreate(
        @Body muteCreateRequest: MuteCreateRequest,
    ): Response<Unit>

    /**
     * mute/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:mutes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [Unit]
     */
    @POST("mute/delete")
    suspend fun muteDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * mute/list
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:mutes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param blockingListRequest * @return [kotlin.collections.List<Muting>]
     */
    @POST("mute/list")
    suspend fun muteList(
        @Body blockingListRequest: BlockingListRequest,
    ): Response<kotlin.collections.List<Muting>>

    /**
     * my/apps
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param myAppsRequest * @return [kotlin.collections.List<App>]
     */
    @POST("my/apps")
    suspend fun myApps(
        @Body myAppsRequest: MyAppsRequest,
    ): Response<kotlin.collections.List<App>>

    /**
     * renote-mute/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:mutes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [Unit]
     */
    @POST("renote-mute/create")
    suspend fun renoteMuteCreate(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * renote-mute/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:mutes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [Unit]
     */
    @POST("renote-mute/delete")
    suspend fun renoteMuteDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * renote-mute/list
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:mutes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param blockingListRequest * @return [kotlin.collections.List<RenoteMuting>]
     */
    @POST("renote-mute/list")
    suspend fun renoteMuteList(
        @Body blockingListRequest: BlockingListRequest,
    ): Response<kotlin.collections.List<RenoteMuting>>

    /**
     * sw/register
     * Register to receive push notifications.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param swRegisterRequest * @return [SwRegister200Response]
     */
    @POST("sw/register")
    suspend fun swRegister(
        @Body swRegisterRequest: SwRegisterRequest,
    ): Response<SwRegister200Response>

    /**
     * sw/show-registration
     * Check push notification registration exists.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param endpointRequest * @return [SwShowRegistration200Response]
     */
    @POST("sw/show-registration")
    suspend fun swShowRegistration(
        @Body endpointRequest: EndpointRequest,
    ): Response<SwShowRegistration200Response>

    /**
     * sw/unregister
     * Unregister from receiving push notifications.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param endpointRequest * @return [Unit]
     */
    @POST("sw/unregister")
    suspend fun swUnregister(
        @Body endpointRequest: EndpointRequest,
    ): Response<Unit>

    /**
     * sw/update-registration
     * Update push notification registration.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param swUpdateRegistrationRequest * @return [SwUpdateRegistration200Response]
     */
    @POST("sw/update-registration")
    suspend fun swUpdateRegistration(
        @Body swUpdateRegistrationRequest: SwUpdateRegistrationRequest,
    ): Response<SwUpdateRegistration200Response>

    /**
     * users/update-memo
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersUpdateMemoRequest * @return [Unit]
     */
    @POST("users/update-memo")
    suspend fun usersUpdateMemo(
        @Body usersUpdateMemoRequest: UsersUpdateMemoRequest,
    ): Response<Unit>
}
