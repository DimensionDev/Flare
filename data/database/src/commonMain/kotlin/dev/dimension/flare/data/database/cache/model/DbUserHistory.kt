package dev.dimension.flare.data.database.cache.model

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey

@Entity(
    indices = [
        Index(value = ["userKey", "accountType"], unique = true),
        Index(value = ["lastVisit"]),
    ],
)
public data class DbUserHistory(
    val userKey: MicroBlogKey,
    val accountType: DbAccountType,
    val lastVisit: Long,
    @PrimaryKey
    val _id: String = "$accountType-$userKey",
)

public data class DbUserHistoryWithUser(
    @Embedded
    val data: DbUserHistory,
    @Relation(parentColumn = "userKey", entityColumn = "userKey")
    val user: DbUser,
)
