package dev.dimension.flare.data.datastore.model

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class AppSettings(
    val version: String,
    val aiConfig: AiConfig = AiConfig(),
    val language: String = "",
    val translateConfig: TranslateConfig = TranslateConfig(),
    val linkOpenDefaults: LinkOpenDefaults = LinkOpenDefaults(),
    val mxgaEnabled: Boolean = false,
    val refreshHomeTimelineOnLaunch: Boolean = true,
    val homeTimelineAutoRefreshInterval: TimelineAutoRefreshInterval = TimelineAutoRefreshInterval.DISABLED,
) {
    public companion object {
        // for SwiftUI environment defaults
        public val default: AppSettings = AppSettings(version = "")
    }

    @Serializable
    public data class LinkOpenDefaults(
        val rules: List<Rule> = emptyList(),
    ) {
        @Serializable
        public data class Rule(
            val host: String,
            val method: Method,
        )

        @Serializable
        public sealed interface Method {
            @Serializable
            @SerialName("Browser")
            public data object Browser : Method

            @Serializable
            @SerialName("Account")
            public data class Account(
                val accountKey: MicroBlogKey,
            ) : Method
        }
    }

    @Serializable
    public data class TranslateConfig(
        val preTranslate: Boolean = false,
        val provider: Provider = Provider.GoogleWeb,
        val autoTranslateExcludedLanguages: List<String> = emptyList(),
        val preferPlatformTranslation: Boolean = false,
        val showOriginalWithTranslation: Boolean = false,
    ) {
        @Serializable
        public sealed interface Provider {
            @Serializable
            @SerialName("AI")
            public data object AI : Provider

            @Serializable
            @SerialName("Google")
            public data object GoogleWeb : Provider

            @Serializable
            public data class DeepL(
                val apiKey: String = "",
                val usePro: Boolean = false,
            ) : Provider

            @Serializable
            public data class GoogleCloud(
                val apiKey: String = "",
            ) : Provider

            @Serializable
            public data class LibreTranslate(
                val baseUrl: String = "",
                val apiKey: String = "",
            ) : Provider
        }
    }

    @Serializable
    public data class AiConfig(
        @Deprecated(
            message = "Translation is always enabled.",
            level = DeprecationLevel.ERROR,
        )
        val translation: Boolean = false,
        val tldr: Boolean = false,
        val type: Type = Type.OpenAI("", "", ""),
        val translatePrompt: String = AiPromptDefaults.TRANSLATE_PROMPT,
        val tldrPrompt: String = AiPromptDefaults.TLDR_PROMPT,
        @Deprecated(
            message = "Use AppSettings.translateConfig.preTranslate instead.",
            level = DeprecationLevel.ERROR,
        )
        val preTranslation: Boolean = false,
        val agent: Boolean = true,
    ) {
        public companion object {
            // for iOS
            public val default: AiConfig = AiConfig()
        }

        @Serializable
        public sealed interface Type {
            @Serializable
            public data object OnDevice : Type

            @Serializable
            public data class OpenAI(
                val serverUrl: String,
                val apiKey: String,
                val model: String = "",
                val reasoningEffort: String = "",
                val extraBody: String = "",
            ) : Type
        }
    }
}

@Serializable
public enum class TimelineAutoRefreshInterval(
    public val minutes: Int,
) {
    DISABLED(0),
    FIVE_MINUTES(5),
    FIFTEEN_MINUTES(15),
    THIRTY_MINUTES(30),
    ONE_HOUR(60),
    ONE_MINUTE(1),
}
