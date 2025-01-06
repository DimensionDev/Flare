package dev.dimension.flare.data.database.cache.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.dimension.flare.model.MicroBlogKey

@Entity(
    indices = [
        Index(value = ["userKey", "accountKey"], unique = true),
    ],
)
internal data class DbUserHistory(
    val userKey: MicroBlogKey,
    val accountKey: MicroBlogKey,
    val lastVisit: Long,
    @PrimaryKey
    val _id: String = "$accountKey-$userKey",
)

internal data class DbUserHistoryWithUser(
    @Embedded
    val data: DbUserHistory,
    @Relation(parentColumn = "userKey", entityColumn = "userKey")
    val user: DbUser,
)
