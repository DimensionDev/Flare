package dev.dimension.flare.flareui.codegen

import java.io.File

internal const val INCOMPLETE_RENDERER_MARKER = "FLARE_UI_RENDERER_TODO"

internal class GeneratorWorkspace(
    private val repositoryRoot: File,
) {
    fun generate(components: List<ComponentMetadata>) {
        writeGenerated(
            relativePath =
                "android-compose/build/generated/flareui/kotlin/" +
                    "dev/dimension/flare/flareui/compose/GeneratedComposeWidgets.kt",
            content = renderComposeGlue(components),
        )
        writeGenerated(
            relativePath =
                "android-view/build/generated/flareui/kotlin/" +
                    "dev/dimension/flare/flareui/view/GeneratedViewWidgets.kt",
            content = renderAndroidViewGlue(components),
        )
        writeGenerated(
            relativePath =
                "apple-runtime/build/generated/flareui/kotlin/" +
                    "dev/dimension/flare/flareui/apple/GeneratedAppleWidgets.kt",
            content = renderAppleRuntimeGlue(components),
        )
        writeGenerated(
            relativePath =
                "apple/Sources/Runtime/Generated/" +
                    "FlareUINodes.generated.swift",
            content = renderSwiftRuntimeModels(components),
        )
        writeGenerated(
            relativePath =
                "apple/Sources/KotlinBridge/Generated/" +
                    "FlareUIKotlinNodeBridge.generated.swift",
            content = renderKotlinBridge(components),
        )
        writeGenerated(
            relativePath =
                "apple/Sources/SwiftUI/Generated/" +
                    "FlareSwiftUINode.generated.swift",
            content = renderSwiftUiRouter(components),
        )
        writeGenerated(
            relativePath =
                "apple/Sources/UIKit/Generated/" +
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
                "apple/Sources/AppKit/Generated/" +
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
                    "android-compose/src/androidMain/kotlin/" +
                        "dev/dimension/flare/flareui/compose/renderers/" +
                        "${component.className}ComposeRenderer.kt",
                content = renderComposeScaffold(component),
            ),
            RendererTarget(
                path =
                    "android-view/src/androidMain/kotlin/" +
                        "dev/dimension/flare/flareui/view/renderers/" +
                        "${component.className}ViewRenderer.kt",
                content = renderAndroidViewScaffold(component),
            ),
            RendererTarget(
                path =
                    "apple/Sources/SwiftUI/Renderers/" +
                        "FlareSwiftUI${component.className}Renderer.swift",
                content = renderSwiftUiScaffold(component),
            ),
            RendererTarget(
                path =
                    "apple/Sources/UIKit/Renderers/" +
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
                    "apple/Sources/AppKit/Renderers/" +
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
        appendLine("import FlareUIRuntime")
        appendLine("import SwiftUI")
        appendLine()
        appendLine("struct FlareSwiftUINode: View {")
        appendLine("    let node: FlareUINode")
        appendLine("    let resources: FlareAppleResources")
        appendLine()
        appendLine("    @ViewBuilder")
        appendLine("    var body: some View {")
        appendLine("        switch node.payload {")
        components.forEach { component ->
            appendLine("        case let .${component.id}(payload):")
            appendLine(
                "            FlareSwiftUI${component.className}Renderer(",
            )
            appendLine("                payload: payload,")
            appendLine("                children: node.children,")
            appendLine("                resources: resources")
            appendLine("            )")
        }
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
        appendLine("#if canImport($toolkit)")
        appendLine("import FlareUIRuntime")
        appendLine("import $toolkit")
        appendLine()
        appendLine("@MainActor")
        appendLine("func makeFlare${toolkit}NodeView(")
        appendLine("    for node: FlareUINode,")
        appendLine("    resources: FlareAppleResources")
        appendLine(") -> $viewType {")
        appendLine("    switch node.payload {")
        components.forEach { component ->
            appendLine("    case let .${component.id}(payload):")
            appendLine(
                "        return makeFlare${toolkit}${component.className}View(",
            )
            appendLine("            payload: payload,")
            appendLine("            children: node.children,")
            appendLine("            resources: resources")
            appendLine("        )")
        }
        appendLine("    }")
        appendLine("}")
        appendLine("#endif")
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
        appendLine("import FlareUIRuntime")
        appendLine("import SwiftUI")
        appendLine()
        appendLine("// $INCOMPLETE_RENDERER_MARKER")
        appendLine(
            "struct FlareSwiftUI${component.className}Renderer: View {",
        )
        appendLine("    let payload: ${component.swiftPayloadName}")
        appendLine("    let children: [FlareUINode]")
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
        appendLine("#if canImport($toolkit)")
        appendLine("import FlareUIRuntime")
        appendLine("import $toolkit")
        appendLine()
        appendLine("// $INCOMPLETE_RENDERER_MARKER")
        appendLine("@MainActor")
        appendLine("func makeFlare${toolkit}${component.className}View(")
        appendLine("    payload: ${component.swiftPayloadName},")
        appendLine("    children: [FlareUINode],")
        appendLine("    resources: FlareAppleResources")
        appendLine(") -> $viewType {")
        appendLine("    $viewType()")
        appendLine("}")
        appendLine("#endif")
    }

private fun renderSwiftRuntimeModels(components: List<ComponentMetadata>): String =
    buildString {
        appendGeneratedHeader()
        appendLine("import Foundation")
        appendLine()
        appendLine("public struct FlareUINode: Identifiable {")
        appendLine("    public let id: Int64")
        appendLine("    public let payload: FlareUINodePayload")
        appendLine("    public let children: [FlareUINode]")
        appendLine()
        appendLine("    public init(")
        appendLine("        id: Int64,")
        appendLine("        payload: FlareUINodePayload,")
        appendLine("        children: [FlareUINode]")
        appendLine("    ) {")
        appendLine("        self.id = id")
        appendLine("        self.payload = payload")
        appendLine("        self.children = children")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("public enum FlareUINodePayload {")
        components.forEach { component ->
            appendLine(
                "    case ${component.id}(${component.swiftPayloadName})",
            )
        }
        appendLine("}")

        components.forEach { component ->
            appendLine()
            appendSwiftPayload(component)
        }
    }

private fun StringBuilder.appendSwiftPayload(component: ComponentMetadata) {
    appendLine("public struct ${component.swiftPayloadName} {")
    if (component.properties.isEmpty()) {
        appendLine("    public init() {}")
        appendLine("}")
        return
    }
    component.properties.forEach { property ->
        if (property.eventParameters == null) {
            appendLine(
                "    public let ${property.name}: ${property.type.swiftType()}",
            )
        } else {
            appendLine(
                "    private let ${property.name}: ${property.swiftClosureType()}",
            )
        }
    }
    appendLine()
    appendLine("    public init(")
    component.properties.forEach { property ->
        val escaping =
            if (property.eventParameters == null) "" else "@escaping "
        val type =
            property.eventParameters?.let {
                property.swiftClosureType()
            } ?: property.type.swiftType()
        appendLine("        ${property.name}: $escaping$type,")
    }
    appendLine("    ) {")
    component.properties.forEach { property ->
        appendLine("        self.${property.name} = ${property.name}")
    }
    appendLine("    }")

    component.properties
        .filter { property -> property.eventParameters != null }
        .forEach { property ->
            val parameters = requireNotNull(property.eventParameters)
            appendLine()
            appendLine("    @MainActor")
            if (parameters.isEmpty()) {
                appendLine(
                    "    public func ${property.eventMethodName()}() {",
                )
                appendLine("        ${property.name}()")
            } else {
                appendLine(
                    "    public func ${property.eventMethodName()}(",
                )
                parameters.forEach { parameter ->
                    appendLine(
                        "        ${parameter.name}: ${parameter.type.swiftType()},",
                    )
                }
                appendLine("    ) {")
                val arguments =
                    parameters.joinToString { parameter -> parameter.name }
                appendLine("        ${property.name}($arguments)")
            }
            appendLine("    }")
        }
    appendLine("}")
}

private fun renderKotlinBridge(components: List<ComponentMetadata>): String =
    buildString {
        appendGeneratedHeader()
        appendLine("@preconcurrency import FlareUIKotlinRuntime")
        appendLine("import FlareUIRuntime")
        appendLine()
        appendLine("@MainActor")
        appendLine("func mapFlareUIKotlinNode(")
        appendLine("    _ node: FlareUiNodeSnapshot")
        appendLine(") -> FlareUINode {")
        appendLine("    let children = node.children.map(mapFlareUIKotlinNode)")
        components.forEach { component ->
            appendLine("    if case .${component.id} = node.kind {")
            if (component.properties.isNotEmpty()) {
                appendLine(
                    "        let payload = node.payload as! ${component.payloadName}",
                )
            }
            appendLine("        return FlareUINode(")
            appendLine("            id: node.id,")
            appendLine("            payload: .${component.id}(")
            if (component.properties.isEmpty()) {
                appendLine("                ${component.swiftPayloadName}()")
            } else {
                appendLine("                ${component.swiftPayloadName}(")
                component.properties.forEach { property ->
                    appendLine(
                        "                    ${property.name}: " +
                            "${property.swiftBridgeExpression()},",
                    )
                }
                appendLine("                )")
            }
            appendLine("            ),")
            appendLine("            children: children")
            appendLine("        )")
            appendLine("    }")
        }
        appendLine(
            "    preconditionFailure(\"Unsupported Kotlin Flare UI node kind\")",
        )
        appendLine("}")
    }

private fun PropertyMetadata.swiftBridgeExpression(): String {
    val parameters = eventParameters
    if (parameters != null) {
        if (parameters.isEmpty()) {
            return "{ payload.${eventMethodName()}() }"
        }
        val names = parameters.joinToString { parameter -> parameter.name }
        val arguments =
            parameters.joinToString { parameter ->
                "${parameter.name}: ${parameter.name}"
            }
        return "{ $names in payload.${eventMethodName()}($arguments) }"
    }
    return when (type) {
        "dev.dimension.flare.flareui.FlareText" -> {
            "mapFlareUIKotlinText(payload.$name)"
        }

        "dev.dimension.flare.flareui.FlareText?" -> {
            "payload.$name.map(mapFlareUIKotlinText)"
        }

        "dev.dimension.flare.flareui.FlareImageResource" -> {
            "mapFlareUIKotlinImage(payload.$name)"
        }

        else -> {
            "payload.$name"
        }
    }
}

private fun PropertyMetadata.swiftClosureType(): String {
    val parameters = requireNotNull(eventParameters)
    val input =
        if (parameters.isEmpty()) {
            "()"
        } else {
            parameters.joinToString(
                prefix = "(",
                postfix = ")",
            ) { parameter -> parameter.type.swiftType() }
        }
    return "@MainActor $input -> Void"
}

private fun String.swiftType(): String =
    when (this) {
        "kotlin.Boolean" -> {
            "Bool"
        }

        "kotlin.String" -> {
            "String"
        }

        "kotlin.Byte" -> {
            "Int8"
        }

        "kotlin.Short" -> {
            "Int16"
        }

        "kotlin.Int" -> {
            "Int32"
        }

        "kotlin.Long" -> {
            "Int64"
        }

        "kotlin.Float" -> {
            "Float"
        }

        "kotlin.Double" -> {
            "Double"
        }

        "dev.dimension.flare.flareui.FlareText" -> {
            "FlareUIText"
        }

        "dev.dimension.flare.flareui.FlareText?" -> {
            "FlareUIText?"
        }

        "dev.dimension.flare.flareui.FlareImageResource" -> {
            "FlareUIImageResource"
        }

        else -> {
            error("Unsupported Swift bridge type: $this")
        }
    }

private fun StringBuilder.appendGeneratedHeader() {
    appendLine("// Generated by :codegen:generateFlareUiCode. Do not edit.")
    appendLine()
}
