package dev.dimension.flare.common

public data class MediaDownloadBatchResult(
    public val succeededFileNames: List<String>,
    public val failedFileNames: List<String>,
) {
    public val totalCount: Int
        get() = succeededFileNames.size + failedFileNames.size

    public val isSuccess: Boolean
        get() = failedFileNames.isEmpty()
}
