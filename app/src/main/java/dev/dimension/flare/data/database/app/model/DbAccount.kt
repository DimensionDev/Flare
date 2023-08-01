package dev.dimension.flare.data.database.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

@Entity
data class DbAccount(
    @PrimaryKey
    val account_key: MicroBlogKey,
    val credential_json: String,
    val platform_type: PlatformType,
    val lastActive: Long
)
