package dev.dimension.flare.data.database.cache.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation

@Entity(
    indices = [
        Index(value = ["canonicalHandle", "host"], unique = true),
    ],
)
internal data class DbUser(
    @PrimaryKey
    val userKey: MicroBlogKey,
    val name: String,
    val canonicalHandle: String,
    val host: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val content: UiProfile,
)

@Entity(
    primaryKeys = ["accountType", "userKey"],
    indices = [
        Index(value = ["accountType", "userKey"], unique = true),
    ],
)
internal data class DbUserRelation(
    val accountType: DbAccountType,
    val userKey: MicroBlogKey,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val relation: UiRelation,
)
