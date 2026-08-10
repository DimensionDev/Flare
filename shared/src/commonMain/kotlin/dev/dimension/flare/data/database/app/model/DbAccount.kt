package dev.dimension.flare.data.database.app.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity
internal data class DbAccount(
    @PrimaryKey val account_key: MicroBlogKey,
    val credential_json: String,
    @SerialName("platform_type")
    @ColumnInfo(name = "platform_type")
    val platformId: String,
    val last_active: Long,
    @ColumnInfo(
        defaultValue = "0",
    )
    val sort_id: Long = 0L,
)
