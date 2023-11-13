package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Multipart
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminDriveShowFileRequest
import dev.dimension.flare.data.network.misskey.api.model.Drive200Response
import dev.dimension.flare.data.network.misskey.api.model.DriveFile
import dev.dimension.flare.data.network.misskey.api.model.DriveFilesAttachedNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFilesCheckExistenceRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFilesFindRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFilesRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFilesUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFilesUploadFromUrlRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFolder
import dev.dimension.flare.data.network.misskey.api.model.DriveFoldersCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFoldersDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFoldersFindRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFoldersRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveFoldersUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.DriveStreamRequest
import dev.dimension.flare.data.network.misskey.api.model.Note
import io.ktor.client.request.forms.MultiPartFormDataContent

interface DriveApi {
    /**
     * drive
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [Drive200Response]
     */
    @POST("drive")
    suspend fun drive(
        @Body body: kotlin.Any,
    ): Response<Drive200Response>

    /**
     * drive/files
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFilesRequest * @return [kotlin.collections.List<DriveFile>]
     */
    @POST("drive/files")
    suspend fun driveFiles(
        @Body driveFilesRequest: DriveFilesRequest,
    ): Response<kotlin.collections.List<DriveFile>>

    /**
     * drive/files/attached-notes
     * Find the notes to which the given file is attached.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFilesAttachedNotesRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("drive/files/attached-notes")
    suspend fun driveFilesAttachedNotes(
        @Body driveFilesAttachedNotesRequest: DriveFilesAttachedNotesRequest,
    ): Response<kotlin.collections.List<Note>>

    /**
     * drive/files/check-existence
     * Check if a given file exists.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFilesCheckExistenceRequest * @return [kotlin.Boolean]
     */
    @POST("drive/files/check-existence")
    suspend fun driveFilesCheckExistence(
        @Body driveFilesCheckExistenceRequest: DriveFilesCheckExistenceRequest,
    ): Response<kotlin.Boolean>

//    /**
//     * drive/files/create
//     * Upload a new drive file.  **Credential required**: *Yes* / **Permission**: *write:drive*
//     * Responses:
//     *  - 200: OK (with results)
//     *  - 400: Client error
//     *  - 401: Authentication error
//     *  - 403: Forbidden error
//     *  - 418: I'm Ai
//     *  - 429: To many requests
//     *  - 500: Internal server error
//     *
//     * @param file The file contents.
//     * @param folderId  (optional)
//     * @param name  (optional)
//     * @param comment  (optional)
//     * @param isSensitive  (optional, default to false)
//     * @param force  (optional, default to false)
//     * @return [DriveFile]
//     */
//    @Multipart
//    @POST("drive/files/create")
//    suspend fun driveFilesCreate(@Part file: MultipartBody.Part, @Part("folderId") folderId: kotlin.String? = null, @Part("name") name: kotlin.String? = null, @Part("comment") comment: kotlin.String? = null, @Part("isSensitive") isSensitive: kotlin.Boolean? = false, @Part("force") force: kotlin.Boolean? = false): Response<DriveFile>
    @Multipart
    @POST("drive/files/create")
    suspend fun driveFilesCreate(
        @Body map: MultiPartFormDataContent,
    ): Response<DriveFile>

    /**
     * drive/files/delete
     * Delete an existing drive file.  **Credential required**: *Yes* / **Permission**: *write:drive*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFilesAttachedNotesRequest * @return [Unit]
     */
    @POST("drive/files/delete")
    suspend fun driveFilesDelete(
        @Body driveFilesAttachedNotesRequest: DriveFilesAttachedNotesRequest,
    ): Response<Unit>

    /**
     * drive/files/find
     * Search for a drive file by the given parameters.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFilesFindRequest * @return [kotlin.collections.List<DriveFile>]
     */
    @POST("drive/files/find")
    suspend fun driveFilesFind(
        @Body driveFilesFindRequest: DriveFilesFindRequest,
    ): Response<kotlin.collections.List<DriveFile>>

    /**
     * drive/files/find-by-hash
     * Search for a drive file by a hash of the contents.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFilesCheckExistenceRequest * @return [kotlin.collections.List<DriveFile>]
     */
    @POST("drive/files/find-by-hash")
    suspend fun driveFilesFindByHash(
        @Body driveFilesCheckExistenceRequest: DriveFilesCheckExistenceRequest,
    ): Response<kotlin.collections.List<DriveFile>>

    /**
     * drive/files/show
     * Show the properties of a drive file.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminDriveShowFileRequest * @return [DriveFile]
     */
    @POST("drive/files/show")
    suspend fun driveFilesShow(
        @Body adminDriveShowFileRequest: AdminDriveShowFileRequest,
    ): Response<DriveFile>

    /**
     * drive/files/update
     * Update the properties of a drive file.  **Credential required**: *Yes* / **Permission**: *write:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFilesUpdateRequest * @return [DriveFile]
     */
    @POST("drive/files/update")
    suspend fun driveFilesUpdate(
        @Body driveFilesUpdateRequest: DriveFilesUpdateRequest,
    ): Response<DriveFile>

    /**
     * drive/files/upload-from-url
     * Request the server to download a new drive file from the specified URL.  **Credential required**: *Yes* / **Permission**: *write:drive*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param driveFilesUploadFromUrlRequest * @return [Unit]
     */
    @POST("drive/files/upload-from-url")
    suspend fun driveFilesUploadFromUrl(
        @Body driveFilesUploadFromUrlRequest: DriveFilesUploadFromUrlRequest,
    ): Response<Unit>

    /**
     * drive/folders
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFoldersRequest * @return [kotlin.collections.List<DriveFolder>]
     */
    @POST("drive/folders")
    suspend fun driveFolders(
        @Body driveFoldersRequest: DriveFoldersRequest,
    ): Response<kotlin.collections.List<DriveFolder>>

    /**
     * drive/folders/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param driveFoldersCreateRequest * @return [DriveFolder]
     */
    @POST("drive/folders/create")
    suspend fun driveFoldersCreate(
        @Body driveFoldersCreateRequest: DriveFoldersCreateRequest,
    ): Response<DriveFolder>

    /**
     * drive/folders/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:drive*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFoldersDeleteRequest * @return [Unit]
     */
    @POST("drive/folders/delete")
    suspend fun driveFoldersDelete(
        @Body driveFoldersDeleteRequest: DriveFoldersDeleteRequest,
    ): Response<Unit>

    /**
     * drive/folders/find
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFoldersFindRequest * @return [kotlin.collections.List<DriveFolder>]
     */
    @POST("drive/folders/find")
    suspend fun driveFoldersFind(
        @Body driveFoldersFindRequest: DriveFoldersFindRequest,
    ): Response<kotlin.collections.List<DriveFolder>>

    /**
     * drive/folders/show
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFoldersDeleteRequest * @return [DriveFolder]
     */
    @POST("drive/folders/show")
    suspend fun driveFoldersShow(
        @Body driveFoldersDeleteRequest: DriveFoldersDeleteRequest,
    ): Response<DriveFolder>

    /**
     * drive/folders/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveFoldersUpdateRequest * @return [DriveFolder]
     */
    @POST("drive/folders/update")
    suspend fun driveFoldersUpdate(
        @Body driveFoldersUpdateRequest: DriveFoldersUpdateRequest,
    ): Response<DriveFolder>

    /**
     * drive/stream
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:drive*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param driveStreamRequest * @return [kotlin.collections.List<DriveFile>]
     */
    @POST("drive/stream")
    suspend fun driveStream(
        @Body driveStreamRequest: DriveStreamRequest,
    ): Response<kotlin.collections.List<DriveFile>>
}
