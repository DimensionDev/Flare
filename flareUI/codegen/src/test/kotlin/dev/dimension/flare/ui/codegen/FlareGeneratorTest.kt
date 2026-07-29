package dev.dimension.flare.ui.codegen

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FlareGeneratorTest {
    @Test
    fun rendersCallablePrimitiveAndStronglyTypedWidgetContract() {
        val generated =
            FlareGenerator.renderPrimitive(
                PrimitiveDefinition(
                    packageName = "dev.example",
                    specType = "dev.example.LabelSpec",
                    apiName = "Label",
                    debugName = "dev.example.Label",
                    parameters =
                        listOf(
                            PrimitiveParameter(
                                name = "text",
                                type = "kotlin.String",
                                kind = ParameterKind.Property,
                            ),
                            PrimitiveParameter(
                                name = "modifier",
                                type = "dev.dimension.flare.ui.FlareModifier",
                                kind = ParameterKind.Modifier,
                            ),
                        ),
                ),
            )

        assertContains(generated, "@dev.dimension.flare.ui.FlareWidgetContract")
        assertContains(generated, "public interface LabelWidget")
        assertContains(
            generated,
            "dev.dimension.flare.ui.FlareComponentType<LabelWidget>",
        )
        assertContains(generated, "public object Label : dev.example.LabelSpec")
        assertContains(generated, "componentType = LabelWidget.Type")
        assertContains(generated, "set(text, LabelWidget::setText)")
    }

    @Test
    fun normalizesNamesForGeneratedApis() {
        assertEquals("NativeButton", "native-button".toKotlinName())
        assertEquals("TrailingIcon", "trailing_icon".toKotlinName())
    }

    @Test
    fun rendersStronglyTypedRendererPluginFromNativeClasses() {
        val generated =
            FlareGenerator.renderRendererPlugin(
                RendererPluginDefinition(
                    packageName = "dev.example.android",
                    generatedName = "AndroidViewExampleRendererPlugin",
                    backendType = "dev.example.android.AndroidViewBackend",
                    actualProperty = false,
                    constructorParameters = emptyList(),
                    bindings =
                        listOf(
                            RendererBinding(
                                componentType = "dev.example.LabelWidget.Type",
                                rendererType = "dev.example.android.AndroidLabelWidget",
                                constructorArguments =
                                    listOf(
                                        ConstructorArgument(
                                            name = "backend",
                                            value = "backend",
                                        ),
                                    ),
                                isObject = false,
                            ),
                        ),
                ),
            )

        assertContains(generated, "public object AndroidViewExampleRendererPlugin")
        assertContains(
            generated,
            "FlareRendererPlugin<dev.example.android.AndroidViewBackend>",
        )
        assertContains(generated, "registrar.register(dev.example.LabelWidget.Type)")
        assertContains(generated, "backend = backend")
    }

    @Test
    fun rendersNativeRendererAsActualProperty() {
        val generated =
            FlareGenerator.renderRendererPlugin(
                RendererPluginDefinition(
                    packageName = "dev.example.uikit",
                    generatedName = "UIKitExampleRendererPlugin",
                    backendType = "dev.example.uikit.UIKitBackend",
                    actualProperty = true,
                    constructorParameters = emptyList(),
                    bindings =
                        listOf(
                            RendererBinding(
                                componentType = "dev.example.LabelWidget.Type",
                                rendererType = "dev.example.uikit.UIKitLabelWidget",
                                constructorArguments = emptyList(),
                                isObject = false,
                            ),
                        ),
                ),
            )

        assertContains(
            generated,
            "public actual val UIKitExampleRendererPlugin: " +
                "dev.dimension.flare.ui.FlareRendererPlugin<dev.example.uikit.UIKitBackend>",
        )
    }

    @Test
    fun rendersSwiftUINodesAndPluginFromCommonPrimitiveDefinitions() {
        val generated =
            FlareSwiftUIGenerator.renderPlugin(
                SwiftUIPluginDefinition(
                    moduleName = "Example",
                    kotlinModuleName = "FlareUI",
                    primitives =
                        listOf(
                            PrimitiveDefinition(
                                packageName = "dev.example",
                                specType = "dev.example.CardSpec",
                                apiName = "Card",
                                debugName = "dev.example.Card",
                                parameters =
                                    listOf(
                                        PrimitiveParameter(
                                            name = "title",
                                            type = "kotlin.String",
                                            kind = ParameterKind.Property,
                                        ),
                                        PrimitiveParameter(
                                            name = "content",
                                            type = "dev.dimension.flare.ui.FlareContent",
                                            kind = ParameterKind.Slot,
                                        ),
                                        PrimitiveParameter(
                                            name = "onClick",
                                            type = "kotlin.Function0<kotlin.Unit>",
                                            kind = ParameterKind.Property,
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        assertContains(generated, "@preconcurrency import FlareUI")
        assertContains(
            generated,
            "public nonisolated final class ExampleSwiftUIPlugin",
        )
        assertContains(
            generated,
            "registry.register(SwiftUICardNode.self)",
        )
        assertContains(
            generated,
            "final class SwiftUICardNode: FlareSwiftUINode, CardWidget",
        )
        assertContains(generated, "func setTitle(value: String)")
        assertContains(generated, "func setOnClick(value: @escaping () -> Void)")
        assertContains(generated, "func performOnClick()")
        assertContains(generated, "override func children(slot: FlareSlotId)")
    }
}
