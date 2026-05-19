@file:Suppress("UNUSED_PARAMETER")

package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.database.DriverFactory
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.io.PlatformPathProducer
import dev.dimension.flare.data.io.WebPlatformPathProducer
import dev.dimension.flare.media.ImageCompressor
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Instant

internal actual val platformModule: Module =
    module {
        single { AppDataStore(get<PlatformPathProducer>()) }
        singleOf(::DriverFactory)
        singleOf(::WebPlatformPathProducer) bind PlatformPathProducer::class
        single<PlatformFormatter> { WebPlatformFormatter }
        single<ImageCompressor> { WebImageCompressor }
        single<OnDeviceAI> { WebOnDeviceAI }
        single<InAppNotification> { NoopInAppNotification }
    }

private data object WebPlatformFormatter : PlatformFormatter {
    override fun formatNumber(number: Long): String = number.toString()

    override fun formatRelativeInstant(instant: Instant): String = instant.toString()

    override fun formatFullInstant(instant: Instant): String = instant.toString()

    override fun formatAbsoluteInstant(instant: Instant): String = instant.toString()
}

private data object WebImageCompressor : ImageCompressor {
    override suspend fun compress(
        imageBytes: ByteArray,
        maxSize: Long,
        maxDimensions: Pair<Int, Int>,
    ): ByteArray = imageBytes
}

private data object WebOnDeviceAI : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = false

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = null
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
