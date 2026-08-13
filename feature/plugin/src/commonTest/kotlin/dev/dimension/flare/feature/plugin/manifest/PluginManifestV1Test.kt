package dev.dimension.flare.feature.plugin.manifest

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTextArgument
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginManifestV1Test {
    @Test
    fun duplicateLoginInteractionIsRejected() {
        val base = PluginJsonV1.decodeFromString<PluginManifestV1>(VALID_MANIFEST)
        val manifest =
            base.copy(
                platform =
                    base.platform.copy(
                        loginMethods =
                            listOf(
                                LoginMethodManifestV1(
                                    id = "token-a",
                                    interaction = LoginInteractionV1.Form,
                                    title = PluginTextV1.Literal("Token A"),
                                    fields =
                                        listOf(
                                            LoginFieldManifestV1(
                                                id = "token",
                                                type = LoginFieldTypeV1.Secret,
                                                label = PluginTextV1.Literal("Token"),
                                            ),
                                        ),
                                ),
                                LoginMethodManifestV1(
                                    id = "token-b",
                                    interaction = LoginInteractionV1.Form,
                                    title = PluginTextV1.Literal("Token B"),
                                    fields =
                                        listOf(
                                            LoginFieldManifestV1(
                                                id = "token",
                                                type = LoginFieldTypeV1.Secret,
                                                label = PluginTextV1.Literal("Token"),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )

        assertTrue(manifest.validate().errors.any { it.code == "login.interaction.duplicate" })
    }

    @Test
    fun parsesLiteralAndLocalizedManifestText() {
        val manifest = PluginJsonV1.decodeFromString<PluginManifestV1>(VALID_MANIFEST)

        assertEquals(PluginTextV1.Literal("Pixelfed sample"), manifest.name)
        assertEquals(
            PluginTextV1.Localized("platform.name", "Pixelfed"),
            manifest.platform.name,
        )
        assertTrue(manifest.validate(validMethodTable()).isValid)
    }

    @Test
    fun knownCapabilityMissingMethodIsRejected() {
        val manifest = PluginJsonV1.decodeFromString<PluginManifestV1>(VALID_MANIFEST)
        val result =
            manifest.validate(
                validMethodTable().copy(methods = validMethodTable().methods - "capabilities.compose.publish"),
            )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == "methods.missing" })
    }

    @Test
    fun unknownCapabilityIsPreservedAsWarning() {
        val manifest = PluginJsonV1.decodeFromString<PluginManifestV1>(VALID_MANIFEST)
        val unknown =
            manifest.copy(
                platform =
                    manifest.platform.copy(
                        capabilities =
                            manifest.platform.capabilities +
                                (
                                    "example.datasource.future/v9" to
                                        CapabilityManifestV1(
                                            mapOf("read" to CapabilityOperationManifestV1()),
                                        )
                                ),
                    ),
            )

        val result = unknown.validate(validMethodTable())

        assertTrue(result.isValid)
        assertEquals(listOf("capability.unknown"), result.warnings.map(ManifestIssueV1::code))
    }

    @Test
    fun authOriginRejectsInvalidHostAndPort() {
        val manifest = PluginJsonV1.decodeFromString<PluginManifestV1>(VALID_MANIFEST)

        listOf("https://bad..host", "https://example.com:99999").forEach { origin ->
            val result =
                manifest
                    .copy(permissions = PluginPermissionsV1(authOrigins = setOf(origin)))
                    .validate(validMethodTable())

            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.code == "permission.origin" })
        }
    }

    @Test
    fun cookieStartUrlMustUseAnApprovedOrigin() {
        val manifest = PluginJsonV1.decodeFromString<PluginManifestV1>(VALID_MANIFEST)
        val cookieMethod =
            LoginMethodManifestV1(
                id = "cookie",
                interaction = LoginInteractionV1.WebCookie,
                title = PluginTextV1.Literal("Cookie"),
                cookie =
                    WebCookieManifestV1(
                        startUrl = "https://unapproved.example/login",
                        probes =
                            listOf(
                                CookieProbeManifestV1(
                                    url = "${PluginAbiV1.ACCOUNT_ORIGIN}/cookie",
                                    cookies = listOf(CookieRequirementManifestV1("session")),
                                ),
                            ),
                    ),
            )
        val result =
            manifest
                .copy(platform = manifest.platform.copy(loginMethods = manifest.platform.loginMethods + cookieMethod))
                .validate(
                    validMethodTable().copy(
                        methods = validMethodTable().methods + setOf("login.cookie.begin", "login.cookie.check"),
                    ),
                )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == "cookie.startUrl.origin" })
    }

    @Test
    fun catalogUsesBcp47FallbackAndNamedArguments() {
        val bundle =
            PluginCatalogBundleV1(
                pluginId = "dev.dimension.flare.sample.pixelfed",
                defaultLocale = "en",
                catalogs =
                    mapOf(
                        "en" to mapOf("welcome" to "Welcome, {name}: {count}"),
                        "zh-Hans" to mapOf("welcome" to "欢迎，{name}：{count}"),
                    ),
            )
        val text =
            UiText.ExternalRef(
                namespace = bundle.pluginId,
                key = "welcome",
                fallback = "Hello, {name}",
                args =
                    mapOf(
                        "name" to UiTextArgument.StringValue("Flare"),
                        "count" to UiTextArgument.NumberValue(2.0),
                    ),
            )

        assertEquals("欢迎，Flare：2", bundle.resolve(text, "zh-Hans-CN"))
        assertEquals("Welcome, Flare: 2", bundle.resolve(text, "fr-FR"))
        assertIs<UiText.ExternalRef>(PluginTextV1.Localized("welcome", "Hello").toUiText(bundle.pluginId))
    }

    private fun validMethodTable(): PluginMethodTableV1 =
        PluginMethodTableV1(
            apiVersion = 1,
            methods =
                setOf(
                    "detector.detect",
                    "login.oauth.begin",
                    "login.oauth.resume",
                    "capabilities.timeline.page",
                    "capabilities.profile.timeline",
                    "capabilities.compose.publish",
                ),
        )

    private companion object {
        val VALID_MANIFEST =
            """
            {
              "schemaVersion": 1,
              "apiVersion": 1,
              "id": "dev.dimension.flare.sample.pixelfed",
              "version": "0.1.0",
              "defaultLocale": "en",
              "name": "Pixelfed sample",
              "platform": {
                "id": "Pixelfed",
                "name": { "key": "platform.name", "fallback": "Pixelfed" },
                "detector": { "priority": 100 },
                "loginMethods": [
                  { "id": "oauth", "interaction": "OAuth", "title": "OAuth" }
                ],
                "capabilities": {
                  "${PluginAbiV1.Capabilities.TIMELINE}": {
                    "operations": { "page": { "directions": ["refresh", "older"] } }
                  },
                  "${PluginAbiV1.Capabilities.PROFILE}": {
                    "operations": { "timeline": { "directions": ["refresh", "older"] } }
                  },
                  "${PluginAbiV1.Capabilities.COMPOSE}": {
                    "operations": { "publish": {} }
                  }
                },
                "timelines": [
                  { "id": "home", "title": "Home", "defaultForNewAccount": true }
                ],
                "profileTabs": [
                  { "id": "posts", "title": "Posts" }
                ],
                "deepLinks": [
                  {
                    "path": [{ "type": "capture", "name": "username" }],
                    "target": { "type": "Profile", "value": "{username}" }
                  }
                ],
                "composeDefaults": {
                  "text": { "maxLength": 500 },
                  "media": {
                    "minCountForNew": 1,
                    "maxCount": 4,
                    "maxBytes": 10485760,
                    "supportedMimeTypes": ["image/jpeg", "image/png"]
                  },
                  "visibility": {
                    "allowed": ["public", "unlisted", "followers"],
                    "default": "public"
                  }
                }
              }
            }
            """.trimIndent()
    }
}
