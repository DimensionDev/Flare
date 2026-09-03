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
    val storedContent: DbStatusContent,
    val contentFingerprint: Long,
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
        preparedContent = DbStatusContent.encode(content),
        renderHash = renderHash,
        text = text,
        id = id,
    )

    @Ignore
    private constructor(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        preparedContent: DbStatusContent,
        renderHash: Int,
        text: String?,
        id: String,
    ) : this(
        statusKey = statusKey,
        accountType = accountType,
        storedContent = preparedContent,
        contentFingerprint = preparedContent.fingerprint,
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

internal class DbStatusContent private constructor(
    val value: UiTimelineV2,
    private val encoded: ByteArray?,
    val fingerprint: Long,
) {
    override fun equals(other: Any?): Boolean = other is DbStatusContent && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        fun encode(value: UiTimelineV2): DbStatusContent {
            val encoded = value.encodeProtobuf<UiTimelineV2>()
            return DbStatusContent(
                value = value,
                encoded = encoded,
                fingerprint = encoded.databaseContentFingerprint(),
            )
        }

        fun decode(encoded: ByteArray): DbStatusContent =
            DbStatusContent(
                value = encoded.decodeProtobuf<UiTimelineV2>(),
                encoded = null,
                fingerprint = 0,
            )

        fun bytes(value: DbStatusContent): ByteArray = value.encoded ?: value.value.encodeProtobuf<UiTimelineV2>()
    }
}

private fun ByteArray.databaseContentFingerprint(): Long =
    fold(FNV_1A_64_OFFSET_BASIS) { hash, byte ->
        (hash xor (byte.toLong() and 0xffL)) * FNV_1A_64_PRIME
    }

private const val FNV_1A_64_OFFSET_BASIS = -3750763034362895579L
private const val FNV_1A_64_PRIME = 1099511628211L

internal class DbStatusContentConverter {
    @ColumnTypeConverter
    fun fromContent(value: DbStatusContent): ByteArray = DbStatusContent.bytes(value)

    @ColumnTypeConverter
    fun toContent(value: ByteArray): DbStatusContent = DbStatusContent.decode(value)
}
