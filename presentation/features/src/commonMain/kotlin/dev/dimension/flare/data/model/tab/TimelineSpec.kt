package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asText
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
    public val targetId: (data: T) -> String

    @OptIn(ExperimentalSerializationApi::class)
    public fun target(
        data: T,
        title: UiText = this.title.asText(),
        icon: IconType = this.icon,
    ): TimelineSourceRef =
        TimelineSourceRef(
            id = "$id:${targetId(data)}",
            specId = id,
            title = title,
            icon = icon,
            data = ProtoBuf.encodeToHexString(serializer, data),
        )

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
    override val targetId: (data: T) -> String,
    private val loaderFactory: (service: MicroblogDataSource, data: T) -> RemoteLoader<UiTimelineV2>,
) : TimelineSpec<T> {
    internal fun accountKey(encodedData: String): MicroBlogKey = decode(encodedData).accountKey

    internal fun createLoader(
        service: MicroblogDataSource,
        encodedData: String,
    ): RemoteLoader<UiTimelineV2> = loaderFactory(service, decode(encodedData))
}

public class StandaloneTimelineSpec<T : TimelineSpec.Data> internal constructor(
    override val id: String,
    override val title: UiStrings,
    override val icon: IconType,
    override val serializer: KSerializer<T>,
    override val targetId: (data: T) -> String,
    private val loaderFactory: (context: TimelineLoaderContext, data: T) -> Flow<RemoteLoader<UiTimelineV2>>,
) : TimelineSpec<T> {
    internal fun createLoader(
        context: TimelineLoaderContext,
        encodedData: String,
    ): Flow<RemoteLoader<UiTimelineV2>> = loaderFactory(context, decode(encodedData))
}

internal class TimelineLoaderContext(
    val appDatabase: AppDatabase,
    val cacheDatabase: CacheDatabase,
)
