package dev.dimension.flare.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal interface PlatformIPC {
    fun sendShutdown()

    fun <T> sendData(
        type: String,
        data: T,
        serializer: KSerializer<T>,
    )
}

internal data object NoopIPC : PlatformIPC {
    override fun sendShutdown() {}

    override fun <T> sendData(
        type: String,
        data: T,
        serializer: KSerializer<T>,
    ) {}
}

@Serializable
data class IPCEvent<T>(
    @SerialName("Type")
    val type: String,
    @SerialName("Data")
    val data: T?,
) {
    @Serializable
    data class DeeplinkData(
        @SerialName("Deeplink")
        val deeplink: String,
    )

    @Serializable
    data class OpenStatusImageData(
        val index: Int,
        val medias: List<StatusMediaItem>,
    ) {
        @Serializable
        data class StatusMediaItem(
            val url: String,
            val type: String,
            val placeholder: String?,
        )
    }
}
