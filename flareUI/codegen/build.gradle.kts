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

val generatedGlueFiles =
    listOf(
        repositoryRoot.file(
            "flareUI/android-compose/build/generated/flareui/kotlin/" +
                "dev/dimension/flare/flareui/compose/GeneratedComposeWidgets.kt",
        ),
        repositoryRoot.file(
            "flareUI/android-view/build/generated/flareui/kotlin/" +
                "dev/dimension/flare/flareui/view/GeneratedViewWidgets.kt",
        ),
        repositoryRoot.file(
            "flareUI/apple/Sources/SwiftUI/Generated/FlareSwiftUINode.generated.swift",
        ),
        repositoryRoot.file(
            "flareUI/apple/Sources/UIKit/Generated/FlareUIKitNodeFactory.generated.swift",
        ),
        repositoryRoot.file(
            "flareUI/apple/Sources/AppKit/Generated/FlareAppKitNodeFactory.generated.swift",
        ),
    )

val verifyFlareUiRenderers =
    tasks.register("verifyFlareUiRenderers") {
        group = "verification"
        description = "Fails when a generated Flare UI renderer scaffold is still incomplete."

        dependsOn(generateFlareUiCode)

        inputs.files(rendererDirectories)
        inputs.files(generatedGlueFiles)
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

            val coverageRules =
                listOf<Pair<Regex, (String) -> String>>(
                    Regex("(.+)ComposeRenderer\\.kt") to
                        { component -> "render${component}Compose(" },
                    Regex("(.+)ViewRenderer\\.kt") to
                        { component -> "create${component}View(context)" },
                    Regex("FlareSwiftUI(.+)Renderer\\.swift") to
                        { component -> "FlareSwiftUI${component}Renderer(" },
                    Regex("FlareUIKit(.+)Renderer\\.swift") to
                        { component -> "makeFlareUIKit${component}View(" },
                    Regex("FlareAppKit(.+)Renderer\\.swift") to
                        { component -> "makeFlareAppKit${component}View(" },
                )
            val missingRegistrations =
                coverageRules.flatMapIndexed { index, (rendererName, expectedToken) ->
                    val generatedFile = generatedGlueFiles[index].asFile
                    val generatedContent = generatedFile.readText()
                    rendererDirectories[index].asFile
                        .walkTopDown()
                        .filter(File::isFile)
                        .mapNotNull { file ->
                            rendererName.matchEntire(file.name)?.groupValues?.get(1)
                        }.filter { component ->
                            expectedToken(component) !in generatedContent
                        }.map { component ->
                            "${generatedFile.relativeTo(repositoryRoot.asFile)} " +
                                "is missing $component"
                        }.toList()
                }
            check(missingRegistrations.isEmpty()) {
                missingRegistrations.joinToString(
                    prefix = "Generated Flare UI glue has incomplete component coverage:\n- ",
                    separator = "\n- ",
                )
            }
        }
    }

tasks.named("check") {
    dependsOn(verifyFlareUiRenderers)
}
