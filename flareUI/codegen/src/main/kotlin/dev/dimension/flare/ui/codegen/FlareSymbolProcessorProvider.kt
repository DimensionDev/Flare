package dev.dimension.flare.ui.codegen

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.validate

public class FlareSymbolProcessorProvider : SymbolProcessorProvider {
    public override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        FlareSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options,
        )
}

private data class ParsedRenderer(
    val declaration: KSClassDeclaration,
    val packageName: String,
    val backendType: String,
    val backendName: String,
    val groupName: String,
    val constructorParameters: List<ConstructorParameter>,
    val binding: RendererBinding,
)

private class FlareSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private var generated: Boolean = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val renderers =
            resolver
                .getSymbolsWithAnnotation(RENDERER_ANNOTATION)
                .toList()
        val primitives =
            resolver
                .getSymbolsWithAnnotation(PRIMITIVE_ANNOTATION)
                .toList()
        val symbols = renderers + primitives
        val invalid = symbols.filterNot(KSAnnotated::validate)
        if (invalid.isNotEmpty()) return invalid

        when {
            renderers.isNotEmpty() -> generateRendererPlugins(resolver, renderers)
            primitives.isNotEmpty() -> generatePrimitives(primitives)
            else -> return emptyList()
        }
        generated = true
        return emptyList()
    }

    private fun generatePrimitives(symbols: List<KSAnnotated>) {
        val primitives =
            symbols
                .filterIsInstance<KSClassDeclaration>()
                .mapNotNull { declaration ->
                    parsePrimitive(declaration)?.let { primitive ->
                        declaration to primitive
                    }
                }
        primitives.forEach { (declaration, primitive) ->
            writeSource(
                packageName = primitive.packageName,
                fileName = "${primitive.apiName}Generated",
                sourceFiles = listOfNotNull(declaration.containingFile),
                aggregating = false,
                content = FlareGenerator.renderPrimitive(primitive),
            )
        }

        val swiftModuleName = options[SWIFT_UI_MODULE_OPTION] ?: return
        if (!swiftModuleName.isKotlinIdentifier()) {
            logger.error(
                "'$SWIFT_UI_MODULE_OPTION' must be a valid Swift identifier.",
            )
            return
        }
        if (primitives.size != symbols.size) return

        writeSource(
            packageName = "",
            fileName = "${swiftModuleName}SwiftUIGenerated",
            extensionName = "swift",
            sourceFiles =
                primitives.mapNotNull { (declaration, _) ->
                    declaration.containingFile
                },
            aggregating = true,
            content =
                FlareSwiftUIGenerator.renderPlugin(
                    SwiftUIPluginDefinition(
                        moduleName = swiftModuleName,
                        kotlinModuleName =
                            options[SWIFT_UI_KOTLIN_MODULE_OPTION]
                                ?: DEFAULT_SWIFT_UI_KOTLIN_MODULE,
                        primitives =
                            primitives
                                .map { (_, primitive) -> primitive }
                                .sortedBy(PrimitiveDefinition::apiName),
                    ),
                ),
        )
    }

    private fun parsePrimitive(declaration: KSClassDeclaration): PrimitiveDefinition? {
        if (declaration.classKind != ClassKind.INTERFACE) {
            return logger.fail("@FlarePrimitive must annotate an interface.", declaration)
        }

        val packageName = declaration.packageName.asString()
        val apiName =
            declaration.simpleName
                .asString()
                .removeSuffix("Spec")
        if (!apiName.isKotlinIdentifier()) {
            return logger.fail("Invalid generated primitive name '$apiName'.", declaration)
        }

        val invokeFunctions =
            declaration
                .getDeclaredFunctions()
                .filter { function -> function.simpleName.asString() == "invoke" }
                .toList()
        if (invokeFunctions.size != 1) {
            return logger.fail(
                "@FlarePrimitive interfaces must declare exactly one invoke function.",
                declaration,
            )
        }
        val invoke = invokeFunctions.single()
        if (
            !invoke.hasAnnotation(COMPOSABLE_ANNOTATION) ||
            !invoke.hasAnnotation(UI_COMPOSABLE_ANNOTATION)
        ) {
            return logger.fail(
                "Primitive invoke functions must use @Composable and @FlareUiComposable.",
                invoke,
            )
        }
        if (invoke.returnType?.resolve()?.qualifiedNameString() != "kotlin.Unit") {
            return logger.fail("Primitive invoke functions must return Unit.", invoke)
        }

        val parameters = invoke.parameters.mapNotNull(::parseParameter)
        if (parameters.size != invoke.parameters.size) return null
        if (parameters.count { parameter -> parameter.kind == ParameterKind.Modifier } > 1) {
            return logger.fail("A primitive may expose at most one FlareModifier.", invoke)
        }
        return PrimitiveDefinition(
            packageName = packageName,
            specType = declaration.qualifiedNameString(),
            apiName = apiName,
            debugName = "$packageName.$apiName",
            parameters = parameters,
        )
    }

    private fun parseParameter(parameter: KSValueParameter): PrimitiveParameter? {
        val name =
            parameter.name?.asString()
                ?: return logger.fail("Primitive parameters must be named.", parameter)
        if (!name.isKotlinIdentifier()) {
            return logger.fail("Invalid primitive parameter name '$name'.", parameter)
        }

        val type = parameter.type.resolve()
        val isContent = type.qualifiedNameString() == FLARE_CONTENT_TYPE

        return when {
            isContent -> {
                PrimitiveParameter(
                    name = name,
                    type = type.renderType(),
                    kind = ParameterKind.Slot,
                )
            }

            type.qualifiedNameString() == FLARE_MODIFIER_TYPE -> {
                PrimitiveParameter(
                    name = name,
                    type = type.renderType(),
                    kind = ParameterKind.Modifier,
                )
            }

            else -> {
                PrimitiveParameter(
                    name = name,
                    type = type.renderType(),
                    kind = ParameterKind.Property,
                )
            }
        }
    }

    private fun generateRendererPlugins(
        resolver: Resolver,
        symbols: List<KSAnnotated>,
    ) {
        val renderers =
            symbols
                .filterIsInstance<KSClassDeclaration>()
                .mapNotNull(::parseRenderer)
        if (renderers.size != symbols.size) return

        renderers
            .groupBy { renderer -> renderer.backendType to renderer.groupName }
            .values
            .forEach { group ->
                generateRendererPlugin(resolver, group)
            }
    }

    private fun parseRenderer(declaration: KSClassDeclaration): ParsedRenderer? {
        if (declaration.classKind != ClassKind.CLASS && declaration.classKind != ClassKind.OBJECT) {
            return logger.fail("@FlareRenderer must annotate a class or object.", declaration)
        }

        val superTypes = declaration.getAllSuperTypes().toList()
        val widgetContracts =
            superTypes
                .mapNotNull { type ->
                    val contract = type.declaration as? KSClassDeclaration ?: return@mapNotNull null
                    if (!contract.hasAnnotation(WIDGET_CONTRACT_ANNOTATION)) {
                        return@mapNotNull null
                    }
                    contract
                }.distinctBy(KSDeclaration::qualifiedNameString)
        if (widgetContracts.size != 1) {
            return logger.fail(
                "@FlareRenderer classes must implement exactly one generated widget contract.",
                declaration,
            )
        }
        val widgetContract = widgetContracts.single()
        val groupName =
            widgetContract.packageName
                .asString()
                .substringAfterLast('.')
                .toKotlinName()

        val backendMarkers =
            superTypes.filter { type ->
                type.declaration.qualifiedNameString() == BACKEND_WIDGET_TYPE
            }
        if (backendMarkers.size != 1) {
            return logger.fail(
                "@FlareRenderer classes must inherit exactly one FlareBackendWidget backend.",
                declaration,
            )
        }
        val backendType =
            backendMarkers
                .single()
                .arguments
                .singleOrNull()
                ?.type
                ?.resolve()
                ?: return logger.fail(
                    "FlareBackendWidget must declare a concrete backend type.",
                    declaration,
                )
        val backendDeclaration =
            backendType.declaration as? KSClassDeclaration
                ?: return logger.fail(
                    "Flare backend types must be class declarations.",
                    declaration,
                )
        if (
            backendDeclaration.classKind != ClassKind.OBJECT &&
            backendDeclaration.classKind != ClassKind.CLASS
        ) {
            return logger.fail(
                "Flare backends must be classes or singleton objects.",
                backendDeclaration,
            )
        }

        val rendererConstructorParameters =
            if (declaration.classKind == ClassKind.OBJECT) {
                emptyList()
            } else {
                declaration.primaryConstructor
                    ?.parameters
                    .orEmpty()
                    .filterNot(KSValueParameter::hasDefault)
                    .mapNotNull(::parseConstructorParameter)
            }
        if (
            declaration.classKind != ClassKind.OBJECT &&
            rendererConstructorParameters.size !=
            declaration.primaryConstructor
                ?.parameters
                .orEmpty()
                .count { parameter -> !parameter.hasDefault }
        ) {
            return null
        }

        return ParsedRenderer(
            declaration = declaration,
            packageName = declaration.packageName.asString(),
            backendType = backendDeclaration.qualifiedNameString(),
            backendName = backendDeclaration.simpleName.asString().removeSuffix("Backend"),
            groupName = groupName,
            constructorParameters =
                rendererConstructorParameters.filterNot { parameter ->
                    parameter.type == backendDeclaration.qualifiedNameString()
                },
            binding =
                RendererBinding(
                    componentType = "${widgetContract.qualifiedNameString()}.Type",
                    rendererType = declaration.qualifiedNameString(),
                    constructorArguments =
                        rendererConstructorParameters.map { parameter ->
                            ConstructorArgument(
                                name = parameter.name,
                                value =
                                    if (parameter.type == backendDeclaration.qualifiedNameString()) {
                                        "backend"
                                    } else {
                                        parameter.name
                                    },
                            )
                        },
                    isObject = declaration.classKind == ClassKind.OBJECT,
                ),
        )
    }

    @OptIn(KspExperimental::class)
    private fun generateRendererPlugin(
        resolver: Resolver,
        renderers: List<ParsedRenderer>,
    ) {
        val first = renderers.first()
        val packages = renderers.map(ParsedRenderer::packageName).distinct()
        if (packages.size != 1) {
            logger.error(
                "Renderers for ${first.backendName}${first.groupName} must use one package.",
                first.declaration,
            )
            return
        }

        val duplicates =
            renderers
                .groupBy { renderer -> renderer.binding.componentType }
                .filterValues { values -> values.size > 1 }
                .keys
        if (duplicates.isNotEmpty()) {
            logger.error(
                "Duplicate renderer bindings for ${duplicates.joinToString()}.",
                first.declaration,
            )
            return
        }

        val constructorParameters = linkedMapOf<String, ConstructorParameter>()
        renderers
            .flatMap(ParsedRenderer::constructorParameters)
            .forEach { parameter ->
                val existing = constructorParameters[parameter.name]
                if (existing != null && existing.type != parameter.type) {
                    logger.error(
                        "Renderer constructor parameter '${parameter.name}' uses both " +
                            "'${existing.type}' and '${parameter.type}'.",
                        first.declaration,
                    )
                    return
                }
                constructorParameters[parameter.name] = parameter
            }

        val packageName = packages.single()
        val generatedName =
            "${first.backendName}${first.groupName}RendererPlugin"
        val expectProperty =
            resolver
                .getDeclarationsFromPackage(packageName)
                .filterIsInstance<KSPropertyDeclaration>()
                .firstOrNull { property ->
                    property.simpleName.asString() == generatedName &&
                        Modifier.EXPECT in property.modifiers
                }
        if (expectProperty != null && constructorParameters.isNotEmpty()) {
            logger.error(
                "Expected renderer plugin properties cannot require constructor parameters.",
                expectProperty,
            )
            return
        }
        val sortedConstructorParameters =
            constructorParameters.values.sortedBy(ConstructorParameter::name)

        writeSource(
            packageName = packageName,
            fileName = "${generatedName}Generated",
            sourceFiles =
                (
                    renderers.mapNotNull { renderer -> renderer.declaration.containingFile } +
                        listOfNotNull(expectProperty?.containingFile)
                ).distinct(),
            aggregating = true,
            content =
                FlareGenerator.renderRendererPlugin(
                    RendererPluginDefinition(
                        packageName = packageName,
                        generatedName = generatedName,
                        backendType = first.backendType,
                        actualProperty = expectProperty != null,
                        constructorParameters = sortedConstructorParameters,
                        bindings =
                            renderers
                                .map(ParsedRenderer::binding)
                                .sortedBy(RendererBinding::componentType),
                    ),
                ),
        )
    }

    private fun parseConstructorParameter(parameter: KSValueParameter): ConstructorParameter? {
        val name =
            parameter.name?.asString()
                ?: return logger.fail("Constructor parameters must be named.", parameter)
        return ConstructorParameter(
            name = name,
            type = parameter.type.resolve().renderType(),
        )
    }

    private fun writeSource(
        packageName: String,
        fileName: String,
        extensionName: String = "kt",
        sourceFiles: List<KSFile>,
        aggregating: Boolean,
        content: String,
    ) {
        codeGenerator
            .createNewFile(
                dependencies =
                    Dependencies(
                        aggregating = aggregating,
                        *sourceFiles.toTypedArray(),
                    ),
                packageName = packageName,
                fileName = fileName,
                extensionName = extensionName,
            ).bufferedWriter()
            .use { writer -> writer.write(content) }
    }
}

private fun KSAnnotated.findAnnotation(qualifiedName: String): KSAnnotation? =
    annotations.firstOrNull { annotation ->
        annotation.annotationType.resolve().qualifiedNameString() == qualifiedName
    }

private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean = findAnnotation(qualifiedName) != null

private fun KSType.qualifiedNameString(): String = declaration.qualifiedNameString()

private fun KSDeclaration.qualifiedNameString(): String =
    qualifiedName?.asString()
        ?: error("Flare declarations must have qualified names: $this")

private fun KSType.renderType(): String {
    val declarationName =
        when (val typeDeclaration = declaration) {
            is KSTypeParameter -> typeDeclaration.name.asString()
            else -> typeDeclaration.qualifiedNameString()
        }
    val renderedArguments =
        arguments.joinToString(separator = ", ") { argument ->
            argument.renderTypeArgument()
        }
    return buildString {
        append(declarationName)
        if (arguments.isNotEmpty()) {
            append('<')
            append(renderedArguments)
            append('>')
        }
        if (isMarkedNullable) append('?')
    }
}

private fun KSTypeArgument.renderTypeArgument(): String {
    if (variance == Variance.STAR) return "*"
    val rendered = type?.resolve()?.renderType() ?: "*"
    return when (variance) {
        Variance.COVARIANT -> "out $rendered"
        Variance.CONTRAVARIANT -> "in $rendered"
        Variance.INVARIANT -> rendered
        Variance.STAR -> "*"
    }
}

private fun String.isKotlinIdentifier(): Boolean =
    isNotEmpty() &&
        (first().isLetter() || first() == '_') &&
        all { character -> character.isLetterOrDigit() || character == '_' }

private fun KSPLogger.fail(
    message: String,
    symbol: KSAnnotated,
): Nothing? {
    error(message, symbol)
    return null
}

private const val PRIMITIVE_ANNOTATION = "dev.dimension.flare.ui.FlarePrimitive"
private const val WIDGET_CONTRACT_ANNOTATION = "dev.dimension.flare.ui.FlareWidgetContract"
private const val RENDERER_ANNOTATION = "dev.dimension.flare.ui.FlareRenderer"
private const val COMPOSABLE_ANNOTATION = "androidx.compose.runtime.Composable"
private const val UI_COMPOSABLE_ANNOTATION = "dev.dimension.flare.ui.FlareUiComposable"
private const val FLARE_MODIFIER_TYPE = "dev.dimension.flare.ui.FlareModifier"
private const val FLARE_CONTENT_TYPE = "dev.dimension.flare.ui.FlareContent"
private const val BACKEND_WIDGET_TYPE = "dev.dimension.flare.ui.FlareBackendWidget"
private const val SWIFT_UI_MODULE_OPTION = "flare.swiftui.moduleName"
private const val SWIFT_UI_KOTLIN_MODULE_OPTION = "flare.swiftui.kotlinModuleName"
private const val DEFAULT_SWIFT_UI_KOTLIN_MODULE = "FlareUI"
