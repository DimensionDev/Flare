package dev.dimension.flare.data.datasource.microblog.timeline

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf

public interface TimelineSpec<T : TimelineSpec.Data> {
    public val id: String
    public val title: UiStrings
    public val icon: IconType
    public val serializer: KSerializer<T>

    public fun stableKey(data: T): String

    public fun ref(data: T): TimelineRef<T> =
        TimelineRef(
            spec = this,
            data = data,
        )

    @OptIn(ExperimentalSerializationApi::class)
    public fun encode(data: T): String = ProtoBuf.encodeToHexString(serializer, data)

    @OptIn(ExperimentalSerializationApi::class)
    public fun decode(encodedData: String): T = ProtoBuf.decodeFromHexString(serializer, encodedData)

    public interface Data

    public interface AccountData : Data {
        public val accountKey: MicroBlogKey
    }

    @Serializable
    public open class AccountBasedData(
        public override val accountKey: MicroBlogKey,
    ) : AccountData

    @Serializable
    public data class AccountResourceData(
        public override val accountKey: MicroBlogKey,
        public val resourceId: String,
    ) : AccountData
}

public class AccountTimelineSpec<T : TimelineSpec.AccountData>(
    override val id: String,
    override val title: UiStrings,
    override val icon: IconType,
    override val serializer: KSerializer<T>,
    private val stableKeyFactory: (data: T) -> String,
    private val loaderFactory: (service: MicroblogDataSource, data: T) -> RemoteLoader<UiTimelineV2>,
) : TimelineSpec<T> {
    override fun stableKey(data: T): String = stableKeyFactory(data)

    public fun accountKey(data: TimelineSpec.Data): MicroBlogKey = typedData(data).accountKey

    public fun createLoader(
        service: MicroblogDataSource,
        data: TimelineSpec.Data,
    ): RemoteLoader<UiTimelineV2> = loaderFactory(service, typedData(data))

    @Suppress("UNCHECKED_CAST")
    private fun typedData(data: TimelineSpec.Data): T = data as T
}

public class StandaloneTimelineSpec<T : TimelineSpec.Data>(
    override val id: String,
    override val title: UiStrings,
    override val icon: IconType,
    override val serializer: KSerializer<T>,
    private val stableKeyFactory: (data: T) -> String,
    private val loaderFactory: (context: TimelineLoaderContext, data: T) -> Flow<RemoteLoader<UiTimelineV2>>,
) : TimelineSpec<T> {
    override fun stableKey(data: T): String = stableKeyFactory(data)

    public fun createLoader(
        context: TimelineLoaderContext,
        data: TimelineSpec.Data,
    ): Flow<RemoteLoader<UiTimelineV2>> = loaderFactory(context, typedData(data))

    @Suppress("UNCHECKED_CAST")
    private fun typedData(data: TimelineSpec.Data): T = data as T
}

public class TimelineLoaderContext(
    public val appDatabase: AppDatabase,
    public val cacheDatabase: CacheDatabase,
)
