plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)

    testImplementation(kotlin("test"))
}

val repositoryRoot = rootProject.layout.projectDirectory

val generateFlareUiCode =
    tasks.register("generateFlareUiCode") {
        group = "flare ui"
        description = "Generates Flare UI registries, Apple payloads, and native router glue."

        dependsOn(":flareUI:core:kspKotlinJvm")
    }

val rendererDirectories =
    listOf(
        repositoryRoot.dir(
            "flareUI/android-compose/src/androidMain/kotlin/" +
                "dev/dimension/flare/flareui/compose/renderers",
        ),
        repositoryRoot.dir(
            "flareUI/android-view/src/androidMain/kotlin/" +
                "dev/dimension/flare/flareui/view/renderers",
        ),
        repositoryRoot.dir("flareUI/apple/Sources/SwiftUI/Renderers"),
        repositoryRoot.dir("flareUI/apple/Sources/UIKit/Renderers"),
        repositoryRoot.dir("flareUI/apple/Sources/AppKit/Renderers"),
    )

val verifyFlareUiRenderers =
    tasks.register("verifyFlareUiRenderers") {
        group = "verification"
        description = "Fails when a generated Flare UI renderer scaffold is still incomplete."

        dependsOn(generateFlareUiCode)

        inputs.files(rendererDirectories)
        doLast {
            val incomplete =
                rendererDirectories
                    .flatMap { directory ->
                        directory.asFile
                            .walkTopDown()
                            .filter(File::isFile)
                            .filter { file ->
                                file.readText().contains("FLARE_UI_RENDERER_TODO")
                            }.map { file -> file.relativeTo(repositoryRoot.asFile) }
                            .toList()
                    }
            check(incomplete.isEmpty()) {
                incomplete.joinToString(
                    prefix = "Incomplete Flare UI renderers:\n- ",
                    separator = "\n- ",
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(verifyFlareUiRenderers)
}
