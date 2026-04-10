package dev.dimension.flare.gradle

import org.gradle.api.Project

open class FlareExtension internal constructor(
    private val project: Project,
    private val configurePlatforms: (Set<FlarePlatform>) -> Unit,
) {
    var namespace: String? = null

    fun platforms(vararg platforms: FlarePlatform) {
        configurePlatforms(platforms.toSet())
    }

    internal fun requireNamespace(): String =
        requireNotNull(namespace) {
            "flare.namespace is required when ${project.path} enables ${FlarePlatform.Android}."
        }
}
