package dev.dimension.flare.server

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Resource("/api")
public class Api {
    @Resource("v1")
    public data class V1(val parent: Api = Api()) {
        @Resource("translate")
        public data class Translate(val parent: V1 = V1()) {
            @Serializable
            public data class Request(val text: String, val targetLanguage: String)
            @Serializable
            public data class Response(val result: String)
        }

        @Resource("tldr")
        public data class Tldr(val parent: V1 = V1()) {
            @Serializable
            public data class Request(val text: String, val targetLanguage: String)
            @Serializable
            public data class Response(val result: String)
        }
    }


    @Resource("about")
    public data class About(val parent: Api = Api()) {
        @Serializable
        public data class Response(
            val name: String,
            val version: String,
            val apiLevel: String,
        )
    }
}