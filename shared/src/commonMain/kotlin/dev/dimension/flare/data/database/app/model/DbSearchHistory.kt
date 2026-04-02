package dev.dimension.flare.data.database.app.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
internal data class DbSearchHistory(
    @PrimaryKey
    val search: String,
    val created_at: Long,
)
