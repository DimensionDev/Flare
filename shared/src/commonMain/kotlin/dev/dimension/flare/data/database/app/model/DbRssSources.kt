package dev.dimension.flare.data.database.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class DbRssSources(
    @PrimaryKey
    val url: String,
    val title: String?,
    val lastUpdate: Long,
)
