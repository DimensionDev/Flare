package dev.dimension.flare.flareui.codegen

import java.io.File

internal const val INCOMPLETE_RENDERER_MARKER = "FLARE_UI_RENDERER_TODO"

internal class GeneratorWorkspace(
    private val repositoryRoot: File,
) {
    fun generate(components: List<ComponentMetadata>) {
        writeGenerated(
            relativePath =
                "flareUI/android-compose/build/generated/flareui/kotlin/" +
                    "dev/dimension/flare/flareui/compose/GeneratedComposeWidgets.kt",
            content = renderComposeGlue(components),
        )
        writeGenerated(
            relativePath =
                "flareUI/android-view/build/generated/flareui/kotlin/" +
                    "dev/dimension/flare/flareui/view/GeneratedViewWidgets.kt",
            content = renderAndroidViewGlue(components),
        )
        writeGenerated(
            relativePath =
                "flareUI/apple-runtime/build/generated/flareui/kotlin/" +
                    "dev/dimension/flare/flareui/apple/GeneratedAppleWidgets.kt",
            content = renderAppleRuntimeGlue(components),
        )
        writeGenerated(
            relativePath =
                "flareUI/apple/Sources/SwiftUI/Generated/" +
                    "FlareSwiftUINode.generated.swift",
            content = renderSwiftUiRouter(components),
        )
        writeGenerated(
            relativePath =
                "flareUI/apple/Sources/UIKit/Generated/" +
                    "FlareUIKitNodeFactory.generated.swift",
            content =
                renderAppleViewFactory(
                    components = components,
                    toolkit = "UIKit",
                    viewType = "UIView",
                ),
        )
        writeGenerated(
            relativePath =
                "flareUI/apple/Sources/AppKit/Generated/" +
                    "FlareAppKitNodeFactory.generated.swift",
            content =
                renderAppleViewFactory(
                    components = components,
                    toolkit = "AppKit",
                    viewType = "NSView",
                ),
        )

        components
            .flatMap(::rendererTargets)
            .forEach { target ->
                writeScaffold(
                    relativePath = target.path,
                    content = target.content,
                )
            }
    }

    private fun rendererTargets(component: ComponentMetadata): List<RendererTarget> =
        listOf(
            RendererTarget(
                path =
                    "flareUI/android-compose/src/androidMain/kotlin/" +
                        "dev/dimension/flare/flareui/compose/renderers/" +
                        "${component.className}ComposeRenderer.kt",
                content = renderComposeScaffold(component),
            ),
            RendererTarget(
                path =
                    "flareUI/android-view/src/androidMain/kotlin/" +
                        "dev/dimension/flare/flareui/view/renderers/" +
                        "${component.className}ViewRenderer.kt",
                content = renderAndroidViewScaffold(component),
            ),
            RendererTarget(
                path =
                    "flareUI/apple/Sources/SwiftUI/Renderers/" +
                        "FlareSwiftUI${component.className}Renderer.swift",
                content = renderSwiftUiScaffold(component),
            ),
            RendererTarget(
                path =
                    "flareUI/apple/Sources/UIKit/Renderers/" +
                        "FlareUIKit${component.className}Renderer.swift",
                content =
                    renderAppleViewScaffold(
                        component = component,
                        toolkit = "UIKit",
                        viewType = "UIView",
                    ),
            ),
            RendererTarget(
                path =
                    "flareUI/apple/Sources/AppKit/Renderers/" +
                        "FlareAppKit${component.className}Renderer.swift",
                content =
                    renderAppleViewScaffold(
                        component = component,
                        toolkit = "AppKit",
                        viewType = "NSView",
                    ),
            ),
        )

    private fun writeGenerated(
        relativePath: String,
        content: String,
    ) {
        val file = File(repositoryRoot, relativePath)
        file.parentFile.mkdirs()
        if (!file.exists() || file.readText() != content) {
            file.writeText(content)
        }
    }

    private fun writeScaffold(
        relativePath: String,
        content: String,
    ) {
        val file = File(repositoryRoot, relativePath)
        if (file.exists()) return
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}

private data class RendererTarget(
    val path: String,
    val content: String,
)

private fun renderComposeGlue(components: List<ComponentMetadata>): String =
    buildString {
        appendGeneratedHeader()
        appendLine("package dev.dimension.flare.flareui.compose")
        appendLine()
        appendLine("import androidx.compose.runtime.Composable")
        appendLine("import androidx.compose.ui.UiComposable")
        appendLine("import dev.dimension.flare.flareui.AndroidFlareResourceResolver")
        appendLine("import dev.dimension.flare.flareui.WidgetRegistry")
        appendLine()
        appendLine("internal fun generatedComposeWidgetRegistry(): WidgetRegistry =")
        appendLine("    WidgetRegistry.build {")
        components.forEach { component ->
            appendLine("        bind(")
            appendLine("            ${component.type},")
            appendLine("            { ComposeTreeNode(${component.type}) },")
            appendLine("        ) { value = it }")
        }
        appendLine("    }")
        appendLine()
        appendLine("@Composable")
        appendLine("@UiComposable")
        appendLine("internal fun renderGeneratedComposeNode(")
        appendLine("    node: ComposeTreeNode,")
        appendLine("    resources: AndroidFlareResourceResolver,")
        appendLine(") {")
        appendLine("    when (node.type) {")
        components.forEach { component ->
            appendLine("        ${component.type} ->")
            appendLine(
                "            render${component.className}Compose(",
            )
            appendLine(
                "                props = node.requireValue<${component.propsType}>(),",
            )
            appendLine("                children = node.children,")
            appendLine("                resources = resources,")
            appendLine("            )")
        }
        appendLine()
        appendLine(
            "        else -> error(\"Unsupported Compose widget: \${node.type?.debugName}\")",
        )
        appendLine("    }")
        appendLine("}")
    }

private fun renderAndroidViewGlue(components: List<ComponentMetadata>): String =
    buildString {
        appendGeneratedHeader()
        appendLine("package dev.dimension.flare.flareui.view")
        appendLine()
        appendLine("import android.content.Context")
        appendLine("import dev.dimension.flare.flareui.AndroidFlareResourceResolver")
        appendLine("import dev.dimension.flare.flareui.WidgetRegistry")
        appendLine()
        appendLine("public fun androidViewWidgetRegistry(")
        appendLine("    context: Context,")
        appendLine("    resources: AndroidFlareResourceResolver,")
        appendLine("): WidgetRegistry =")
        appendLine("    WidgetRegistry.build {")
        components.forEach { component ->
            appendLine("        bind(")
            appendLine("            type = ${component.type},")
            appendLine(
                "            create = { create${component.className}View(context) },",
            )
            appendLine(
                "            update = { props: ${component.propsType} ->",
            )
            appendLine(
                "                update${component.className}View(this, props, resources)",
            )
            appendLine("            },")
            appendLine("        )")
        }
        appendLine("    }")
    }

private fun renderAppleRuntimeGlue(components: List<ComponentMetadata>): String =
    buildString {
        appendGeneratedHeader()
        appendLine("package dev.dimension.flare.flareui.apple")
        appendLine()
        appendLine("import dev.dimension.flare.flareui.WidgetRegistry")
        appendLine("import dev.dimension.flare.flareui.WidgetType")
        appendLine()
        appendLine("public enum class FlareUiNodeKind {")
        components.forEach { component ->
            appendLine("    ${component.kindName},")
        }
        appendLine("}")
        appendLine()
        appendLine(
            "public abstract class FlareUiNodePayload internal constructor()",
        )
        components.forEach { component ->
            appendLine()
            appendPayload(component)
        }
        appendLine()
        appendLine("internal fun generatedAppleWidgetRegistry(")
        appendLine(
            "    createNode: (WidgetType<*>) -> AppleTreeNode,",
        )
        appendLine("): WidgetRegistry =")
        appendLine("    WidgetRegistry.build {")
        components.forEach { component ->
            appendLine("        bind(")
            appendLine("            type = ${component.type},")
            appendLine("            create = { createNode(${component.type}) },")
            appendLine("            update = { setProps(it) },")
            appendLine("        )")
        }
        appendLine("    }")
        appendLine()
        appendLine(
            "internal fun AppleTreeNode.generatedSnapshot(): FlareUiNodeSnapshot =",
        )
        appendLine("    when (type) {")
        components.forEach { component ->
            appendLine("        ${component.type} -> {")
            if (component.properties.isNotEmpty()) {
                appendLine(
                    "            val props = requireProps<${component.propsType}>()",
                )
            }
            appendLine("            FlareUiNodeSnapshot(")
            appendLine("                id = id,")
            appendLine(
                "                kind = FlareUiNodeKind.${component.kindName},",
            )
            if (component.properties.isEmpty()) {
                appendLine(
                    "                payload = ${component.payloadName}(),",
                )
            } else {
                appendLine("                payload =")
                appendLine(
                    "                    ${component.payloadName}(",
                )
                component.properties.forEach { property ->
                    appendLine(
                        "                        ${property.name} = ${property.mapperExpression()},",
                    )
                }
                appendLine("                    ),")
            }
            appendLine("                children = snapshotChildren(),")
            appendLine("            )")
            appendLine("        }")
        }
        appendLine()
        appendLine(
            "        else -> error(\"Unsupported Apple widget: \${type?.debugName}\")",
        )
        appendLine("    }")
    }

private fun StringBuilder.appendPayload(component: ComponentMetadata) {
    if (component.properties.isEmpty()) {
        appendLine(
            "public class ${component.payloadName} internal constructor() : " +
                "FlareUiNodePayload()",
        )
        return
    }
    appendLine("public class ${component.payloadName} internal constructor(")
    component.properties.forEach { property ->
        if (property.eventParameters == null) {
            appendLine("    public val ${property.name}: ${property.type},")
        } else {
            appendLine("    private val ${property.name}: ${property.type},")
        }
    }
    val events =
        component.properties.filter { property ->
            property.eventParameters != null
        }
    if (events.isEmpty()) {
        appendLine(") : FlareUiNodePayload()")
        return
    }
    appendLine(") : FlareUiNodePayload() {")
    events.forEach { property ->
        val parameters = requireNotNull(property.eventParameters)
        if (parameters.isEmpty()) {
            appendLine(
                "    public fun ${property.eventMethodName()}(): Unit = ${property.name}()",
            )
        } else {
            appendLine(
                "    public fun ${property.eventMethodName()}(",
            )
            parameters.forEach { parameter ->
                appendLine("        ${parameter.name}: ${parameter.type},")
            }
            appendLine("    ): Unit = ${property.name}(")
            parameters.forEach { parameter ->
                appendLine("        ${parameter.name},")
            }
            appendLine("    )")
        }
    }
    appendLine("}")
}

private fun PropertyMetadata.mapperExpression(): String {
    val parameters = eventParameters ?: return "props.$name"
    val arguments = parameters.joinToString { parameter -> parameter.name }
    return if (parameters.isEmpty()) {
        "{ dispatchAction { props.$name() } }"
    } else {
        "{ $arguments -> dispatchAction { props.$name($arguments) } }"
    }
}

private fun PropertyMetadata.eventMethodName(): String {
    val stem =
        name
            .removePrefix("on")
            .ifEmpty { name }
    return "perform${stem.replaceFirstChar(Char::uppercaseChar)}"
}

private fun renderSwiftUiRouter(components: List<ComponentMetadata>): String =
    buildString {
        appendGeneratedHeader()
        appendLine("@preconcurrency import FlareUIDemoKit")
        appendLine("import SwiftUI")
        appendLine()
        appendLine("struct FlareSwiftUINode: View {")
        appendLine("    let node: FlareUiNodeSnapshot")
        appendLine("    let resources: FlareAppleResources")
        appendLine()
        appendLine("    @ViewBuilder")
        appendLine("    var body: some View {")
        appendLine("        switch node.kind {")
        components.forEach { component ->
            appendLine("        case .${component.id}:")
            appendLine(
                "            FlareSwiftUI${component.className}Renderer(",
            )
            appendLine(
                "                payload: node.payload as! ${component.payloadName},",
            )
            appendLine("                children: node.children,")
            appendLine("                resources: resources")
            appendLine("            )")
        }
        appendLine("        default:")
        appendLine("            EmptyView()")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }

private fun renderAppleViewFactory(
    components: List<ComponentMetadata>,
    toolkit: String,
    viewType: String,
): String =
    buildString {
        appendGeneratedHeader()
        appendLine("@preconcurrency import FlareUIDemoKit")
        appendLine("import $toolkit")
        appendLine()
        appendLine("func makeFlare${toolkit}NodeView(")
        appendLine("    for node: FlareUiNodeSnapshot,")
        appendLine("    resources: FlareAppleResources")
        appendLine(") -> $viewType {")
        appendLine("    switch node.kind {")
        components.forEach { component ->
            appendLine("    case .${component.id}:")
            appendLine(
                "        return makeFlare${toolkit}${component.className}View(",
            )
            appendLine(
                "            payload: node.payload as! ${component.payloadName},",
            )
            appendLine("            children: node.children,")
            appendLine("            resources: resources")
            appendLine("        )")
        }
        appendLine("    default:")
        appendLine("        return $viewType()")
        appendLine("    }")
        appendLine("}")
    }

private fun renderComposeScaffold(component: ComponentMetadata): String =
    buildString {
        appendLine("package dev.dimension.flare.flareui.compose")
        appendLine()
        appendLine("import androidx.compose.runtime.Composable")
        appendLine("import androidx.compose.ui.UiComposable")
        appendLine()
        appendLine("// $INCOMPLETE_RENDERER_MARKER")
        appendLine("@Composable")
        appendLine("@UiComposable")
        appendLine("internal fun render${component.className}Compose(")
        appendLine("    props: ${component.propsType},")
        appendLine("    children: List<ComposeTreeNode>,")
        appendLine(
            "    resources: dev.dimension.flare.flareui.AndroidFlareResourceResolver,",
        )
        appendLine(") {")
        appendLine(
            "    error(\"Implement the ${component.id} Compose renderer\")",
        )
        appendLine("}")
    }

private fun renderAndroidViewScaffold(component: ComponentMetadata): String =
    buildString {
        appendLine("package dev.dimension.flare.flareui.view")
        appendLine()
        appendLine("import android.content.Context")
        appendLine()
        appendLine("// $INCOMPLETE_RENDERER_MARKER")
        appendLine(
            "internal fun create${component.className}View(context: Context): AndroidViewNode =",
        )
        appendLine(
            "    error(\"Implement the ${component.id} Android View renderer\")",
        )
        appendLine()
        appendLine("internal fun update${component.className}View(")
        appendLine("    node: AndroidViewNode,")
        appendLine("    props: ${component.propsType},")
        appendLine(
            "    resources: dev.dimension.flare.flareui.AndroidFlareResourceResolver,",
        )
        appendLine(") {")
        appendLine(
            "    error(\"Implement the ${component.id} Android View update\")",
        )
        appendLine("}")
    }

private fun renderSwiftUiScaffold(component: ComponentMetadata): String =
    buildString {
        appendLine("@preconcurrency import FlareUIDemoKit")
        appendLine("import SwiftUI")
        appendLine()
        appendLine("// $INCOMPLETE_RENDERER_MARKER")
        appendLine(
            "struct FlareSwiftUI${component.className}Renderer: View {",
        )
        appendLine("    let payload: ${component.payloadName}")
        appendLine("    let children: [FlareUiNodeSnapshot]")
        appendLine("    let resources: FlareAppleResources")
        appendLine()
        appendLine("    var body: some View {")
        appendLine("        EmptyView()")
        appendLine("    }")
        appendLine("}")
    }

private fun renderAppleViewScaffold(
    component: ComponentMetadata,
    toolkit: String,
    viewType: String,
): String =
    buildString {
        appendLine("@preconcurrency import FlareUIDemoKit")
        appendLine("import $toolkit")
        appendLine()
        appendLine("// $INCOMPLETE_RENDERER_MARKER")
        appendLine("func makeFlare${toolkit}${component.className}View(")
        appendLine("    payload: ${component.payloadName},")
        appendLine("    children: [FlareUiNodeSnapshot],")
        appendLine("    resources: FlareAppleResources")
        appendLine(") -> $viewType {")
        appendLine("    $viewType()")
        appendLine("}")
    }

private fun StringBuilder.appendGeneratedHeader() {
    appendLine("// Generated by :flareUI:codegen:generateFlareUiCode. Do not edit.")
    appendLine()
}
