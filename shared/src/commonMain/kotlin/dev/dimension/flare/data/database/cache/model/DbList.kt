package dev.dimension.flare.data.database.cache.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.cache.model.DbList.ListContent
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Entity(
    indices = [Index(value = ["listKey", "accountType"], unique = true)],
)
internal data class DbList(
    val listKey: MicroBlogKey,
    val accountType: DbAccountType,
    val content: ListContent,
    @PrimaryKey
    val id: String = "${accountType}_$listKey",
) {
    @Serializable
    data class ListContent(
        val data: UiList,
    )
}

internal class ListContentConverters {
    @TypeConverter
    fun fromMessageContent(content: ListContent): String = content.encodeJson()

    @TypeConverter
    fun toMessageContent(value: String): ListContent = value.decodeJson()
}

@Entity(
    indices = [
        Index(
            value = ["accountType", "listKey", "pagingKey"],
            unique = true,
        ),
    ],
)
internal data class DbListPaging(
    val accountType: DbAccountType,
    val pagingKey: String,
    val listKey: MicroBlogKey,
    @PrimaryKey
    val _id: String = Uuid.random().toString(),
)

internal data class DbListWithContent(
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
            value = ["listKey", "memberKey"],
            unique = true,
        ),
    ],
)
internal data class DbListMember(
    val listKey: MicroBlogKey,
    val memberKey: MicroBlogKey,
    @PrimaryKey
    val id: String = "${listKey}_$memberKey",
)

internal data class DbListMemberWithContent(
    @Embedded
    val member: DbListMember,
    @Relation(
        parentColumn = "memberKey",
        entityColumn = "userKey",
        entity = DbUser::class,
    )
    val user: DbUser,
)

internal data class DbListMemberWithList(
    @Embedded
    val member: DbListMember,
    @Relation(
        parentColumn = "listKey",
        entityColumn = "listKey",
        entity = DbList::class,
    )
    val list: DbList,
)

internal data class DbUserWithListMembership(
    @Embedded
    val user: DbUser,
    @Relation(
        parentColumn = "userKey",
        entityColumn = "memberKey",
        entity = DbListMember::class,
    )
    val listMemberships: List<DbListMemberWithList>,
)
