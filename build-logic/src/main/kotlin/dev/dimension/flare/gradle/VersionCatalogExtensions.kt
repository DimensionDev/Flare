package dev.dimension.flare.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.flareCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.flareLibrary(alias: String): Provider<MinimalExternalModuleDependency> =
    flareCatalog.findLibrary(alias).orElseThrow {
        IllegalArgumentException("Library alias '$alias' is not defined in libs.versions.toml.")
    }

internal fun Project.flarePluginId(alias: String): String =
    flareCatalog.findPlugin(alias).orElseThrow {
        IllegalArgumentException("Plugin alias '$alias' is not defined in libs.versions.toml.")
    }.get().pluginId

internal fun Project.flareVersion(alias: String): String =
    flareCatalog.findVersion(alias).orElseThrow {
        IllegalArgumentException("Version '$alias' is not defined in libs.versions.toml.")
    }.requiredVersion

internal fun Project.flareVersionInt(alias: String): Int =
    flareVersion(alias).toInt()
