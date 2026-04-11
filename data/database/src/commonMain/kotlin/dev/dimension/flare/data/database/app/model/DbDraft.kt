package dev.dimension.flare.data.database.app.model

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
public data class DraftContent(
    val text: String,
    val visibility: UiTimelineV2.Post.Visibility,
    val language: List<String>,
    val sensitive: Boolean,
    val spoilerText: String? = null,
    val localOnly: Boolean = false,
    val poll: DraftPoll? = null,
    val reference: DraftReference? = null,
) {
    @Serializable
    public data class DraftPoll(
        val options: List<String>,
        val expiredAfter: Long,
        val multiple: Boolean,
    )

    @Serializable
    public data class DraftReference(
        val type: DraftReferenceType,
        val statusKey: MicroBlogKey,
        val rootId: String? = null,
    )
}

@Serializable
public enum class DraftReferenceType {
    @SerialName("reply")
    REPLY,

    @SerialName("quote")
    QUOTE,

    @SerialName("vvo_comment")
    VVO_COMMENT,
}

@Serializable
public enum class DraftTargetStatus {
    @SerialName("draft")
    DRAFT,

    @SerialName("sending")
    SENDING,

    @SerialName("failed")
    FAILED,
}

@Serializable
public enum class DraftMediaType {
    @SerialName("image")
    IMAGE,

    @SerialName("video")
    VIDEO,

    @SerialName("other")
    OTHER,
}

@Entity(
    indices = [
        Index(value = ["updated_at"]),
    ],
)
@Serializable
public data class DbDraftGroup(
    @PrimaryKey
    val group_id: String = Uuid.random().toString(),
    val content: DraftContent,
    val created_at: Long,
    val updated_at: Long,
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DbDraftGroup::class,
            parentColumns = ["group_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["account_key"]),
        Index(value = ["status"]),
        Index(value = ["group_id", "account_key"], unique = true),
    ],
)
@Serializable
public data class DbDraftTarget(
    val group_id: String,
    val account_key: MicroBlogKey,
    val status: DraftTargetStatus,
    val error_message: String? = null,
    val attempt_count: Int = 0,
    val last_attempt_at: Long? = null,
    val created_at: Long,
    val updated_at: Long,
    @PrimaryKey
    val target_id: String = "${group_id}_${account_key}",
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DbDraftGroup::class,
            parentColumns = ["group_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["group_id", "sort_order"]),
    ],
)
@Serializable
public data class DbDraftMedia(
    val group_id: String,
    val cache_path: String,
    val file_name: String? = null,
    val media_type: DraftMediaType,
    val alt_text: String? = null,
    val sort_order: Int,
    val created_at: Long,
    @PrimaryKey
    val media_id: String = Uuid.random().toString(),
)

public data class DbDraftGroupWithRelations(
    @Embedded
    val group: DbDraftGroup,
    @Relation(
        parentColumn = "group_id",
        entityColumn = "group_id",
        entity = DbDraftTarget::class,
    )
    val targets: List<DbDraftTarget>,
    @Relation(
        parentColumn = "group_id",
        entityColumn = "group_id",
        entity = DbDraftMedia::class,
    )
    val medias: List<DbDraftMedia>,
)

public class DraftConverters {
    @TypeConverter
    public fun fromDraftContent(value: DraftContent): String = value.encodeJson()

    @TypeConverter
    public fun toDraftContent(value: String): DraftContent = value.decodeJson()

    @TypeConverter
    public fun fromDraftTargetStatus(value: DraftTargetStatus): String = value.name

    @TypeConverter
    public fun toDraftTargetStatus(value: String): DraftTargetStatus = DraftTargetStatus.valueOf(value)

    @TypeConverter
    public fun fromDraftMediaType(value: DraftMediaType): String = value.name

    @TypeConverter
    public fun toDraftMediaType(value: String): DraftMediaType = DraftMediaType.valueOf(value)
}
