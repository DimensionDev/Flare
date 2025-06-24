package dev.dimension.flare.data.database.cache.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

@Entity(
    indices = [
        Index(value = ["userKey", "accountType"], unique = true),
    ],
)
internal data class DbUserHistory(
    val userKey: MicroBlogKey,
    val accountType: AccountType,
    val lastVisit: Long,
    @PrimaryKey
    val _id: String = "$accountType-$userKey",
)

internal data class DbUserHistoryWithUser(
    @Embedded
    val data: DbUserHistory,
    @Relation(parentColumn = "userKey", entityColumn = "userKey")
    val user: DbUser,
)
