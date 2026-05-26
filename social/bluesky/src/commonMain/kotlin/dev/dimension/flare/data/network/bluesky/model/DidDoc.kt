package dev.dimension.flare.data.network.bluesky.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DidDoc(
    @SerialName("@context")
    val context: List<String>? = null,
    val id: String? = null,
    val alsoKnownAs: List<String>? = null,
    val verificationMethod: List<VerificationMethod>? = null,
    val service: List<Service>? = null,
)

@Serializable
internal data class Service(
    val id: String? = null,
    val type: String? = null,
    val serviceEndpoint: String? = null,
)

@Serializable
internal data class VerificationMethod(
    val id: String? = null,
    val type: String? = null,
    val controller: String? = null,
    val publicKeyMultibase: String? = null,
)
