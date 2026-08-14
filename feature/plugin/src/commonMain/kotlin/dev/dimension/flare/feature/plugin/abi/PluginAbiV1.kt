package dev.dimension.flare.feature.plugin.abi

public object PluginAbiV1 {
    public const val API_VERSION: Int = 1
    public const val MANIFEST_SCHEMA_VERSION: Int = 1
    public const val ICON_PATH: String = "assets/icon.png"
    public const val ENTRY_PATH: String = "plugin.js"
    public const val MANIFEST_PATH: String = "manifest.json"
    public const val ACCOUNT_ORIGIN: String = "\$accountOrigin"

    public object Capabilities {
        public const val TIMELINE: String = "flare.datasource.timeline/v1"
        public const val SEARCH: String = "flare.datasource.search/v1"
        public const val PROFILE: String = "flare.datasource.profile/v1"
        public const val POST: String = "flare.datasource.post/v1"
        public const val RELATION: String = "flare.datasource.relation/v1"
        public const val COMPOSE: String = "flare.datasource.compose/v1"
        public const val NOTIFICATION: String = "flare.datasource.notification/v1"
        public const val LIST: String = "flare.datasource.list/v1"
        public const val DIRECT_MESSAGE: String = "flare.datasource.direct-message/v1"
        public const val ARTICLE: String = "flare.datasource.article/v1"
        public const val GALLERY: String = "flare.datasource.gallery/v1"
        public const val TAB_CATALOG: String = "flare.datasource.tab-catalog/v1"
    }

    /** Exact operation names supported by API v1. */
    public val knownCapabilityOperations: Map<String, Set<String>> =
        mapOf(
            Capabilities.TIMELINE to setOf("page"),
            Capabilities.SEARCH to
                setOf(
                    "posts",
                    "profiles",
                    "discoverPosts",
                    "discoverProfiles",
                    "discoverHashtags",
                ),
            Capabilities.PROFILE to setOf("byId", "byHandle", "timeline", "following", "followers"),
            Capabilities.POST to setOf("detail", "context", "delete", "mutate"),
            Capabilities.RELATION to setOf("state", "mutate"),
            Capabilities.COMPOSE to setOf("publish"),
            Capabilities.NOTIFICATION to setOf("page", "badge"),
            Capabilities.LIST to
                setOf(
                    "page",
                    "detail",
                    "create",
                    "update",
                    "delete",
                    "timeline",
                    "members",
                    "memberships",
                    "addMember",
                    "removeMember",
                ),
            Capabilities.DIRECT_MESSAGE to
                setOf(
                    "rooms",
                    "room",
                    "messages",
                    "send",
                    "delete",
                    "leave",
                    "create",
                    "badge",
                    "canSend",
                ),
            Capabilities.ARTICLE to setOf("detail", "comments"),
            Capabilities.GALLERY to setOf("detail", "comments", "recommendations"),
            Capabilities.TAB_CATALOG to setOf("page"),
        )

    /** Operations required before a capability can be exposed through its Host interface. */
    public val requiredCapabilityOperations: Map<String, Set<String>> =
        knownCapabilityOperations.mapValues { (capability, operations) ->
            when (capability) {
                Capabilities.PROFILE -> setOf("byId", "byHandle")
                Capabilities.POST -> setOf("detail")
                Capabilities.NOTIFICATION -> emptySet()
                else -> operations
            }
        }

    public fun hasRequiredOperations(
        capabilityId: String,
        operations: Set<String>,
    ): Boolean = requiredCapabilityOperations[capabilityId]?.let(operations::containsAll) == true && operations.isNotEmpty()

    public fun isDisplayableCapability(
        capabilityId: String,
        operations: Set<String>,
    ): Boolean =
        capabilityId in DISPLAY_CAPABILITIES &&
            hasRequiredOperations(capabilityId, operations) &&
            (capabilityId != Capabilities.NOTIFICATION || "page" in operations)

    public fun capabilityMethod(
        capabilityId: String,
        operation: String,
    ): String? {
        val service =
            when (capabilityId) {
                Capabilities.TIMELINE -> "timeline"
                Capabilities.SEARCH -> "search"
                Capabilities.PROFILE -> "profile"
                Capabilities.POST -> "post"
                Capabilities.RELATION -> "relation"
                Capabilities.COMPOSE -> "compose"
                Capabilities.NOTIFICATION -> "notification"
                Capabilities.LIST -> "list"
                Capabilities.DIRECT_MESSAGE -> "directMessage"
                Capabilities.ARTICLE -> "article"
                Capabilities.GALLERY -> "gallery"
                Capabilities.TAB_CATALOG -> "tabCatalog"
                else -> return null
            }
        return "capabilities.$service.$operation"
    }

    private val DISPLAY_CAPABILITIES =
        setOf(
            Capabilities.TIMELINE,
            Capabilities.SEARCH,
            Capabilities.PROFILE,
            Capabilities.POST,
            Capabilities.NOTIFICATION,
            Capabilities.LIST,
            Capabilities.DIRECT_MESSAGE,
            Capabilities.ARTICLE,
            Capabilities.GALLERY,
            Capabilities.TAB_CATALOG,
        )
}
