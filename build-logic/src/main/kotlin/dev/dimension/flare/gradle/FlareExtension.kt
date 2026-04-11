package dev.dimension.flare.gradle

import org.gradle.api.Project

open class FlareExtension internal constructor(
    private val project: Project,
    private val configurePlatforms: (namespace: String, platforms:Set<FlarePlatform>) -> Unit,
) {
    fun platforms(namespace: String, vararg platforms: FlarePlatform) {
        configurePlatforms(namespace, platforms.toSet())
    }
}
