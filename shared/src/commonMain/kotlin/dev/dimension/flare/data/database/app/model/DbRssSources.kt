package dev.dimension.flare.data.database.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
internal data class DbRssSources(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val title: String?,
    val icon: String?,
    @ColumnInfo(defaultValue = "0")
    val openInBrowser: Boolean = false,
    val lastUpdate: Long,
)
