package dev.dimension.flare.server

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/v1")
public class V1 {
    @Serializable
    @Resource("translate")
    public data class Translate(val parent: V1 = V1()) {
        @Serializable
        public data class Request(val text: String, val targetLanguage: String)
        @Serializable
        public data class Response(val result: String)
    }

    @Serializable
    @Resource("tldr")
    public data class Tldr(val parent: V1 = V1()) {
        @Serializable
        public data class Request(val text: String, val targetLanguage: String)
        @Serializable
        public data class Response(val result: String)
    }
}