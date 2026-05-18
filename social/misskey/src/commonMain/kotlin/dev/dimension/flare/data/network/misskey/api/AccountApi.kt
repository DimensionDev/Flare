package dev.dimension.flare.data.network.misskey.api

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

public interface AccountApi {
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
    public suspend fun blockingCreate(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): UserDetailedNotMe

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
    public suspend fun blockingDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): UserDetailedNotMe

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
    public suspend fun blockingList(
        @Body blockingListRequest: BlockingListRequest,
    ): kotlin.collections.List<Blocking>

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
    public suspend fun clipsAddNote(
        @Body clipsAddNoteRequest: ClipsAddNoteRequest,
    ): Unit

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
    public suspend fun clipsMyFavorites(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<Clip>

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
    public suspend fun clipsNotes(
        @Body clipsNotesRequest: ClipsNotesRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun clipsRemoveNote(
        @Body clipsAddNoteRequest: ClipsAddNoteRequest,
    ): Unit

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
    public suspend fun flashMy(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<Flash>

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
    public suspend fun flashMyLikes(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<FlashMyLikes200ResponseInner>

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
    public suspend fun i(
        @Body body: kotlin.Any,
    ): MeDetailed

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
    public suspend fun iFavorites(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<NoteFavorite>

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
    public suspend fun iGalleryLikes(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<IGalleryLikes200ResponseInner>

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
    public suspend fun iGalleryPosts(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<GalleryPost>

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
    public suspend fun iGetWordMutedNotesCount(
        @Body body: kotlin.Any,
    ): IGetWordMutedNotesCount200Response

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
    @POST("i/notifications")
    public suspend fun iNotifications(
        @Body inotificationsRequest: INotificationsRequest,
    ): kotlin.collections.List<Notification>

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
    public suspend fun iPageLikes(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<IPageLikes200ResponseInner>

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
    public suspend fun iPages(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<Page>

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
    public suspend fun iPin(
        @Body ipinRequest: IPinRequest,
    ): MeDetailed

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
    public suspend fun iReadAllUnreadNotes(
        @Body body: kotlin.Any,
    ): Unit

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
    public suspend fun iReadAnnouncement(
        @Body ireadAnnouncementRequest: IReadAnnouncementRequest,
    ): Unit

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
    public suspend fun iUnpin(
        @Body ipinRequest: IPinRequest,
    ): MeDetailed

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
    public suspend fun iUpdate(
        @Body iupdateRequest: IUpdateRequest,
    ): MeDetailed

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
    public suspend fun muteCreate(
        @Body muteCreateRequest: MuteCreateRequest,
    ): Unit

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
    public suspend fun muteDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun muteList(
        @Body blockingListRequest: BlockingListRequest,
    ): kotlin.collections.List<Muting>

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
    public suspend fun myApps(
        @Body myAppsRequest: MyAppsRequest,
    ): kotlin.collections.List<App>

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
    public suspend fun renoteMuteCreate(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun renoteMuteDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun renoteMuteList(
        @Body blockingListRequest: BlockingListRequest,
    ): kotlin.collections.List<RenoteMuting>

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
    public suspend fun swRegister(
        @Body swRegisterRequest: SwRegisterRequest,
    ): SwRegister200Response

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
    public suspend fun swShowRegistration(
        @Body endpointRequest: EndpointRequest,
    ): SwShowRegistration200Response

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
    public suspend fun swUnregister(
        @Body endpointRequest: EndpointRequest,
    ): Unit

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
    public suspend fun swUpdateRegistration(
        @Body swUpdateRegistrationRequest: SwUpdateRegistrationRequest,
    ): SwUpdateRegistration200Response

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
    public suspend fun usersUpdateMemo(
        @Body usersUpdateMemoRequest: UsersUpdateMemoRequest,
    ): Unit
}
