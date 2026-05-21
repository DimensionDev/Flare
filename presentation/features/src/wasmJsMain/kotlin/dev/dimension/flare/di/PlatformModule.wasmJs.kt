@file:Suppress("UNUSED_PARAMETER")

package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.io.InMemoryFileStorage
import dev.dimension.flare.media.ImageCompressor
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.humanizer.WebFormatter
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal actual val platformModule: Module =
    module {
        single { AppDataStore(get<FileStorage>()) }
        singleOf(::DriverFactory)
        single<FileStorage> { InMemoryFileStorage() }
        single<PlatformFormatter> { WebFormatter }
        single<ImageCompressor> { WebImageCompressor }
        single<InAppNotification> { NoopInAppNotification }
    }

private data object WebImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray = imageBytes
}

private data object NoopInAppNotification : InAppNotification {
    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) {
    }

    override fun onSuccess(message: Message) {
    }

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) {
    }
}
