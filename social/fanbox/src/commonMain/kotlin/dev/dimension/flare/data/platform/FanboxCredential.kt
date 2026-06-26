package dev.dimension.flare.data.platform

import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@Serializable
public data class FanboxCredential(
    val sessionId: String,
    val csrfToken: String? = null,
    val userId: String,
    val creatorId: String? = null,
    val name: String? = null,
    val iconUrl: String? = null,
    val showAdultContent: Boolean = false,
    val isSupporter: Boolean = false,
    val isCreator: Boolean = false,
)

@HiddenFromObjC
public const val FANBOX_HOST: String = "fanbox.cc"

@HiddenFromObjC
public const val FANBOX_WEB_HOST: String = "www.fanbox.cc"
