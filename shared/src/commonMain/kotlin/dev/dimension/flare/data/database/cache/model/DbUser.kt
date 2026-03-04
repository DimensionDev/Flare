package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
    val content: UiProfile,
)

@Entity(
    primaryKeys = ["accountKey", "userKey"],
    indices = [
        Index(value = ["accountKey", "userKey"], unique = true),
    ],
)
internal data class DbUserRelation(
    val accountKey: MicroBlogKey,
    val userKey: MicroBlogKey,
    val relation: UiRelation,
)
