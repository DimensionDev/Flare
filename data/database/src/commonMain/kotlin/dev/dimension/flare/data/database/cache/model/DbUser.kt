package dev.dimension.flare.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation

@Entity(
    indices = [
        Index(value = ["canonicalHandle", "host"], unique = true),
    ],
)
public data class DbUser(
    @PrimaryKey
    public val userKey: MicroBlogKey,
    public val name: String,
    public val canonicalHandle: String,
    public val host: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public val content: UiProfile,
)

@Entity(
    primaryKeys = ["accountType", "userKey"],
    indices = [
        Index(value = ["accountType", "userKey"], unique = true),
    ],
)
public data class DbUserRelation(
    public val accountType: DbAccountType,
    public val userKey: MicroBlogKey,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public val relation: UiRelation,
)
