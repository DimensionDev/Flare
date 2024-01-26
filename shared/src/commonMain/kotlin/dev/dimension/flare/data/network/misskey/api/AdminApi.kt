package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseReportResolverCreate200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseReportResolverCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseUserReports200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseUserReportsRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAdCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAdDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAdListRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAdUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAnnouncementsCreate200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminAnnouncementsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAnnouncementsList200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AdminAnnouncementsListRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAnnouncementsUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminDriveFilesRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminDriveShowFile200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminDriveShowFileRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiAddAliasesBulkRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiAddRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiCopy200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiCopyRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiDeleteBulkRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiList200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiListRemote200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiListRemoteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiListRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiSetCategoryBulkRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiSetLicenseBulkRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminEmojiUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminFederationDeleteAllFilesRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminFederationUpdateInstanceRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminInviteCreate200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AdminInviteCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminInviteListRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminPromoCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminQueueDeliverDelayed200ResponseInnerInner
import dev.dimension.flare.data.network.misskey.api.model.AdminQueuePromoteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminQueueStats200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminRelaysAdd200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminRelaysAddRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminResetPassword200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminResolveAbuseUserReportRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesAssignRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesUnassignRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesUpdateDefaultPoliciesRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesUsersRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminSendEmailRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminServerInfo200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminShowModerationLogs200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.AdminShowUsersRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminUpdateMetaRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminUpdateUserNoteRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.User
import dev.dimension.flare.data.network.misskey.api.model.UserDetailed

internal interface AdminApi {
    /**
     * admin/abuse-report-resolver/create
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAbuseReportResolverCreateRequest * @return [AdminAbuseReportResolverCreate200Response]
     */
    @POST("admin/abuse-report-resolver/create")
    suspend fun adminAbuseReportResolverCreate(
        @Body adminAbuseReportResolverCreateRequest: AdminAbuseReportResolverCreateRequest,
    ): Response<AdminAbuseReportResolverCreate200Response>

    /**
     * admin/abuse-user-reports
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAbuseUserReportsRequest * @return [kotlin.collections.List<AdminAbuseUserReports200ResponseInner>]
     */
    @POST("admin/abuse-user-reports")
    suspend fun adminAbuseUserReports(
        @Body adminAbuseUserReportsRequest: AdminAbuseUserReportsRequest,
    ): Response<kotlin.collections.List<AdminAbuseUserReports200ResponseInner>>

    /**
     * admin/accounts/create
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsCreateRequest * @return [User]
     */
    @POST("admin/accounts/create")
    suspend fun adminAccountsCreate(
        @Body adminAccountsCreateRequest: AdminAccountsCreateRequest,
    ): Response<User>

    /**
     * admin/accounts/delete
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/accounts/delete")
    suspend fun adminAccountsDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * admin/ad/create
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdCreateRequest * @return [Unit]
     */
    @POST("admin/ad/create")
    suspend fun adminAdCreate(
        @Body adminAdCreateRequest: AdminAdCreateRequest,
    ): Response<Unit>

    /**
     * admin/ad/delete
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdDeleteRequest * @return [Unit]
     */
    @POST("admin/ad/delete")
    suspend fun adminAdDelete(
        @Body adminAdDeleteRequest: AdminAdDeleteRequest,
    ): Response<Unit>

    /**
     * admin/ad/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [Unit]
     */
    @POST("admin/ad/list")
    suspend fun adminAdList(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<Unit>

    /**
     * admin/ad/update
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdUpdateRequest * @return [Unit]
     */
    @POST("admin/ad/update")
    suspend fun adminAdUpdate(
        @Body adminAdUpdateRequest: AdminAdUpdateRequest,
    ): Response<Unit>

    /**
     * admin/announcements/create
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAnnouncementsCreateRequest * @return [AdminAnnouncementsCreate200Response]
     */
    @POST("admin/announcements/create")
    suspend fun adminAnnouncementsCreate(
        @Body adminAnnouncementsCreateRequest: AdminAnnouncementsCreateRequest,
    ): Response<AdminAnnouncementsCreate200Response>

    /**
     * admin/announcements/delete
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdDeleteRequest * @return [Unit]
     */
    @POST("admin/announcements/delete")
    suspend fun adminAnnouncementsDelete(
        @Body adminAdDeleteRequest: AdminAdDeleteRequest,
    ): Response<Unit>

    /**
     * admin/announcements/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAnnouncementsListRequest * @return [kotlin.collections.List<AdminAnnouncementsList200ResponseInner>]
     */
    @POST("admin/announcements/list")
    suspend fun adminAnnouncementsList(
        @Body adminAnnouncementsListRequest: AdminAnnouncementsListRequest,
    ): Response<kotlin.collections.List<AdminAnnouncementsList200ResponseInner>>

    /**
     * admin/announcements/update
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAnnouncementsUpdateRequest * @return [Unit]
     */
    @POST("admin/announcements/update")
    suspend fun adminAnnouncementsUpdate(
        @Body adminAnnouncementsUpdateRequest: AdminAnnouncementsUpdateRequest,
    ): Response<Unit>

    /**
     * admin/delete-account
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [kotlin.Any]
     */
    @POST("admin/delete-account")
    suspend fun adminDeleteAccount(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<kotlin.Any>

    /**
     * admin/delete-all-files-of-a-user
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/delete-all-files-of-a-user")
    suspend fun adminDeleteAllFilesOfAUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * admin/drive/clean-remote-files
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/drive/clean-remote-files")
    suspend fun adminDriveCleanRemoteFiles(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * admin/drive/cleanup
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/drive/cleanup")
    suspend fun adminDriveCleanup(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * admin/drive/files
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminDriveFilesRequest * @return [kotlin.collections.List<DriveFile>]
     */
    @POST("admin/drive/files")
    suspend fun adminDriveFiles(
        @Body adminDriveFilesRequest: AdminDriveFilesRequest,
    ): Response<kotlin.collections.List<DriveFile>>

    /**
     * admin/drive/show-file
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminDriveShowFileRequest * @return [AdminDriveShowFile200Response]
     */
    @POST("admin/drive/show-file")
    suspend fun adminDriveShowFile(
        @Body adminDriveShowFileRequest: AdminDriveShowFileRequest,
    ): Response<AdminDriveShowFile200Response>

    /**
     * admin/emoji/add
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiAddRequest * @return [Unit]
     */
    @POST("admin/emoji/add")
    suspend fun adminEmojiAdd(
        @Body adminEmojiAddRequest: AdminEmojiAddRequest,
    ): Response<Unit>

    /**
     * admin/emoji/add-aliases-bulk
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiAddAliasesBulkRequest * @return [Unit]
     */
    @POST("admin/emoji/add-aliases-bulk")
    suspend fun adminEmojiAddAliasesBulk(
        @Body adminEmojiAddAliasesBulkRequest: AdminEmojiAddAliasesBulkRequest,
    ): Response<Unit>

    /**
     * admin/emoji/copy
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiCopyRequest * @return [AdminEmojiCopy200Response]
     */
    @POST("admin/emoji/copy")
    suspend fun adminEmojiCopy(
        @Body adminEmojiCopyRequest: AdminEmojiCopyRequest,
    ): Response<AdminEmojiCopy200Response>

    /**
     * admin/emoji/delete
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdDeleteRequest * @return [Unit]
     */
    @POST("admin/emoji/delete")
    suspend fun adminEmojiDelete(
        @Body adminAdDeleteRequest: AdminAdDeleteRequest,
    ): Response<Unit>

    /**
     * admin/emoji/delete-bulk
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiDeleteBulkRequest * @return [Unit]
     */
    @POST("admin/emoji/delete-bulk")
    suspend fun adminEmojiDeleteBulk(
        @Body adminEmojiDeleteBulkRequest: AdminEmojiDeleteBulkRequest,
    ): Response<Unit>

    /**
     * admin/emoji/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiListRequest * @return [kotlin.collections.List<AdminEmojiList200ResponseInner>]
     */
    @POST("admin/emoji/list")
    suspend fun adminEmojiList(
        @Body adminEmojiListRequest: AdminEmojiListRequest,
    ): Response<kotlin.collections.List<AdminEmojiList200ResponseInner>>

    /**
     * admin/emoji/list-remote
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiListRemoteRequest * @return [kotlin.collections.List<AdminEmojiListRemote200ResponseInner>]
     */
    @POST("admin/emoji/list-remote")
    suspend fun adminEmojiListRemote(
        @Body adminEmojiListRemoteRequest: AdminEmojiListRemoteRequest,
    ): Response<kotlin.collections.List<AdminEmojiListRemote200ResponseInner>>

    /**
     * admin/emoji/remove-aliases-bulk
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiAddAliasesBulkRequest * @return [Unit]
     */
    @POST("admin/emoji/remove-aliases-bulk")
    suspend fun adminEmojiRemoveAliasesBulk(
        @Body adminEmojiAddAliasesBulkRequest: AdminEmojiAddAliasesBulkRequest,
    ): Response<Unit>

    /**
     * admin/emoji/set-aliases-bulk
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiAddAliasesBulkRequest * @return [Unit]
     */
    @POST("admin/emoji/set-aliases-bulk")
    suspend fun adminEmojiSetAliasesBulk(
        @Body adminEmojiAddAliasesBulkRequest: AdminEmojiAddAliasesBulkRequest,
    ): Response<Unit>

    /**
     * admin/emoji/set-category-bulk
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiSetCategoryBulkRequest * @return [Unit]
     */
    @POST("admin/emoji/set-category-bulk")
    suspend fun adminEmojiSetCategoryBulk(
        @Body adminEmojiSetCategoryBulkRequest: AdminEmojiSetCategoryBulkRequest,
    ): Response<Unit>

    /**
     * admin/emoji/set-license-bulk
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiSetLicenseBulkRequest * @return [Unit]
     */
    @POST("admin/emoji/set-license-bulk")
    suspend fun adminEmojiSetLicenseBulk(
        @Body adminEmojiSetLicenseBulkRequest: AdminEmojiSetLicenseBulkRequest,
    ): Response<Unit>

    /**
     * admin/emoji/update
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminEmojiUpdateRequest * @return [Unit]
     */
    @POST("admin/emoji/update")
    suspend fun adminEmojiUpdate(
        @Body adminEmojiUpdateRequest: AdminEmojiUpdateRequest,
    ): Response<Unit>

    /**
     * admin/federation/delete-all-files
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminFederationDeleteAllFilesRequest * @return [Unit]
     */
    @POST("admin/federation/delete-all-files")
    suspend fun adminFederationDeleteAllFiles(
        @Body adminFederationDeleteAllFilesRequest: AdminFederationDeleteAllFilesRequest,
    ): Response<Unit>

    /**
     * admin/federation/refresh-remote-instance-metadata
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminFederationDeleteAllFilesRequest * @return [Unit]
     */
    @POST("admin/federation/refresh-remote-instance-metadata")
    suspend fun adminFederationRefreshRemoteInstanceMetadata(
        @Body adminFederationDeleteAllFilesRequest: AdminFederationDeleteAllFilesRequest,
    ): Response<Unit>

    /**
     * admin/federation/remove-all-following
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminFederationDeleteAllFilesRequest * @return [Unit]
     */
    @POST("admin/federation/remove-all-following")
    suspend fun adminFederationRemoveAllFollowing(
        @Body adminFederationDeleteAllFilesRequest: AdminFederationDeleteAllFilesRequest,
    ): Response<Unit>

    /**
     * admin/federation/update-instance
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminFederationUpdateInstanceRequest * @return [Unit]
     */
    @POST("admin/federation/update-instance")
    suspend fun adminFederationUpdateInstance(
        @Body adminFederationUpdateInstanceRequest: AdminFederationUpdateInstanceRequest,
    ): Response<Unit>

    /**
     * admin/get-index-stats
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/get-index-stats")
    suspend fun adminGetIndexStats(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * admin/get-table-stats
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/get-table-stats")
    suspend fun adminGetTableStats(
        @Body body: kotlin.Any,
    ): Response<kotlin.Any>

    /**
     * admin/get-user-ips
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/get-user-ips")
    suspend fun adminGetUserIps(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * admin/invite/create
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminInviteCreateRequest * @return [kotlin.collections.List<AdminInviteCreate200ResponseInner>]
     */
    @POST("admin/invite/create")
    suspend fun adminInviteCreate(
        @Body adminInviteCreateRequest: AdminInviteCreateRequest,
    ): Response<kotlin.collections.List<AdminInviteCreate200ResponseInner>>

    /**
     * admin/invite/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminInviteListRequest * @return [kotlin.collections.List<kotlin.Any>]
     */
    @POST("admin/invite/list")
    suspend fun adminInviteList(
        @Body adminInviteListRequest: AdminInviteListRequest,
    ): Response<kotlin.collections.List<kotlin.Any>>

    /**
     * admin/promo/create
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminPromoCreateRequest * @return [Unit]
     */
    @POST("admin/promo/create")
    suspend fun adminPromoCreate(
        @Body adminPromoCreateRequest: AdminPromoCreateRequest,
    ): Response<Unit>

    /**
     * admin/queue/clear
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/queue/clear")
    suspend fun adminQueueClear(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * admin/queue/deliver-delayed
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<kotlin.collections.List<AdminQueueDeliverDelayed200ResponseInnerInner>>]
     */
    @POST("admin/queue/deliver-delayed")
    suspend fun adminQueueDeliverDelayed(
        @Body body: kotlin.Any,
    ): Response<kotlin.collections.List<kotlin.collections.List<AdminQueueDeliverDelayed200ResponseInnerInner>>>

    /**
     * admin/queue/inbox-delayed
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<kotlin.collections.List<AdminQueueDeliverDelayed200ResponseInnerInner>>]
     */
    @POST("admin/queue/inbox-delayed")
    suspend fun adminQueueInboxDelayed(
        @Body body: kotlin.Any,
    ): Response<kotlin.collections.List<kotlin.collections.List<AdminQueueDeliverDelayed200ResponseInnerInner>>>

    /**
     * admin/queue/promote
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminQueuePromoteRequest * @return [Unit]
     */
    @POST("admin/queue/promote")
    suspend fun adminQueuePromote(
        @Body adminQueuePromoteRequest: AdminQueuePromoteRequest,
    ): Response<Unit>

    /**
     * admin/queue/stats
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [AdminQueueStats200Response]
     */
    @POST("admin/queue/stats")
    suspend fun adminQueueStats(
        @Body body: kotlin.Any,
    ): Response<AdminQueueStats200Response>

    /**
     * admin/relays/add
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRelaysAddRequest * @return [AdminRelaysAdd200Response]
     */
    @POST("admin/relays/add")
    suspend fun adminRelaysAdd(
        @Body adminRelaysAddRequest: AdminRelaysAddRequest,
    ): Response<AdminRelaysAdd200Response>

    /**
     * admin/relays/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<AdminRelaysAdd200Response>]
     */
    @POST("admin/relays/list")
    suspend fun adminRelaysList(
        @Body body: kotlin.Any,
    ): Response<kotlin.collections.List<AdminRelaysAdd200Response>>

    /**
     * admin/relays/remove
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRelaysAddRequest * @return [Unit]
     */
    @POST("admin/relays/remove")
    suspend fun adminRelaysRemove(
        @Body adminRelaysAddRequest: AdminRelaysAddRequest,
    ): Response<Unit>

    /**
     * admin/reset-password
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [AdminResetPassword200Response]
     */
    @POST("admin/reset-password")
    suspend fun adminResetPassword(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<AdminResetPassword200Response>

    /**
     * admin/resolve-abuse-user-report
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminResolveAbuseUserReportRequest * @return [Unit]
     */
    @POST("admin/resolve-abuse-user-report")
    suspend fun adminResolveAbuseUserReport(
        @Body adminResolveAbuseUserReportRequest: AdminResolveAbuseUserReportRequest,
    ): Response<Unit>

    /**
     * admin/roles/assign
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesAssignRequest * @return [Unit]
     */
    @POST("admin/roles/assign")
    suspend fun adminRolesAssign(
        @Body adminRolesAssignRequest: AdminRolesAssignRequest,
    ): Response<Unit>

    /**
     * admin/roles/create
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesCreateRequest * @return [Unit]
     */
    @POST("admin/roles/create")
    suspend fun adminRolesCreate(
        @Body adminRolesCreateRequest: AdminRolesCreateRequest,
    ): Response<Unit>

    /**
     * admin/roles/delete
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesDeleteRequest * @return [Unit]
     */
    @POST("admin/roles/delete")
    suspend fun adminRolesDelete(
        @Body adminRolesDeleteRequest: AdminRolesDeleteRequest,
    ): Response<Unit>

    /**
     * admin/roles/list
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/roles/list")
    suspend fun adminRolesList(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * admin/roles/show
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesDeleteRequest * @return [Unit]
     */
    @POST("admin/roles/show")
    suspend fun adminRolesShow(
        @Body adminRolesDeleteRequest: AdminRolesDeleteRequest,
    ): Response<Unit>

    /**
     * admin/roles/unassign
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesUnassignRequest * @return [Unit]
     */
    @POST("admin/roles/unassign")
    suspend fun adminRolesUnassign(
        @Body adminRolesUnassignRequest: AdminRolesUnassignRequest,
    ): Response<Unit>

    /**
     * admin/roles/update
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesUpdateRequest * @return [Unit]
     */
    @POST("admin/roles/update")
    suspend fun adminRolesUpdate(
        @Body adminRolesUpdateRequest: AdminRolesUpdateRequest,
    ): Response<Unit>

    /**
     * admin/roles/update-default-policies
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesUpdateDefaultPoliciesRequest * @return [Unit]
     */
    @POST("admin/roles/update-default-policies")
    suspend fun adminRolesUpdateDefaultPolicies(
        @Body adminRolesUpdateDefaultPoliciesRequest: AdminRolesUpdateDefaultPoliciesRequest,
    ): Response<Unit>

    /**
     * admin/roles/users
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesUsersRequest * @return [Unit]
     */
    @POST("admin/roles/users")
    suspend fun adminRolesUsers(
        @Body adminRolesUsersRequest: AdminRolesUsersRequest,
    ): Response<Unit>

    /**
     * admin/send-email
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminSendEmailRequest * @return [Unit]
     */
    @POST("admin/send-email")
    suspend fun adminSendEmail(
        @Body adminSendEmailRequest: AdminSendEmailRequest,
    ): Response<Unit>

    /**
     * admin/server-info
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [AdminServerInfo200Response]
     */
    @POST("admin/server-info")
    suspend fun adminServerInfo(
        @Body body: kotlin.Any,
    ): Response<AdminServerInfo200Response>

    /**
     * admin/show-moderation-logs
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAdListRequest * @return [kotlin.collections.List<AdminShowModerationLogs200ResponseInner>]
     */
    @POST("admin/show-moderation-logs")
    suspend fun adminShowModerationLogs(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Response<kotlin.collections.List<AdminShowModerationLogs200ResponseInner>>

    /**
     * admin/show-user
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [kotlin.Any]
     */
    @POST("admin/show-user")
    suspend fun adminShowUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<kotlin.Any>

    /**
     * admin/show-users
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminShowUsersRequest * @return [kotlin.collections.List<UserDetailed>]
     */
    @POST("admin/show-users")
    suspend fun adminShowUsers(
        @Body adminShowUsersRequest: AdminShowUsersRequest,
    ): Response<kotlin.collections.List<UserDetailed>>

    /**
     * admin/suspend-user
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/suspend-user")
    suspend fun adminSuspendUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * admin/unsuspend-user
     * No description provided.  **Credential required**: *Yes*
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
    @POST("admin/unsuspend-user")
    suspend fun adminUnsuspendUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * admin/update-meta
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminUpdateMetaRequest * @return [Unit]
     */
    @POST("admin/update-meta")
    suspend fun adminUpdateMeta(
        @Body adminUpdateMetaRequest: AdminUpdateMetaRequest,
    ): Response<Unit>

    /**
     * admin/update-user-note
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminUpdateUserNoteRequest * @return [Unit]
     */
    @POST("admin/update-user-note")
    suspend fun adminUpdateUserNote(
        @Body adminUpdateUserNoteRequest: AdminUpdateUserNoteRequest,
    ): Response<Unit>
}
