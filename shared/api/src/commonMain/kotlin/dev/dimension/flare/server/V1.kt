package dev.dimension.flare.server

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/v1")
public sealed class V1 {
    @Serializable
    @Resource("translate")
    public data class Translate(val text: String, val targetLanguage: String) {
        @Serializable
        public data class Response(val result: String)
    }
}