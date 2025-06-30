package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.datetime.Instant

@Entity(
    indices = [Index(value = ["statusKey", "accountType"], unique = true)],
)
internal data class DbStatus(
    val statusKey: MicroBlogKey,
    val accountType: AccountType,
    val userKey: MicroBlogKey?,
    val content: StatusContent,
    val text: String?, // For Searching
    val createdAt: Instant,
    @PrimaryKey
    val id: String = "${accountType}_$statusKey",
)
