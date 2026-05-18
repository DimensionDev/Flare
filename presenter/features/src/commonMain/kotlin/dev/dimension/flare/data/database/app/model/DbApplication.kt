package dev.dimension.flare.data.database.app.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.Serializable

@Serializable
@Entity
internal data class DbApplication(
    @PrimaryKey val host: String,
    val credential_json: String,
    val platform_type: PlatformType,
    val has_pending_oauth_request: Int = 0,
)
