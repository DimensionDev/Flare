package dev.dimension.flare.data.database.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.dimension.flare.model.PlatformType

@Entity
data class DbGuestData(
    @PrimaryKey
    val id: Int = 0,
    val host: String,
    val platformType: PlatformType,
)
