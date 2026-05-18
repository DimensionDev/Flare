package dev.dimension.flare.data.database.app.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.RssDisplayMode
import kotlinx.serialization.Serializable

@Serializable
public enum class SubscriptionType {
    RSS,
    MASTODON_TRENDS,
    MASTODON_PUBLIC,
    MASTODON_LOCAL,
}

@Serializable
@Entity
public data class DbRssSources(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val title: String?,
    val icon: String?,
    @ColumnInfo(defaultValue = "FULL_CONTENT")
    val displayMode: RssDisplayMode = RssDisplayMode.FULL_CONTENT,
    val lastUpdate: Long,
    @ColumnInfo(defaultValue = "RSS")
    val type: SubscriptionType = SubscriptionType.RSS,
)
