package dev.dimension.flare.data.database.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DbKeywordFilter(
    @PrimaryKey
    val keyword: String,
    val for_timeline: Long,
    val for_notification: Long,
    val for_search: Long,
    val expired_at: Long,
)
