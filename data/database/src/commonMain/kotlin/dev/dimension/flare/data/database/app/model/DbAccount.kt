package dev.dimension.flare.data.database.app.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.Serializable

@Serializable
@Entity
public data class DbAccount(
    @PrimaryKey val account_key: MicroBlogKey,
    val credential_json: String,
    val platform_type: PlatformType,
    val last_active: Long,
    @ColumnInfo(
        defaultValue = "0",
    )
    val sort_id: Long = 0L,
)
