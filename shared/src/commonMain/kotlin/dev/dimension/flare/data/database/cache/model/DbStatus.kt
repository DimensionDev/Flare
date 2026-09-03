package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverter
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2

@Entity(
    indices = [Index(value = ["statusKey", "accountType"], unique = true)],
)
internal data class DbStatus(
    val statusKey: MicroBlogKey,
    val accountType: DbAccountType,
    @ColumnInfo(name = "content", typeAffinity = ColumnInfo.BLOB)
    val storedContent: DbTimelineContent,
    val contentHash: Long,
    val renderHash: Int,
    val text: String?, // For Searching
    @PrimaryKey
    val id: String = createId(accountType = accountType, statusKey = statusKey),
) {
    @Ignore
    constructor(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: UiTimelineV2,
        renderHash: Int,
        text: String?,
        id: String = createId(accountType = accountType, statusKey = statusKey),
    ) : this(
        statusKey = statusKey,
        accountType = accountType,
        preparedContent = DbTimelineContent.encode(content),
        renderHash = renderHash,
        text = text,
        id = id,
    )

    @Ignore
    private constructor(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        preparedContent: DbTimelineContent,
        renderHash: Int,
        text: String?,
        id: String,
    ) : this(
        statusKey = statusKey,
        accountType = accountType,
        storedContent = preparedContent,
        contentHash = preparedContent.contentHash,
        renderHash = renderHash,
        text = text,
        id = id,
    )

    @get:Ignore
    val content: UiTimelineV2
        get() = storedContent.value

    companion object {
        fun createId(
            accountType: DbAccountType,
            statusKey: MicroBlogKey,
        ): String = "${accountType}_$statusKey"
    }
}

internal fun UiTimelineV2.databaseContentHash(): Long = encodeProtobuf<UiTimelineV2>().databaseContentHash()

private fun ByteArray.databaseContentHash(): Long =
    fold(FNV_1A_64_OFFSET_BASIS) { hash, byte ->
        (hash xor (byte.toLong() and 0xffL)) * FNV_1A_64_PRIME
    }

internal class DbTimelineContent private constructor(
    val value: UiTimelineV2,
    private val encoded: ByteArray?,
    val contentHash: Long,
) {
    override fun equals(other: Any?): Boolean = other is DbTimelineContent && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        fun encode(value: UiTimelineV2): DbTimelineContent {
            val encoded = value.encodeProtobuf<UiTimelineV2>()
            return DbTimelineContent(
                value = value,
                encoded = encoded,
                contentHash = encoded.databaseContentHash(),
            )
        }

        fun decode(encoded: ByteArray): DbTimelineContent =
            DbTimelineContent(
                value = encoded.decodeProtobuf<UiTimelineV2>(),
                // The decoded model is retained by the timeline cache; keeping the database
                // blob here as well would duplicate every cached status payload in memory.
                encoded = null,
                // DbStatus.contentHash is read from its own numeric column.
                contentHash = 0,
            )

        fun bytes(value: DbTimelineContent): ByteArray = value.encoded ?: value.value.encodeProtobuf<UiTimelineV2>()
    }
}

internal class DbTimelineContentConverter {
    @ColumnTypeConverter
    fun fromTimelineContent(value: DbTimelineContent): ByteArray = DbTimelineContent.bytes(value)

    @ColumnTypeConverter
    fun toTimelineContent(value: ByteArray): DbTimelineContent = DbTimelineContent.decode(value)
}

private const val FNV_1A_64_OFFSET_BASIS = -3750763034362895579L
private const val FNV_1A_64_PRIME = 1099511628211L
