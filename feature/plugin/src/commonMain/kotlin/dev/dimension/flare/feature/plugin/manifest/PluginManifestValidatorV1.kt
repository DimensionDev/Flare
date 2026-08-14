package dev.dimension.flare.feature.plugin.manifest

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.wire.requireValid

public data class ManifestValidationV1(
    val errors: List<ManifestIssueV1>,
    val warnings: List<ManifestIssueV1>,
) {
    public val isValid: Boolean
        get() = errors.isEmpty()

    public fun requireValid() {
        require(isValid) { errors.joinToString(separator = "; ") { "${it.path}: ${it.message}" } }
    }
}

public data class ManifestIssueV1(
    val code: String,
    val path: String,
    val message: String,
)

public fun PluginManifestV1.validate(methodTable: PluginMethodTableV1? = null): ManifestValidationV1 {
    val errors = mutableListOf<ManifestIssueV1>()
    val warnings = mutableListOf<ManifestIssueV1>()

    fun check(
        condition: Boolean,
        code: String,
        path: String,
        message: String,
    ) {
        if (!condition) errors += ManifestIssueV1(code, path, message)
    }

    check(schemaVersion == PluginAbiV1.MANIFEST_SCHEMA_VERSION, "manifest.schema", "schemaVersion", "Unsupported schema")
    check(apiVersion == PluginAbiV1.API_VERSION, "manifest.api", "apiVersion", "Unsupported API version")
    check(PLUGIN_ID.matches(id), "manifest.id", "id", "Invalid plugin ID")
    check(SEMVER.matches(version), "manifest.version", "version", "Invalid semantic version")
    check(isValidLocaleTag(defaultLocale), "manifest.locale", "defaultLocale", "Invalid default locale")
    validateText(name, "name", errors)
    description?.let { validateText(it, "description", errors) }

    check(PLATFORM_ID.matches(platform.id), "platform.id", "platform.id", "Invalid platform ID")
    validateText(platform.name, "platform.name", errors)
    platform.description?.let { validateText(it, "platform.description", errors) }
    check(
        platform.credentialSchemaVersion in 1..1_000_000,
        "platform.credentialSchema",
        "platform.credentialSchemaVersion",
        "Invalid credential schema version",
    )
    check(
        platform.timelineSchemaVersion in 1..1_000_000,
        "platform.timelineSchema",
        "platform.timelineSchemaVersion",
        "Invalid timeline schema version",
    )
    platform.detector?.let {
        check(it.priority in -10_000..10_000, "detector.priority", "platform.detector.priority", "Invalid detector priority")
    }

    check(permissions.authOrigins.size <= 16, "permission.count", "permissions.authOrigins", "Too many auth origins")
    permissions.authOrigins.forEachIndexed { index, origin ->
        check(origin.isExactHttpsOrigin(), "permission.origin", "permissions.authOrigins[$index]", "Auth origin must be exact HTTPS origin")
    }

    check(platform.capabilities.size <= 32, "capability.count", "platform.capabilities", "Too many capabilities")
    platform.capabilities.forEach { (capabilityId, declaration) ->
        val path = "platform.capabilities.$capabilityId"
        check(CAPABILITY_ID.matches(capabilityId), "capability.id", path, "Invalid capability ID")
        check(declaration.operations.isNotEmpty(), "capability.empty", path, "Capability has no operations")
        check(declaration.operations.size <= 32, "capability.operations", path, "Too many operations")
        val knownOperations = PluginAbiV1.knownCapabilityOperations[capabilityId]
        if (knownOperations == null) {
            warnings += ManifestIssueV1("capability.unknown", path, "Unknown capability is ignored by this Host")
        } else {
            check(
                declaration.operations.keys.containsAll(PluginAbiV1.requiredCapabilityOperations.getValue(capabilityId)),
                "capability.incomplete",
                path,
                "Capability is missing operations required by the Host interface",
            )
            declaration.operations.forEach { (operation, config) ->
                check(operation in knownOperations, "capability.operation", "$path.$operation", "Unknown operation")
                check(config.directions.size <= 3, "capability.directions", "$path.$operation.directions", "Invalid paging directions")
            }
        }
        when (capabilityId) {
            PluginAbiV1.Capabilities.NOTIFICATION -> {
                check(
                    "page" !in declaration.operations || declaration.notificationFilters.isNotEmpty(),
                    "notification.filters",
                    "$path.notificationFilters",
                    "Notification page requires at least one static filter",
                )
                check(
                    declaration.relationActions.isEmpty(),
                    "capability.config",
                    "$path.relationActions",
                    "Relation actions are only valid for the relation capability",
                )
            }

            PluginAbiV1.Capabilities.RELATION -> {
                check(
                    declaration.relationActions.isNotEmpty(),
                    "relation.actions",
                    "$path.relationActions",
                    "Relation capability requires at least one static action",
                )
                check(
                    declaration.notificationFilters.isEmpty(),
                    "capability.config",
                    "$path.notificationFilters",
                    "Notification filters are only valid for the notification capability",
                )
            }

            else -> {
                check(
                    declaration.notificationFilters.isEmpty() && declaration.relationActions.isEmpty(),
                    "capability.config",
                    path,
                    "Capability-specific configuration is not valid here",
                )
            }
        }
    }

    validateUniqueIds(platform.timelines.map(TimelineManifestV1::id), "platform.timelines", errors)
    platform.timelines.forEachIndexed { index, timeline ->
        val path = "platform.timelines[$index]"
        check(ID.matches(timeline.id), "timeline.id", "$path.id", "Invalid timeline ID")
        validateText(timeline.title, "$path.title", errors)
        validateParameters(timeline.parameters, "$path.parameters", errors)
    }
    if (platform.timelines.isNotEmpty()) {
        check(
            platform.hasOperation(PluginAbiV1.Capabilities.TIMELINE, "page"),
            "timeline.capability",
            "platform.timelines",
            "Timeline page operation is required",
        )
        check(
            platform.timelines.any(TimelineManifestV1::defaultForNewAccount),
            "timeline.default",
            "platform.timelines",
            "At least one timeline must be a default for new accounts",
        )
    } else if (platform.hasOperation(PluginAbiV1.Capabilities.TIMELINE, "page")) {
        check(false, "timeline.missing", "platform.timelines", "Timeline capability requires a declared timeline")
    }

    validateUniqueIds(platform.profileTabs.map(ProfileTabManifestV1::id), "platform.profileTabs", errors)
    platform.profileTabs.forEachIndexed { index, tab ->
        val path = "platform.profileTabs[$index]"
        check(ID.matches(tab.id), "profileTab.id", "$path.id", "Invalid profile tab ID")
        validateText(tab.title, "$path.title", errors)
        validateParameters(tab.parameters, "$path.parameters", errors)
    }
    if (platform.profileTabs.isNotEmpty()) {
        check(
            platform.hasOperation(PluginAbiV1.Capabilities.PROFILE, "timeline"),
            "profileTab.capability",
            "platform.profileTabs",
            "Profile timeline operation is required",
        )
    }

    validateUniqueIds(platform.loginMethods.map(LoginMethodManifestV1::id), "platform.loginMethods", errors)
    platform.loginMethods
        .groupBy(LoginMethodManifestV1::interaction)
        .filterValues { it.size > 1 }
        .forEach { (interaction, methods) ->
            errors +=
                ManifestIssueV1(
                    code = "login.interaction.duplicate",
                    path = "platform.loginMethods",
                    message =
                        "Only one $interaction login method is supported; found " +
                            methods.joinToString { it.id },
                )
        }
    platform.loginMethods.forEachIndexed { index, method -> validateLogin(method, index, permissions, errors) }
    check(platform.deepLinks.size <= 64, "deepLink.count", "platform.deepLinks", "Too many Deep Link rules")
    val timelineIds = platform.timelines.mapTo(hashSetOf(), TimelineManifestV1::id)
    platform.deepLinks.forEachIndexed { index, rule -> validateDeepLink(rule, index, permissions, timelineIds, errors) }
    platform.composeDefaults?.let {
        runCatching(it::requireValid).onFailure { error ->
            errors += ManifestIssueV1("compose.config", "platform.composeDefaults", error.message ?: "Invalid Compose config")
        }
        check(
            platform.hasOperation(PluginAbiV1.Capabilities.COMPOSE, "publish"),
            "compose.capability",
            "platform.composeDefaults",
            "Compose publish operation is required",
        )
    }

    methodTable?.let { table ->
        check(table.apiVersion == apiVersion, "methods.api", "methodTable.apiVersion", "Method table API mismatch")
        requiredMethods().forEach { method ->
            check(method in table.methods, "methods.missing", "methodTable.methods", "Missing method $method")
        }
        table.methods.forEach { method ->
            check(METHOD_PATH.matches(method), "methods.path", "methodTable.methods", "Invalid method path $method")
        }
    }

    return ManifestValidationV1(errors = errors, warnings = warnings)
}

public fun PluginManifestV1.requiredMethods(): Set<String> =
    buildSet {
        if (platform.detector != null) add("detector.detect")
        platform.loginMethods.forEach { method ->
            when (method.interaction) {
                LoginInteractionV1.OAuth -> {
                    add("login.${method.id}.begin")
                    add("login.${method.id}.resume")
                }

                LoginInteractionV1.WebCookie -> {
                    add("login.${method.id}.begin")
                    add("login.${method.id}.check")
                }

                LoginInteractionV1.Password,
                LoginInteractionV1.CredentialImport,
                LoginInteractionV1.Form,
                -> {
                    add("login.${method.id}.begin")
                }
            }
        }
        platform.capabilities.forEach { (capability, declaration) ->
            declaration.operations.keys.mapNotNullTo(this) { operation -> PluginAbiV1.capabilityMethod(capability, operation) }
        }
    }

private fun validateLogin(
    method: LoginMethodManifestV1,
    index: Int,
    permissions: PluginPermissionsV1,
    errors: MutableList<ManifestIssueV1>,
) {
    val path = "platform.loginMethods[$index]"
    addErrorUnless(ID.matches(method.id), errors, "login.id", "$path.id", "Invalid login method ID")
    validateText(method.title, "$path.title", errors)
    method.description?.let { validateText(it, "$path.description", errors) }
    validateUniqueIds(method.fields.map(LoginFieldManifestV1::id), "$path.fields", errors)
    method.fields.forEachIndexed { fieldIndex, field ->
        val fieldPath = "$path.fields[$fieldIndex]"
        addErrorUnless(ID.matches(field.id), errors, "login.field.id", "$fieldPath.id", "Invalid field ID")
        validateText(field.label, "$fieldPath.label", errors)
        field.placeholder?.let { validateText(it, "$fieldPath.placeholder", errors) }
    }
    when (method.interaction) {
        LoginInteractionV1.OAuth -> {
            addErrorUnless(method.fields.isEmpty(), errors, "login.oauth.fields", "$path.fields", "OAuth cannot declare form fields")
            addErrorUnless(method.cookie == null, errors, "login.oauth.cookie", "$path.cookie", "OAuth cannot declare Cookie probes")
        }

        LoginInteractionV1.WebCookie -> {
            addErrorUnless(method.fields.isEmpty(), errors, "login.cookie.fields", "$path.fields", "WebCookie cannot declare form fields")
            val cookie = method.cookie
            addErrorUnless(cookie != null, errors, "login.cookie.config", "$path.cookie", "WebCookie requires Cookie configuration")
            cookie?.let { validateCookie(it, path, permissions, errors) }
        }

        LoginInteractionV1.Password,
        LoginInteractionV1.CredentialImport,
        LoginInteractionV1.Form,
        -> {
            addErrorUnless(method.fields.isNotEmpty(), errors, "login.fields", "$path.fields", "Login method requires fields")
            addErrorUnless(
                method.cookie == null,
                errors,
                "login.cookie.unexpected",
                "$path.cookie",
                "Only WebCookie may declare Cookie probes",
            )
        }
    }
}

private fun validateCookie(
    cookie: WebCookieManifestV1,
    parentPath: String,
    permissions: PluginPermissionsV1,
    errors: MutableList<ManifestIssueV1>,
) {
    addErrorUnless(
        cookie.startUrl.isHttpsUrl(),
        errors,
        "cookie.startUrl",
        "$parentPath.cookie.startUrl",
        "Cookie start URL must use HTTPS",
    )
    val startOrigin = cookie.startUrl.approvedOriginOrNull()
    addErrorUnless(
        startOrigin == PluginAbiV1.ACCOUNT_ORIGIN || startOrigin in permissions.authOrigins,
        errors,
        "cookie.startUrl.origin",
        "$parentPath.cookie.startUrl",
        "Cookie start URL origin is not approved",
    )
    addErrorUnless(
        cookie.probes.isNotEmpty() && cookie.probes.size <= 16,
        errors,
        "cookie.probes",
        "$parentPath.cookie.probes",
        "Invalid Cookie probe count",
    )
    cookie.probes.forEachIndexed { index, probe ->
        val path = "$parentPath.cookie.probes[$index]"
        addErrorUnless(probe.url.isHttpsUrl(), errors, "cookie.probe.url", "$path.url", "Cookie probe URL must use HTTPS")
        val origin = probe.url.approvedOriginOrNull()
        addErrorUnless(
            origin == PluginAbiV1.ACCOUNT_ORIGIN || origin in permissions.authOrigins,
            errors,
            "cookie.probe.origin",
            "$path.url",
            "Cookie probe origin is not approved",
        )
        addErrorUnless(
            probe.cookies.isNotEmpty() && probe.cookies.size <= 32,
            errors,
            "cookie.names",
            "$path.cookies",
            "Invalid Cookie requirement count",
        )
        addErrorUnless(
            probe.cookies
                .map {
                    it.name.lowercase()
                }.distinct()
                .size == probe.cookies.size,
            errors,
            "cookie.names.duplicate",
            "$path.cookies",
            "Duplicate Cookie name",
        )
        probe.cookies.forEachIndexed { cookieIndex, requirement ->
            addErrorUnless(
                COOKIE_NAME.matches(requirement.name),
                errors,
                "cookie.name",
                "$path.cookies[$cookieIndex].name",
                "Invalid Cookie name",
            )
        }
    }
}

private fun validateDeepLink(
    rule: DeepLinkManifestV1,
    index: Int,
    permissions: PluginPermissionsV1,
    timelineIds: Set<String>,
    errors: MutableList<ManifestIssueV1>,
) {
    val path = "platform.deepLinks[$index]"
    addErrorUnless(
        rule.origin == PluginAbiV1.ACCOUNT_ORIGIN || rule.origin in permissions.authOrigins,
        errors,
        "deepLink.origin",
        "$path.origin",
        "Deep Link origin is not approved",
    )
    addErrorUnless(rule.path.isNotEmpty() && rule.path.size <= 32, errors, "deepLink.path", "$path.path", "Invalid Deep Link path")
    val captures = mutableSetOf<String>()
    rule.path.forEachIndexed { segmentIndex, segment ->
        when (segment) {
            is DeepLinkPathSegmentV1.Literal -> {
                addErrorUnless(
                    PATH_SEGMENT.matches(segment.value),
                    errors,
                    "deepLink.literal",
                    "$path.path[$segmentIndex]",
                    "Invalid literal segment",
                )
            }

            is DeepLinkPathSegmentV1.Capture -> {
                addErrorUnless(ID.matches(segment.name), errors, "deepLink.capture", "$path.path[$segmentIndex]", "Invalid capture name")
                addErrorUnless(
                    captures.add(segment.name),
                    errors,
                    "deepLink.capture.duplicate",
                    "$path.path[$segmentIndex]",
                    "Duplicate capture name",
                )
            }
        }
    }
    val value = rule.target.value
    when (rule.target.type) {
        DeepLinkTargetTypeV1.Profile,
        DeepLinkTargetTypeV1.Post,
        -> {
            addErrorUnless(
                value != null && value.referencesOnly(captures),
                errors,
                "deepLink.target",
                "$path.target.value",
                "Entity target must reference captured values",
            )
        }

        DeepLinkTargetTypeV1.Timeline -> {
            addErrorUnless(
                value != null && value in timelineIds,
                errors,
                "deepLink.timeline",
                "$path.target.value",
                "Timeline target must reference a declared timeline",
            )
        }

        DeepLinkTargetTypeV1.Browser -> {
            addErrorUnless(
                value == null,
                errors,
                "deepLink.browser",
                "$path.target.value",
                "Browser target does not accept a value",
            )
        }
    }
}

private fun validateText(
    text: PluginTextV1,
    path: String,
    errors: MutableList<ManifestIssueV1>,
) {
    runCatching { text.requireValid(path) }.onFailure {
        errors += ManifestIssueV1("text.invalid", path, it.message ?: "Invalid text")
    }
}

private fun validateUniqueIds(
    ids: List<String>,
    path: String,
    errors: MutableList<ManifestIssueV1>,
) {
    addErrorUnless(ids.distinct().size == ids.size, errors, "id.duplicate", path, "Duplicate ID")
}

private fun validateParameters(
    parameters: Map<String, String>,
    path: String,
    errors: MutableList<ManifestIssueV1>,
) {
    addErrorUnless(parameters.size <= 32, errors, "parameters.count", path, "Too many parameters")
    addErrorUnless(
        parameters.all { (key, value) ->
            ID.matches(key) && value.length <= 4_096
        },
        errors,
        "parameters.invalid",
        path,
        "Invalid parameters",
    )
}

private fun addErrorUnless(
    condition: Boolean,
    errors: MutableList<ManifestIssueV1>,
    code: String,
    path: String,
    message: String,
) {
    if (!condition) errors += ManifestIssueV1(code, path, message)
}

private fun PluginPlatformManifestV1.hasOperation(
    capability: String,
    operation: String,
): Boolean = capability in capabilities && operation in capabilities.getValue(capability).operations

private fun String.referencesOnly(captures: Set<String>): Boolean {
    val matches = TARGET_CAPTURE.findAll(this).map { it.groupValues[1] }.toList()
    return matches.isNotEmpty() && matches.all(captures::contains) && TARGET_CAPTURE.replace(this, "").isEmpty()
}

private fun String.isExactHttpsOrigin(): Boolean = httpsOriginOrNull() == this.removeSuffix("/")

private fun String.isHttpsUrl(): Boolean = approvedOriginOrNull() != null

private fun String.approvedOriginOrNull(): String? =
    if (this == PluginAbiV1.ACCOUNT_ORIGIN || startsWith("${PluginAbiV1.ACCOUNT_ORIGIN}/")) {
        PluginAbiV1.ACCOUNT_ORIGIN
    } else {
        httpsOriginOrNull()
    }

private fun String.httpsOriginOrNull(): String? {
    val match = HTTPS_URL.matchEntire(this) ?: return null
    val host = match.groupValues[1]
    if (!host.isValidDnsHost()) return null
    val port = match.groupValues[2]
    if (port.isNotEmpty()) {
        val parsedPort = port.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1..65_535) return null
    }
    val origin = "https://${host.lowercase()}${if (port.isEmpty()) "" else ":$port"}"
    return origin
}

private fun String.isValidDnsHost(): Boolean =
    length <= 253 &&
        split('.').all { label ->
            label.length in 1..63 &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }

private val PLUGIN_ID = Regex("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9-]*)+")
private val PLATFORM_ID = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
private val ID = Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")
private val CAPABILITY_ID = Regex("[a-z0-9.-]+/[a-z0-9.-]+")
private val METHOD_PATH = Regex("[A-Za-z][A-Za-z0-9_-]*(?:\\.[A-Za-z][A-Za-z0-9_-]*)+")
private val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}")
private val PATH_SEGMENT = Regex("[^/\\s?#]{1,256}")
private val TARGET_CAPTURE = Regex("\\{([A-Za-z][A-Za-z0-9_.-]{0,127})\\}")
private val HTTPS_URL = Regex("https://([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?(?:/[^\\s]*)?")
private const val SEMVER_IDENTIFIER = "(?:0|[1-9][0-9]*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)"
private val SEMVER =
    Regex(
        "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)" +
            "(?:-$SEMVER_IDENTIFIER(?:\\.$SEMVER_IDENTIFIER)*)?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?",
    )
