package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.data.database.cache.model.DbList.ListContent
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Entity(
    indices = [Index(value = ["listKey", "accountType"], unique = true)],
)
public data class DbList(
    val listKey: MicroBlogKey,
    val accountType: DbAccountType,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val content: ListContent,
    @PrimaryKey
    val id: String = "${accountType}_$listKey",
) {
    @Serializable
    data class ListContent(
        val data: UiList,
    )
}

public class ListContentConverters {
    @TypeConverter
    fun fromMessageContent(content: ListContent): ByteArray = content.encodeProtobuf()

    @TypeConverter
    fun toMessageContent(value: ByteArray): ListContent = value.decodeProtobuf()
}

@Entity(
    indices = [
        Index(
            value = ["pagingKey"],
        ),
        Index(
            value = ["accountType", "listKey", "pagingKey"],
            unique = true,
        ),
    ],
)
public data class DbListPaging(
    val accountType: DbAccountType,
    val pagingKey: String,
    val listKey: MicroBlogKey,
    @PrimaryKey
    val _id: String = Uuid.random().toString(),
)

public data class DbListWithContent(
    @Embedded
    val paging: DbListPaging,
    @Relation(
        parentColumn = "listKey",
        entityColumn = "listKey",
        entity = DbList::class,
    )
    val list: DbList,
)

@Entity(
    indices = [
        Index(
            value = ["memberKey"],
        ),
        Index(
            value = ["listKey", "memberKey"],
            unique = true,
        ),
    ],
)
public data class DbListMember(
    val listKey: MicroBlogKey,
    val memberKey: MicroBlogKey,
    @PrimaryKey
    val id: String = "${listKey}_$memberKey",
)

public data class DbListMemberWithContent(
    @Embedded
    val member: DbListMember,
    @Relation(
        parentColumn = "memberKey",
        entityColumn = "userKey",
        entity = DbUser::class,
    )
    val user: DbUser,
)

public data class DbListMemberWithList(
    @Embedded
    val member: DbListMember,
    @Relation(
        parentColumn = "listKey",
        entityColumn = "listKey",
        entity = DbList::class,
    )
    val list: DbList,
)

public data class DbUserWithListMembership(
    @Embedded
    val user: DbUser,
    @Relation(
        parentColumn = "userKey",
        entityColumn = "memberKey",
        entity = DbListMember::class,
    )
    val listMemberships: List<DbListMemberWithList>,
)
