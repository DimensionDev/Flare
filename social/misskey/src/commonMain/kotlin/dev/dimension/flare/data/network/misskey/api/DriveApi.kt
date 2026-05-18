package dev.dimension.flare.data.network.misskey.api

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

public interface DriveApi {
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
    public suspend fun drive(
        @Body body: kotlin.Any,
    ): Drive200Response

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
    public suspend fun driveFiles(
        @Body driveFilesRequest: DriveFilesRequest,
    ): kotlin.collections.List<DriveFile>

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
    public suspend fun driveFilesAttachedNotes(
        @Body driveFilesAttachedNotesRequest: DriveFilesAttachedNotesRequest,
    ): kotlin.collections.List<Note>

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
    public suspend fun driveFilesCheckExistence(
        @Body driveFilesCheckExistenceRequest: DriveFilesCheckExistenceRequest,
    ): kotlin.Boolean

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
//    suspend fun driveFilesCreate(@Part file: MultipartBody.Part, @Part("folderId") folderId: kotlin.String? = null, @Part("name") name: kotlin.String? = null, @Part("comment") comment: kotlin.String? = null, @Part("isSensitive") isSensitive: kotlin.Boolean? = false, @Part("force") force: kotlin.Boolean? = false): DriveFile
    @Multipart
    @POST("drive/files/create")
    public suspend fun driveFilesCreate(
        @Body map: MultiPartFormDataContent,
    ): DriveFile

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
    public suspend fun driveFilesDelete(
        @Body driveFilesAttachedNotesRequest: DriveFilesAttachedNotesRequest,
    ): Unit

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
    public suspend fun driveFilesFind(
        @Body driveFilesFindRequest: DriveFilesFindRequest,
    ): kotlin.collections.List<DriveFile>

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
    public suspend fun driveFilesFindByHash(
        @Body driveFilesCheckExistenceRequest: DriveFilesCheckExistenceRequest,
    ): kotlin.collections.List<DriveFile>

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
    public suspend fun driveFilesShow(
        @Body adminDriveShowFileRequest: AdminDriveShowFileRequest,
    ): DriveFile

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
    public suspend fun driveFilesUpdate(
        @Body driveFilesUpdateRequest: DriveFilesUpdateRequest,
    ): DriveFile

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
    public suspend fun driveFilesUploadFromUrl(
        @Body driveFilesUploadFromUrlRequest: DriveFilesUploadFromUrlRequest,
    ): Unit

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
    public suspend fun driveFolders(
        @Body driveFoldersRequest: DriveFoldersRequest,
    ): kotlin.collections.List<DriveFolder>

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
    public suspend fun driveFoldersCreate(
        @Body driveFoldersCreateRequest: DriveFoldersCreateRequest,
    ): DriveFolder

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
    public suspend fun driveFoldersDelete(
        @Body driveFoldersDeleteRequest: DriveFoldersDeleteRequest,
    ): Unit

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
    public suspend fun driveFoldersFind(
        @Body driveFoldersFindRequest: DriveFoldersFindRequest,
    ): kotlin.collections.List<DriveFolder>

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
    public suspend fun driveFoldersShow(
        @Body driveFoldersDeleteRequest: DriveFoldersDeleteRequest,
    ): DriveFolder

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
    public suspend fun driveFoldersUpdate(
        @Body driveFoldersUpdateRequest: DriveFoldersUpdateRequest,
    ): DriveFolder

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
    public suspend fun driveStream(
        @Body driveStreamRequest: DriveStreamRequest,
    ): kotlin.collections.List<DriveFile>
}
