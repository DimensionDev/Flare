package dev.dimension.flare.data.database.cache.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiProfile

@Entity(
    indices = [
        Index(value = ["handle", "host", "platformType"], unique = true),
    ],
)
internal data class DbUser(
    @PrimaryKey
    val userKey: MicroBlogKey,
    val platformType: PlatformType,
    val name: String,
    val handle: String,
    val host: String,
    val content: UiProfile,
)
