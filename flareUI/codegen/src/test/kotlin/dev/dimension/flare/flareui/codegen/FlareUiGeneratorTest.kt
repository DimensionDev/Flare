package dev.dimension.flare.flareui.codegen

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FlareUiGeneratorTest {
    @Test
    fun generatesGlueAndNeverOverwritesRendererImplementations() {
        val repositoryRoot = Files.createTempDirectory("flare-ui-codegen-test").toFile()
        try {
            val components =
                listOf(
                    ComponentMetadata(
                        type = "example.TextType",
                        propsType = "example.TextProps",
                        properties =
                            listOf(
                                PropertyMetadata(
                                    name = "value",
                                    type = "kotlin.String",
                                ),
                            ),
                    ),
                )
            val workspace = GeneratorWorkspace(repositoryRoot)

            workspace.generate(components)

            val composeGlue =
                repositoryRoot.resolve(
                    "flareUI/android-compose/build/generated/flareui/kotlin/" +
                        "dev/dimension/flare/flareui/compose/GeneratedComposeWidgets.kt",
                )
            assertContains(composeGlue.readText(), "example.TextType")

            val renderer =
                repositoryRoot.resolve(
                    "flareUI/android-compose/src/androidMain/kotlin/" +
                        "dev/dimension/flare/flareui/compose/renderers/" +
                        "TextComposeRenderer.kt",
                )
            assertContains(renderer.readText(), INCOMPLETE_RENDERER_MARKER)

            renderer.writeText("handwritten renderer")
            workspace.generate(components)
            assertEquals("handwritten renderer", renderer.readText())
        } finally {
            repositoryRoot.deleteRecursively()
        }
    }
}
