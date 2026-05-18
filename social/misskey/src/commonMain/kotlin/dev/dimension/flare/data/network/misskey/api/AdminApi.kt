package dev.dimension.flare.data.network.misskey.api

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

public interface AdminApi {
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
    public suspend fun adminAbuseReportResolverCreate(
        @Body adminAbuseReportResolverCreateRequest: AdminAbuseReportResolverCreateRequest,
    ): AdminAbuseReportResolverCreate200Response

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
    public suspend fun adminAbuseUserReports(
        @Body adminAbuseUserReportsRequest: AdminAbuseUserReportsRequest,
    ): kotlin.collections.List<AdminAbuseUserReports200ResponseInner>

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
    public suspend fun adminAccountsCreate(
        @Body adminAccountsCreateRequest: AdminAccountsCreateRequest,
    ): User

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
    public suspend fun adminAccountsDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun adminAdCreate(
        @Body adminAdCreateRequest: AdminAdCreateRequest,
    ): Unit

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
    public suspend fun adminAdDelete(
        @Body adminAdDeleteRequest: AdminAdDeleteRequest,
    ): Unit

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
    public suspend fun adminAdList(
        @Body adminAdListRequest: AdminAdListRequest,
    ): Unit

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
    public suspend fun adminAdUpdate(
        @Body adminAdUpdateRequest: AdminAdUpdateRequest,
    ): Unit

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
    public suspend fun adminAnnouncementsCreate(
        @Body adminAnnouncementsCreateRequest: AdminAnnouncementsCreateRequest,
    ): AdminAnnouncementsCreate200Response

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
    public suspend fun adminAnnouncementsDelete(
        @Body adminAdDeleteRequest: AdminAdDeleteRequest,
    ): Unit

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
    public suspend fun adminAnnouncementsList(
        @Body adminAnnouncementsListRequest: AdminAnnouncementsListRequest,
    ): kotlin.collections.List<AdminAnnouncementsList200ResponseInner>

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
    public suspend fun adminAnnouncementsUpdate(
        @Body adminAnnouncementsUpdateRequest: AdminAnnouncementsUpdateRequest,
    ): Unit

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
    public suspend fun adminDeleteAccount(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): kotlin.Any

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
    public suspend fun adminDeleteAllFilesOfAUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun adminDriveCleanRemoteFiles(
        @Body body: kotlin.Any,
    ): Unit

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
    public suspend fun adminDriveCleanup(
        @Body body: kotlin.Any,
    ): Unit

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
    public suspend fun adminDriveFiles(
        @Body adminDriveFilesRequest: AdminDriveFilesRequest,
    ): kotlin.collections.List<DriveFile>

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
    public suspend fun adminDriveShowFile(
        @Body adminDriveShowFileRequest: AdminDriveShowFileRequest,
    ): AdminDriveShowFile200Response

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
    public suspend fun adminEmojiAdd(
        @Body adminEmojiAddRequest: AdminEmojiAddRequest,
    ): Unit

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
    public suspend fun adminEmojiAddAliasesBulk(
        @Body adminEmojiAddAliasesBulkRequest: AdminEmojiAddAliasesBulkRequest,
    ): Unit

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
    public suspend fun adminEmojiCopy(
        @Body adminEmojiCopyRequest: AdminEmojiCopyRequest,
    ): AdminEmojiCopy200Response

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
    public suspend fun adminEmojiDelete(
        @Body adminAdDeleteRequest: AdminAdDeleteRequest,
    ): Unit

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
    public suspend fun adminEmojiDeleteBulk(
        @Body adminEmojiDeleteBulkRequest: AdminEmojiDeleteBulkRequest,
    ): Unit

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
    public suspend fun adminEmojiList(
        @Body adminEmojiListRequest: AdminEmojiListRequest,
    ): kotlin.collections.List<AdminEmojiList200ResponseInner>

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
    public suspend fun adminEmojiListRemote(
        @Body adminEmojiListRemoteRequest: AdminEmojiListRemoteRequest,
    ): kotlin.collections.List<AdminEmojiListRemote200ResponseInner>

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
    public suspend fun adminEmojiRemoveAliasesBulk(
        @Body adminEmojiAddAliasesBulkRequest: AdminEmojiAddAliasesBulkRequest,
    ): Unit

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
    public suspend fun adminEmojiSetAliasesBulk(
        @Body adminEmojiAddAliasesBulkRequest: AdminEmojiAddAliasesBulkRequest,
    ): Unit

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
    public suspend fun adminEmojiSetCategoryBulk(
        @Body adminEmojiSetCategoryBulkRequest: AdminEmojiSetCategoryBulkRequest,
    ): Unit

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
    public suspend fun adminEmojiSetLicenseBulk(
        @Body adminEmojiSetLicenseBulkRequest: AdminEmojiSetLicenseBulkRequest,
    ): Unit

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
    public suspend fun adminEmojiUpdate(
        @Body adminEmojiUpdateRequest: AdminEmojiUpdateRequest,
    ): Unit

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
    public suspend fun adminFederationDeleteAllFiles(
        @Body adminFederationDeleteAllFilesRequest: AdminFederationDeleteAllFilesRequest,
    ): Unit

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
    public suspend fun adminFederationRefreshRemoteInstanceMetadata(
        @Body adminFederationDeleteAllFilesRequest: AdminFederationDeleteAllFilesRequest,
    ): Unit

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
    public suspend fun adminFederationRemoveAllFollowing(
        @Body adminFederationDeleteAllFilesRequest: AdminFederationDeleteAllFilesRequest,
    ): Unit

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
    public suspend fun adminFederationUpdateInstance(
        @Body adminFederationUpdateInstanceRequest: AdminFederationUpdateInstanceRequest,
    ): Unit

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
    public suspend fun adminGetIndexStats(
        @Body body: kotlin.Any,
    ): Unit

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
    public suspend fun adminGetTableStats(
        @Body body: kotlin.Any,
    ): kotlin.Any

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
    public suspend fun adminGetUserIps(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun adminInviteCreate(
        @Body adminInviteCreateRequest: AdminInviteCreateRequest,
    ): kotlin.collections.List<AdminInviteCreate200ResponseInner>

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
    public suspend fun adminInviteList(
        @Body adminInviteListRequest: AdminInviteListRequest,
    ): kotlin.collections.List<kotlin.Any>

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
    public suspend fun adminPromoCreate(
        @Body adminPromoCreateRequest: AdminPromoCreateRequest,
    ): Unit

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
    public suspend fun adminQueueClear(
        @Body body: kotlin.Any,
    ): Unit

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
    public suspend fun adminQueueDeliverDelayed(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<kotlin.collections.List<AdminQueueDeliverDelayed200ResponseInnerInner>>

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
    public suspend fun adminQueueInboxDelayed(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<kotlin.collections.List<AdminQueueDeliverDelayed200ResponseInnerInner>>

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
    public suspend fun adminQueuePromote(
        @Body adminQueuePromoteRequest: AdminQueuePromoteRequest,
    ): Unit

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
    public suspend fun adminQueueStats(
        @Body body: kotlin.Any,
    ): AdminQueueStats200Response

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
    public suspend fun adminRelaysAdd(
        @Body adminRelaysAddRequest: AdminRelaysAddRequest,
    ): AdminRelaysAdd200Response

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
    public suspend fun adminRelaysList(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<AdminRelaysAdd200Response>

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
    public suspend fun adminRelaysRemove(
        @Body adminRelaysAddRequest: AdminRelaysAddRequest,
    ): Unit

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
    public suspend fun adminResetPassword(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): AdminResetPassword200Response

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
    public suspend fun adminResolveAbuseUserReport(
        @Body adminResolveAbuseUserReportRequest: AdminResolveAbuseUserReportRequest,
    ): Unit

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
    public suspend fun adminRolesAssign(
        @Body adminRolesAssignRequest: AdminRolesAssignRequest,
    ): Unit

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
    public suspend fun adminRolesCreate(
        @Body adminRolesCreateRequest: AdminRolesCreateRequest,
    ): Unit

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
    public suspend fun adminRolesDelete(
        @Body adminRolesDeleteRequest: AdminRolesDeleteRequest,
    ): Unit

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
    public suspend fun adminRolesList(
        @Body body: kotlin.Any,
    ): Unit

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
    public suspend fun adminRolesShow(
        @Body adminRolesDeleteRequest: AdminRolesDeleteRequest,
    ): Unit

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
    public suspend fun adminRolesUnassign(
        @Body adminRolesUnassignRequest: AdminRolesUnassignRequest,
    ): Unit

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
    public suspend fun adminRolesUpdate(
        @Body adminRolesUpdateRequest: AdminRolesUpdateRequest,
    ): Unit

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
    public suspend fun adminRolesUpdateDefaultPolicies(
        @Body adminRolesUpdateDefaultPoliciesRequest: AdminRolesUpdateDefaultPoliciesRequest,
    ): Unit

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
    public suspend fun adminRolesUsers(
        @Body adminRolesUsersRequest: AdminRolesUsersRequest,
    ): Unit

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
    public suspend fun adminSendEmail(
        @Body adminSendEmailRequest: AdminSendEmailRequest,
    ): Unit

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
    public suspend fun adminServerInfo(
        @Body body: kotlin.Any,
    ): AdminServerInfo200Response

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
    public suspend fun adminShowModerationLogs(
        @Body adminAdListRequest: AdminAdListRequest,
    ): kotlin.collections.List<AdminShowModerationLogs200ResponseInner>

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
    public suspend fun adminShowUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): kotlin.Any

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
    public suspend fun adminShowUsers(
        @Body adminShowUsersRequest: AdminShowUsersRequest,
    ): kotlin.collections.List<UserDetailed>

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
    public suspend fun adminSuspendUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun adminUnsuspendUser(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

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
    public suspend fun adminUpdateMeta(
        @Body adminUpdateMetaRequest: AdminUpdateMetaRequest,
    ): Unit

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
    public suspend fun adminUpdateUserNote(
        @Body adminUpdateUserNoteRequest: AdminUpdateUserNoteRequest,
    ): Unit
}
