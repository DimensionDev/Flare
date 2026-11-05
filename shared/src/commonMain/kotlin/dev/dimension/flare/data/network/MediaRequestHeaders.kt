package dev.dimension.flare.data.network

public object MediaRequestHeaders {
    // Unknown names can contain credentials, including provider-specific token headers.
    public fun mayForwardAcrossOrigins(name: String): Boolean = name.lowercase() in publicHeaders

    private val publicHeaders = setOf("accept", "accept-language", "origin", "referer", "user-agent")
}
