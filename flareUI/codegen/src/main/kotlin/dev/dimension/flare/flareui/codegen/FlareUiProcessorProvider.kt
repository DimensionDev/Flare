package dev.dimension.flare.flareui.codegen

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.validate
import java.io.File

public class FlareUiProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        FlareUiProcessor(
            logger = environment.logger,
            options = environment.options,
        )
}

private class FlareUiProcessor(
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val symbols = resolver.getSymbolsWithAnnotation(FLARE_COMPONENT_ANNOTATION).toList()
        val invalid = symbols.filterNot(KSAnnotated::validate)
        if (invalid.isNotEmpty()) return invalid

        val components =
            symbols
                .filterIsInstance<KSClassDeclaration>()
                .mapNotNull(::parseComponent)
                .sortedBy(ComponentMetadata::className)

        if (components.isEmpty()) return emptyList()

        val repositoryRoot = options[REPOSITORY_ROOT_OPTION]
        if (repositoryRoot.isNullOrBlank()) {
            logger.error("KSP option $REPOSITORY_ROOT_OPTION is required.")
            return emptyList()
        }

        GeneratorWorkspace(File(repositoryRoot)).generate(components)
        generated = true
        return emptyList()
    }

    private fun parseComponent(declaration: KSClassDeclaration): ComponentMetadata? {
        if (declaration.classKind != ClassKind.OBJECT) {
            logger.error("@FlareComponent can only annotate an object.", declaration)
            return null
        }

        val typeName = declaration.simpleName.asString()
        if (typeName == "Type" || !typeName.endsWith("Type")) {
            logger.error(
                "@FlareComponent object names must end in Type.",
                declaration,
            )
            return null
        }

        val widgetSuperType =
            declaration.superTypes
                .map { reference -> reference.resolve() }
                .firstOrNull { type ->
                    type.declaration.qualifiedName?.asString() == WIDGET_TYPE
                }
        val propsType =
            widgetSuperType
                ?.arguments
                ?.singleOrNull()
                ?.type
                ?.resolve()
        if (propsType == null) {
            logger.error(
                "@FlareComponent objects must directly extend WidgetType<Props>.",
                declaration,
            )
            return null
        }

        val propsTypeName = propsType.renderType()
        val properties =
            if (propsTypeName == "kotlin.Unit") {
                emptyList()
            } else {
                parseProperties(propsType, declaration) ?: return null
            }

        return ComponentMetadata(
            type =
                declaration.qualifiedName?.asString()
                    ?: error("Flare component must have a qualified name"),
            propsType = propsTypeName,
            properties = properties,
        )
    }

    private fun parseProperties(
        propsType: KSType,
        component: KSClassDeclaration,
    ): List<PropertyMetadata>? {
        val propsDeclaration = propsType.declaration as? KSClassDeclaration
        val constructor = propsDeclaration?.primaryConstructor
        if (propsDeclaration == null || constructor == null) {
            logger.error(
                "Flare component props must be a class with a primary constructor.",
                component,
            )
            return null
        }

        val propertyNames =
            propsDeclaration
                .getAllProperties()
                .map { property -> property.simpleName.asString() }
                .toSet()
        return constructor.parameters.map { parameter ->
            val name = parameter.name?.asString()
            if (name == null || name !in propertyNames) {
                logger.error(
                    "Every Flare props constructor parameter must be a property.",
                    parameter,
                )
                return null
            }
            parseProperty(name, parameter) ?: return null
        }
    }

    private fun parseProperty(
        name: String,
        parameter: KSValueParameter,
    ): PropertyMetadata? {
        val type = parameter.type.resolve()
        val declarationName =
            type.declaration.qualifiedName
                ?.asString()
                .orEmpty()
        if (declarationName.startsWith("kotlin.Function")) {
            if (type.isMarkedNullable) {
                logger.error("Nullable Flare UI events are not supported.", parameter)
                return null
            }
            val arguments = type.arguments.mapNotNull { argument -> argument.type?.resolve() }
            val returnType = arguments.lastOrNull()
            if (returnType?.renderType() != "kotlin.Unit") {
                logger.error("Flare UI events must return Unit.", parameter)
                return null
            }
            return PropertyMetadata(
                name = name,
                type = type.renderType(),
                eventParameters =
                    arguments
                        .dropLast(1)
                        .mapIndexed { index, eventType ->
                            EventParameterMetadata(
                                name = "value$index",
                                type = eventType.renderType(),
                            )
                        },
            )
        }
        return PropertyMetadata(
            name = name,
            type = type.renderType(),
        )
    }

    private fun KSType.renderType(): String {
        val declarationName =
            declaration.qualifiedName?.asString()
                ?: error("Flare UI types must have qualified names")
        val arguments =
            arguments.mapNotNull { argument ->
                argument.type?.resolve()?.renderType()
            }
        return buildString {
            append(declarationName)
            if (arguments.isNotEmpty()) {
                append(arguments.joinToString(prefix = "<", postfix = ">"))
            }
            if (isMarkedNullable) append('?')
        }
    }
}

private const val FLARE_COMPONENT_ANNOTATION =
    "dev.dimension.flare.flareui.FlareComponent"
private const val WIDGET_TYPE = "dev.dimension.flare.flareui.WidgetType"
private const val REPOSITORY_ROOT_OPTION = "flareUiRepositoryRoot"
