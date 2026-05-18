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
    public val listKey: MicroBlogKey,
    public val accountType: DbAccountType,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public val content: ListContent,
    @PrimaryKey
    public val id: String = "${accountType}_$listKey",
) {
    @Serializable
    public data class ListContent(
        public val data: UiList,
    )
}

public class ListContentConverters {
    @TypeConverter
    public fun fromMessageContent(content: ListContent): ByteArray = content.encodeProtobuf()

    @TypeConverter
    public fun toMessageContent(value: ByteArray): ListContent = value.decodeProtobuf()
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
    public val accountType: DbAccountType,
    public val pagingKey: String,
    public val listKey: MicroBlogKey,
    @PrimaryKey
    public val _id: String = Uuid.random().toString(),
)

public data class DbListWithContent(
    @Embedded
    public val paging: DbListPaging,
    @Relation(
        parentColumn = "listKey",
        entityColumn = "listKey",
        entity = DbList::class,
    )
    public val list: DbList,
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
    public val listKey: MicroBlogKey,
    public val memberKey: MicroBlogKey,
    @PrimaryKey
    public val id: String = "${listKey}_$memberKey",
)

public data class DbListMemberWithContent(
    @Embedded
    public val member: DbListMember,
    @Relation(
        parentColumn = "memberKey",
        entityColumn = "userKey",
        entity = DbUser::class,
    )
    public val user: DbUser,
)

public data class DbListMemberWithList(
    @Embedded
    public val member: DbListMember,
    @Relation(
        parentColumn = "listKey",
        entityColumn = "listKey",
        entity = DbList::class,
    )
    public val list: DbList,
)

public data class DbUserWithListMembership(
    @Embedded
    public val user: DbUser,
    @Relation(
        parentColumn = "userKey",
        entityColumn = "memberKey",
        entity = DbListMember::class,
    )
    public val listMemberships: List<DbListMemberWithList>,
)
