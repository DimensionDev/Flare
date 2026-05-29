package dev.dimension.flare.web.presenter.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import java.io.File

public class WebPresenterProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        WebPresenterProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options,
        )
}

private class WebPresenterProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {
    private var generated = false
    private val typeAnalyzer = WebTypeAnalyzer()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val annotatedSymbols = resolver.getSymbolsWithAnnotation(WEB_PRESENTER_ANNOTATION).toList()
        val configuredSymbols =
            options["webPresenterTypes"]
                .orEmpty()
                .split(',')
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .mapNotNull { typeName ->
                    resolver.getClassDeclarationByName(resolver.getKSNameFromString(typeName))
                        ?: run {
                            logger.error("Configured web presenter type was not found: $typeName")
                            null
                        }
                }.toList()
        val invalid = annotatedSymbols.filterNot { it.validate() }
        val symbols =
            (annotatedSymbols.filter { it.validate() } + configuredSymbols)
                .distinctBy {
                    (it as? KSClassDeclaration)?.qualifiedName?.asString() ?: it.toString()
                }
        val presenters =
            symbols
                .filterIsInstance<KSClassDeclaration>()
                .mapNotNull(::parsePresenter)
                .sortedBy { it.name }

        if (presenters.isNotEmpty()) {
            generateKotlin(presenters)
            writeManifest(presenters)
            generated = true
        }

        return invalid
    }

    private fun parsePresenter(declaration: KSClassDeclaration): PresenterModel? {
        if (declaration.classKind != ClassKind.CLASS) {
            logger.error("@WebPresenter can only annotate classes.", declaration)
            return null
        }
        val primaryConstructor = declaration.primaryConstructor
        val constructorParameters =
            primaryConstructor
                ?.parameters
                .orEmpty()
                .map { parameter ->
                    parseArgument(
                        parameter = parameter,
                        errorTarget = primaryConstructor ?: declaration,
                        context = "@WebPresenter constructor parameter",
                    ) ?: return null
                }

        val presenterName =
            declaration.annotations
                .firstOrNull { it.shortName.asString() == "WebPresenter" }
                ?.arguments
                ?.firstOrNull { it.name?.asString() == "name" }
                ?.value as? String
        if (presenterName.isNullOrBlank()) {
            logger.error("@WebPresenter name cannot be blank.", declaration)
            return null
        }

        val stateType = presenterStateType(declaration)
        if (stateType == null) {
            logger.error("@WebPresenter presenter must directly extend PresenterBase<State>.", declaration)
            return null
        }

        val stateDeclaration = stateType.declaration as? KSClassDeclaration
        if (stateDeclaration == null) {
            logger.error("@WebPresenter state type must be a class or interface.", declaration)
            return null
        }

        val properties =
            stateDeclaration
                .getAllProperties()
                .filterNot { it.hasAnnotation(WEB_IGNORE_ANNOTATION) }
                .filter { it.isBridgeVisible() }
                .mapNotNull(::parseProperty)
                .sortedBy { it.name }
                .toList()
        val actions =
            stateDeclaration
                .getAllFunctions()
                .filterNot { it.hasAnnotation(WEB_IGNORE_ANNOTATION) }
                .filterNot { it.hasAnnotation(COMPOSABLE_ANNOTATION) }
                .filter { it.isBridgeVisible() }
                .filterNot { it.simpleName.asString().isIgnoredFunctionName() }
                .mapNotNull(::parseAction)
                .sortedBy { it.name }
                .toList()

        val containingFiles =
            buildSet {
                declaration.containingFile?.let(::add)
                stateDeclaration.containingFile?.let(::add)
            }

        return PresenterModel(
            name = presenterName,
            factoryName = presenterName.toFactoryName(),
            bindFactoryName = presenterName.toBindFactoryName(),
            presenterType = declaration.qualifiedName?.asString().orEmpty(),
            specName = "${declaration.simpleName.asString()}WebSpec",
            stateType = stateDeclaration.qualifiedName?.asString().orEmpty(),
            stateTypeName = stateDeclaration.webStateTypeName(declaration),
            creatable = Modifier.ABSTRACT !in declaration.modifiers,
            parameters = constructorParameters,
            properties = properties,
            actions = actions,
            containingFiles = containingFiles,
        )
    }

    private fun presenterStateType(declaration: KSClassDeclaration): KSType? =
        declaration.superTypes
            .map { it.resolve() }
            .firstOrNull { type ->
                (type.declaration as? KSClassDeclaration)
                    ?.qualifiedName
                    ?.asString() == PRESENTER_BASE
            }?.arguments
            ?.firstOrNull()
            ?.type
            ?.resolve()

    private fun parseProperty(property: KSPropertyDeclaration): PropertyModel? {
        val name = property.simpleName.asString()
        val type = property.type.resolve()
        val value = typeAnalyzer.value(type)
        if (value == null) {
            logger.error("Unsupported @WebPresenter state property type for $name.", property)
            return null
        }
        return PropertyModel(
            name = name,
            webType = value.webType,
            enum = value.enum,
            sealed = value.sealed,
            ref = value.ref,
            array = value.array,
            uiState = value.uiState,
            pagingState = value.pagingState,
            nullable = type.isMarkedNullable,
        )
    }

    private fun parseAction(function: KSFunctionDeclaration): ActionModel? {
        if (function.typeParameters.isNotEmpty()) return null
        if (Modifier.SUSPEND in function.modifiers) return null
        val name = function.simpleName.asString()
        val returnValue =
            function
                .returnType
                ?.resolve()
                ?.let(typeAnalyzer::returnValue)
        if (returnValue == null && !function.returnsUnit()) {
            logger.error("Unsupported @WebPresenter action return type in $name.", function)
            return null
        }
        val args =
            function.parameters.map { parameter ->
                parseArgument(
                    parameter = parameter,
                    errorTarget = function,
                    context = "@WebPresenter action parameter in $name",
                    allowRef = true,
                ) ?: return null
            }
        if (args.any { it.callback != null }) {
            logger.error("@WebPresenter action callback parameters are not supported yet.", function)
            return null
        }
        return ActionModel(
            name = name,
            args = args,
            returnValue = returnValue,
        )
    }

    private fun parseArgument(
        parameter: KSValueParameter,
        errorTarget: KSAnnotated,
        context: String,
        allowRef: Boolean = false,
    ): ArgumentModel? {
        val parameterName = parameter.name?.asString()
        val type = parameter.type.resolve()
        val value = typeAnalyzer.value(type, allowRef = allowRef)
        val callback = typeAnalyzer.callback(type)
        if (parameterName == null || (value == null && callback == null)) {
            logger.error("Unsupported $context.", errorTarget)
            return null
        }
        return ArgumentModel(
            name = parameterName,
            webType = value?.webType,
            enum = value?.enum,
            sealed = value?.sealed,
            ref = value?.ref,
            callback = callback,
            array = value?.array,
            uiState = value?.uiState,
            pagingState = value?.pagingState,
            nullable = type.isMarkedNullable,
        )
    }

    private fun generateKotlin(presenters: List<PresenterModel>) {
        val files = presenters.flatMap { it.containingFiles }.distinct().toTypedArray()
        codeGenerator
            .createNewFile(
                dependencies = Dependencies(aggregating = true, *files),
                packageName = GENERATED_PACKAGE,
                fileName = "GeneratedWebPresenters",
            ).bufferedWriter()
            .use { writer ->
                writer.write(WebPresenterRenderer.renderKotlin(presenters))
            }
    }

    private fun writeManifest(presenters: List<PresenterModel>) {
        val manifestPath =
            options["webPresenterManifestPath"]
                ?: return
        val output = File(manifestPath)
        output.parentFile.mkdirs()
        output.writeText(WebPresenterRenderer.renderManifest(presenters))
    }
}

internal object WebPresenterRenderer {
    fun renderKotlin(presenters: List<PresenterModel>): String =
        buildString {
            val allRefs = presenters.flatMap { it.referencedRefs() }.distinctBy { it.type }
            val allRefMethods = allRefs.flatMap { ref -> ref.methods }
            val allArrays = presenters.flatMap { it.referencedArrays() }.distinctBy { it.type }
            val allStateCodecValues =
                presenters
                    .flatMap { it.referencedStateCodecValues() }
                    .distinctBy { "${it.codec.kind}:${it.type}" }
            val allSealeds = presenters.flatMap { it.referencedSealeds() }.distinctBy { it.type }
            val inputSealedTypes = presenters.flatMap { it.referencedInputSealeds() }.map { it.type }.toSet()
            val allCallbackParameters =
                presenters
                    .flatMap { presenter -> presenter.parameters }
                    .filter { it.callback != null }
            val allReturnValues =
                presenters.flatMap { presenter ->
                    presenter.actions.mapNotNull { action -> action.returnValue } +
                        allRefMethods.mapNotNull { method -> method.returnValue }
                }

            appendLine("@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)")
            appendLine(
                "@file:Suppress(\"DEPRECATION\", \"NATIVE_INVOKE\", \"REDUNDANT_ELSE_IN_WHEN\", \"UNCHECKED_CAST\", \"UNCHECKED_CAST_TO_EXTERNAL_INTERFACE\")",
            )
            appendLine()
            appendLine("package $GENERATED_PACKAGE")
            appendLine()
            appendLine("import dev.dimension.flare.web.shared.WebPresenterBinding")
            appendLine("import dev.dimension.flare.web.shared.WebPresenterSpec")
            appendLine("import dev.dimension.flare.web.shared.WebPresenterSnapshot")
            appendLine("import dev.dimension.flare.web.shared.webPresenterActionPath")
            appendLine("import kotlinx.coroutines.flow.StateFlow")
            appendLine("import kotlinx.serialization.json.JsonElement")
            appendLine("import kotlinx.serialization.json.JsonNull")
            appendLine("import kotlinx.serialization.json.JsonPrimitive")
            appendLine("import kotlinx.serialization.json.add")
            appendLine("import kotlinx.serialization.json.addJsonObject")
            appendLine("import kotlinx.serialization.json.boolean")
            appendLine("import kotlinx.serialization.json.buildJsonArray")
            appendLine("import kotlinx.serialization.json.buildJsonObject")
            appendLine("import kotlinx.serialization.json.double")
            appendLine("import kotlinx.serialization.json.float")
            appendLine("import kotlinx.serialization.json.int")
            appendLine("import kotlinx.serialization.json.put")
            appendLine("import kotlinx.serialization.json.long")
            appendLine("import kotlinx.serialization.json.putJsonObject")
            appendLine("import kotlin.js.JsAny")
            appendLine("import kotlin.js.JsArray")
            appendLine("import kotlin.js.JsReference")
            appendLine("import kotlin.js.get")
            appendLine("import kotlin.js.toJsReference")
            appendLine("import kotlin.js.toJsArray")
            appendLine("import dev.dimension.flare.web.shared.WebPresenterJson")
            appendLine("import kotlinx.serialization.json.jsonObject")
            appendLine("import kotlinx.serialization.json.jsonArray")
            appendLine("import kotlinx.serialization.json.jsonPrimitive")
            if (allCallbackParameters.isNotEmpty()) {
                appendLine("import kotlin.js.nativeInvoke")
            }
            appendLine()
            appendLine("private fun webPresenterRefIndex(value: JsonElement): Int =")
            appendLine("    try {")
            appendLine("        value.jsonPrimitive.int")
            appendLine("    } catch (_: IllegalArgumentException) {")
            appendLine("        requireNotNull(value.jsonObject[\"__webPresenterRef\"]?.jsonPrimitive).int")
            appendLine("    }")
            appendLine()

            allRefs.forEach { ref ->
                appendRefJsonFunctions(ref)
                appendLine()
            }
            allSealeds.forEach { sealed ->
                appendSealedJsonFunctions(
                    sealed = sealed,
                    renderDecoder = sealed.type in inputSealedTypes,
                )
                appendLine()
            }
            allArrays.forEach { array ->
                appendArrayJsonFunctions(array)
                appendLine()
            }
            allStateCodecValues.forEach { value ->
                value.codec.appendJsonFunctions(this, value)
                appendLine()
            }

            presenters.forEach { presenter ->
                presenter.parameters
                    .filter { it.callback != null }
                    .forEach { parameter ->
                        val callback = requireNotNull(parameter.callback)
                        appendLine("private external interface ${presenter.callbackInterfaceName(parameter)} : JsAny {")
                        appendLine("    @nativeInvoke")
                        appendLine("    operator fun invoke(${callback.kotlinParameterDeclarations()})")
                        appendLine("}")
                        appendLine()
                    }
            }

            presenters.forEach { presenter ->
                val stateDispatchProperties =
                    presenter
                        .properties
                        .mapNotNull { property ->
                            property.valueModel().stateCodecValue()?.takeIf { it.codec.handlesDispatch }?.let { value ->
                                property to value
                            }
                        }
                val stateCallProperties =
                    presenter
                        .properties
                        .mapNotNull { property ->
                            property.valueModel().stateCodecValue()?.takeIf { it.codec.handlesCall }?.let { value ->
                                property to value
                            }
                        }
                appendLine("internal object ${presenter.specName} : WebPresenterSpec {")
                appendLine("    override val name: String = ${presenter.name.kotlinString()}")
                appendLine()
                appendLine("    override fun create(")
                appendLine("        argsJson: String,")
                appendLine("        callbacks: JsArray<JsAny>,")
                appendLine("    ): WebPresenterBinding {")
                if (!presenter.creatable) {
                    appendLine("        error(\"Web presenter ${presenter.name} cannot be created directly.\")")
                } else {
                    if (presenter.parameters.any { it.callback == null }) {
                        appendLine("        val createArgs = WebPresenterJson.parseToJsonElement(argsJson).jsonObject")
                    }
                    presenter.parameters
                        .filter { it.callback != null }
                        .forEachIndexed { callbackIndex, parameter ->
                            appendLine("        val ${parameter.callbackVariableName()} =")
                            if (parameter.nullable) {
                                appendLine("            callbacks[$callbackIndex] as ${presenter.callbackInterfaceName(parameter)}?")
                            } else {
                                appendLine(
                                    "            requireNotNull(callbacks[$callbackIndex]) as ${presenter.callbackInterfaceName(
                                        parameter,
                                    )}",
                                )
                            }
                        }
                    appendLine("        val presenter = ${presenter.presenterType}(")
                    presenter.parameters.forEach { parameter ->
                        parameter.callback?.let { callback ->
                            if (parameter.nullable) {
                                appendLine("            ${parameter.name} = ${parameter.callbackVariableName()}?.let { callback ->")
                                appendLine("                { ${callback.kotlinParameterList()} ->")
                                appendLine("                    callback(${callback.kotlinArgumentList()})")
                                appendLine("                }")
                                appendLine("            },")
                            } else {
                                appendLine("            ${parameter.name} = { ${callback.kotlinParameterList()} ->")
                                appendLine("                ${parameter.callbackVariableName()}(${callback.kotlinArgumentList()})")
                                appendLine("            },")
                            }
                        } ?: appendLine(
                            "            ${parameter.name} = ${
                                parameter.valueModel().readJsonExpression(
                                    objectName = "createArgs",
                                    argumentName = parameter.name,
                                    refsName = "emptyArray<JsReference<Any>>().toJsArray()",
                                )
                            },",
                        )
                    }
                    appendLine("        )")
                    appendLine("        return bindPresenter(presenter)")
                }
                appendLine("    }")
                appendLine()
                appendLine("    override fun bind(presenter: Any): WebPresenterBinding =")
                appendLine("        bindPresenter(presenter as ${presenter.presenterType})")
                appendLine()
                appendLine("    private fun bindPresenter(presenter: ${presenter.presenterType}): WebPresenterBinding {")
                appendLine("        return object : WebPresenterBinding {")
                appendLine("            override val models: StateFlow<Any> = presenter.models as StateFlow<Any>")
                appendLine()
                appendLine("            override fun encode(model: Any): WebPresenterSnapshot {")
                appendLine("                val state = model as ${presenter.stateType}")
                appendLine("                val refs = mutableListOf<JsReference<Any>>()")
                appendLine("                val json = buildJsonObject {")
                presenter.properties.forEach { property ->
                    appendPutValue(
                        key = property.name.kotlinString(),
                        value = property.valueModel(),
                        valueExpression = "state.${property.name}",
                        refsName = "refs",
                        indent = "                    ",
                    )
                }
                appendLine("                }.toString()")
                appendLine("                return WebPresenterSnapshot(")
                appendLine("                    json = json,")
                appendLine("                    refs = refs.toJsArray(),")
                appendLine("                )")
                appendLine("            }")
                appendLine()
                appendLine("            override fun dispatch(")
                appendLine("                model: Any,")
                appendLine("                actionJson: String,")
                appendLine("                refs: JsArray<JsReference<Any>>,")
                appendLine("            ) {")
                appendLine("                val state = model as ${presenter.stateType}")
                if (presenter.actions.any { it.args.isNotEmpty() } || stateDispatchProperties.isNotEmpty()) {
                    appendLine("                val action = WebPresenterJson.parseToJsonElement(actionJson).jsonObject")
                    appendLine("                val args = action[\"args\"]?.jsonObject")
                }
                appendLine("                when (webPresenterActionPath(actionJson)) {")
                stateDispatchProperties.forEach { (property, value) ->
                    value.codec.appendDispatchCase(this, property, value)
                }
                presenter.actions.forEach { action ->
                    appendLine("                    ${action.name.kotlinString()} -> state.${action.name}(")
                    appendArguments(action.args, refsName = "refs", indent = "                        ")
                    appendLine("                    )")
                }
                appendLine(
                    "                    else -> error(\"Unknown ${presenter.name} action: \${webPresenterActionPath(actionJson)}\")",
                )
                appendLine("                }")
                appendLine("            }")
                appendLine()
                appendLine("            override fun call(")
                appendLine("                model: Any,")
                appendLine("                actionJson: String,")
                appendLine("                refs: JsArray<JsReference<Any>>,")
                appendLine("            ): WebPresenterSnapshot {")
                appendLine("                val state = model as ${presenter.stateType}")
                appendLine("                val action = WebPresenterJson.parseToJsonElement(actionJson).jsonObject")
                appendLine("                val args = action[\"args\"]?.jsonObject")
                appendLine("                return when (webPresenterActionPath(actionJson)) {")
                stateCallProperties.forEach { (property, value) ->
                    value.codec.appendCallCase(this, property, value)
                }
                presenter.actions
                    .filter { action -> action.returnValue != null }
                    .forEach { action ->
                        appendLine("                    ${action.name.kotlinString()} -> {")
                        appendLine("                        val result = state.${action.name}(")
                        appendArguments(action.args, refsName = "refs", indent = "                            ")
                        appendLine("                        )")
                        appendReturnSnapshot(
                            returnValue = requireNotNull(action.returnValue),
                            valueExpression = "result",
                            indent = "                        ",
                        )
                        appendLine("                    }")
                    }
                presenter.referencedRefs().forEach { ref ->
                    ref.methods.forEach { method ->
                        appendLine("                    ${ref.methodPath(method).kotlinString()} -> {")
                        val receiverIndex =
                            WebType.Int.readJsonExpression(
                                objectName = "requireNotNull(args)",
                                argumentName = RECEIVER_REF_ARGUMENT,
                            )
                        appendLine("                        val receiver =")
                        appendLine("                            requireNotNull(refs[$receiverIndex]).get() as ${ref.type}")
                        if (method.returnValue == null) {
                            appendLine("                        receiver.${method.name}(")
                        } else {
                            appendLine("                        val result = receiver.${method.name}(")
                        }
                        appendArguments(method.args, refsName = "refs", indent = "                            ")
                        appendLine("                        )")
                        appendReturnSnapshot(
                            returnValue = method.returnValue,
                            valueExpression = "result",
                            indent = "                        ",
                        )
                        appendLine("                    }")
                    }
                }
                appendLine(
                    "                    else -> error(\"Unknown ${presenter.name} call: \${webPresenterActionPath(actionJson)}\")",
                )
                appendLine("                }")
                appendLine("            }")
                appendLine()
                appendLine("            override fun close() {")
                appendLine("                presenter.close()")
                appendLine("            }")
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
                appendLine()
            }
            appendLine("internal object GeneratedWebPresenterRegistry {")
            appendLine("    val specs: Map<String, WebPresenterSpec> =")
            appendLine("        mapOf(")
            presenters.forEach { presenter ->
                appendLine("            ${presenter.name.kotlinString()} to ${presenter.specName},")
            }
            appendLine("        )")
            appendLine("}")
        }

    private fun StringBuilder.appendArguments(
        args: List<ArgumentModel>,
        refsName: String,
        indent: String,
    ) {
        args.forEach { argument ->
            appendLine(
                "$indent${argument.name} = ${
                    argument.valueModel().readJsonExpression(
                        objectName = "requireNotNull(args)",
                        argumentName = argument.name,
                        refsName = refsName,
                    )
                },",
            )
        }
    }

    private fun StringBuilder.appendReturnSnapshot(
        returnValue: ReturnModel?,
        valueExpression: String,
        indent: String,
    ) {
        if (returnValue == null) {
            appendLine("${indent}WebPresenterSnapshot(")
            appendLine("$indent    json = \"{}\",")
            appendLine("$indent    refs = emptyArray<JsReference<Any>>().toJsArray(),")
            appendLine("$indent)")
            return
        }

        appendLine("${indent}val resultRefs = mutableListOf<JsReference<Any>>()")
        appendLine("${indent}val json = buildJsonObject {")
        appendPutValue(
            key = "\"value\"",
            value = returnValue.valueModel(),
            valueExpression = valueExpression,
            refsName = "resultRefs",
            indent = "$indent    ",
        )
        appendLine("$indent}.toString()")
        appendLine("${indent}WebPresenterSnapshot(")
        appendLine("$indent    json = json,")
        appendLine("$indent    refs = resultRefs.toJsArray(),")
        appendLine("$indent)")
    }

    private fun StringBuilder.appendPutValue(
        key: String,
        value: WebValueModel,
        valueExpression: String,
        refsName: String,
        indent: String,
    ) {
        appendLine(
            "${indent}put($key, ${value.writeJsonElementExpression(valueExpression = valueExpression, refsName = refsName)})",
        )
    }

    private fun StringBuilder.appendAddValue(
        value: WebValueModel,
        valueExpression: String,
        refsName: String,
        indent: String,
    ) {
        appendLine(
            "${indent}add(${value.writeJsonElementExpression(valueExpression = valueExpression, refsName = refsName)})",
        )
    }

    private fun StringBuilder.appendRefObject(
        ref: RefModel,
        valueExpression: String,
        refsName: String,
        indent: String,
    ) {
        appendLine("${indent}put(\"__webPresenterRef\", $refsName.size)")
        appendLine("$indent$refsName += ($valueExpression as Any).toJsReference()")
        ref.properties.forEach { refProperty ->
            appendPutValue(
                key = refProperty.name.kotlinString(),
                value = refProperty.valueModel(),
                valueExpression = "$valueExpression.${refProperty.name}",
                refsName = refsName,
                indent = indent,
            )
        }
    }

    private fun StringBuilder.appendRefJsonFunctions(ref: RefModel) {
        appendLine("private fun ${ref.encoderName()}(")
        appendLine("    value: ${ref.type},")
        appendLine("    refs: MutableList<JsReference<Any>>,")
        appendLine("): JsonElement =")
        appendLine("    buildJsonObject {")
        appendRefObject(
            ref = ref,
            valueExpression = "value",
            refsName = "refs",
            indent = "        ",
        )
        appendLine("    }")
    }

    private fun StringBuilder.appendArrayJsonFunctions(array: ArrayModel) {
        appendLine("private fun ${array.encoderName()}(")
        appendLine("    value: ${array.type},")
        appendLine("    refs: MutableList<JsReference<Any>>,")
        appendLine("): JsonElement =")
        appendLine("    buildJsonArray {")
        appendLine("        value.forEach { item ->")
        appendAddValue(
            value = array.item,
            valueExpression = "item",
            refsName = "refs",
            indent = "            ",
        )
        appendLine("        }")
        appendLine("    }")
    }

    private fun StringBuilder.appendSealedJsonFunctions(
        sealed: SealedModel,
        renderDecoder: Boolean,
    ) {
        appendLine("private fun ${sealed.encoderName()}(")
        appendLine("    value: ${sealed.type},")
        appendLine("    refs: MutableList<JsReference<Any>>,")
        appendLine("): JsonElement =")
        appendLine("    when (value) {")
        sealed.variants.forEach { variant ->
            appendLine("        is ${variant.type} -> buildJsonObject {")
            appendLine("            put($SEALED_DISCRIMINATOR_JSON, ${variant.tag.kotlinString()})")
            variant.properties.forEach { property ->
                appendPutValue(
                    key = property.name.kotlinString(),
                    value = property.valueModel(),
                    valueExpression = "value.${property.name}",
                    refsName = "refs",
                    indent = "            ",
                )
            }
            appendLine("        }")
        }
        appendLine("        else -> error(\"Unsupported ${sealed.typeName} variant: \${value::class}\")")
        appendLine("    }")
        if (!renderDecoder) return
        appendLine()
        appendLine(
            "private fun ${sealed.decoderName()}(value: JsonElement, refs: JsArray<JsReference<Any>>): ${sealed.type} {",
        )
        appendLine("    val json = value.jsonObject")
        appendLine("    return when (val type = requireNotNull(json[$SEALED_DISCRIMINATOR_JSON]?.jsonPrimitive).content) {")
        sealed.variants.forEach { variant ->
            if (variant.objectLike) {
                appendLine("        ${variant.tag.kotlinString()} -> ${variant.type}")
            } else {
                appendLine("        ${variant.tag.kotlinString()} -> ${variant.type}(")
                variant.properties.forEach { property ->
                    appendLine(
                        "            ${property.name} = ${
                            property.valueModel().readJsonExpression(
                                objectName = "json",
                                argumentName = property.name,
                                refsName = "refs",
                            )
                        },",
                    )
                }
                appendLine("        )")
            }
        }
        appendLine("        else -> error(\"Unknown ${sealed.typeName} variant: \$type\")")
        appendLine("    }")
        appendLine("}")
    }

    fun renderManifest(presenters: List<PresenterModel>): String =
        buildString {
            appendLine("{")
            appendLine("  \"presenters\": [")
            presenters.forEachIndexed { index, presenter ->
                appendLine("    {")
                appendLine("      \"name\": ${presenter.name.jsonString()},")
                appendLine("      \"factory\": ${presenter.factoryName.jsonString()},")
                appendLine("      \"bindFactory\": ${presenter.bindFactoryName.jsonString()},")
                appendLine("      \"creatable\": ${presenter.creatable},")
                appendLine("      \"stateType\": ${presenter.stateTypeName.jsonString()},")
                appendLine("      \"parameters\": [")
                presenter.parameters.forEachIndexed { parameterIndex, parameter ->
                    append("        { \"name\": ${parameter.name.jsonString()}, ")
                    if (parameter.callback == null) {
                        appendValueManifest(parameter.valueModel(), includeValueKind = true)
                    }
                    parameter.callback?.let { callback ->
                        append("\"kind\": \"callback\", \"args\": [")
                        callback.args.forEachIndexed { callbackArgumentIndex, callbackArgument ->
                            append("{ \"name\": ${callbackArgument.name.jsonString()}, ")
                            appendValueManifest(callbackArgument.valueModel(), includeValueKind = false)
                            append(" }")
                            if (callbackArgumentIndex != callback.args.lastIndex) {
                                append(", ")
                            }
                        }
                        append("]")
                        appendNullable(parameter.nullable)
                    }
                    append(" }")
                    appendLine(if (parameterIndex == presenter.parameters.lastIndex) "" else ",")
                }
                appendLine("      ],")
                appendLine("      \"properties\": [")
                presenter.properties.forEachIndexed { propertyIndex, property ->
                    append("        { \"name\": ${property.name.jsonString()}, ")
                    appendValueManifest(property.valueModel(), includeValueKind = true)
                    append(" }")
                    appendLine(if (propertyIndex == presenter.properties.lastIndex) "" else ",")
                }
                appendLine("      ],")
                appendLine("      \"actions\": [")
                presenter.actions.forEachIndexed { actionIndex, action ->
                    appendLine("        {")
                    appendLine("          \"name\": ${action.name.jsonString()},")
                    appendLine("          \"args\": [")
                    action.args.forEachIndexed { argumentIndex, argument ->
                        append("            { \"name\": ${argument.name.jsonString()}, ")
                        appendValueManifest(argument.valueModel(), includeValueKind = true)
                        append(" }")
                        appendLine(if (argumentIndex == action.args.lastIndex) "" else ",")
                    }
                    append("          ]")
                    action.returnValue?.let { returnValue ->
                        appendLine(",")
                        append("          \"return\": ")
                        appendLine(returnValue.toManifestJson())
                    } ?: appendLine()
                    append("        }")
                    appendLine(if (actionIndex == presenter.actions.lastIndex) "" else ",")
                }
                appendLine("      ]")
                append("    }")
                appendLine(if (index == presenters.lastIndex) "" else ",")
            }
            appendLine("  ]")
            appendLine("}")
        }
}

internal data class PresenterModel(
    val name: String,
    val factoryName: String,
    val bindFactoryName: String = name.toBindFactoryName(),
    val presenterType: String,
    val specName: String,
    val stateType: String,
    val stateTypeName: String,
    val creatable: Boolean = true,
    val parameters: List<ArgumentModel>,
    val properties: List<PropertyModel>,
    val actions: List<ActionModel>,
    val containingFiles: Set<KSFile> = emptySet(),
)

internal data class PropertyModel(
    val name: String,
    val webType: WebType?,
    val enum: EnumModel? = null,
    val sealed: SealedModel? = null,
    val ref: RefModel?,
    val array: ArrayModel? = null,
    val uiState: UiStateModel? = null,
    val pagingState: PagingStateModel? = null,
    val nullable: Boolean = false,
)

internal data class ActionModel(
    val name: String,
    val args: List<ArgumentModel>,
    val returnValue: ReturnModel? = null,
)

internal data class ArgumentModel(
    val name: String,
    val webType: WebType?,
    val enum: EnumModel? = null,
    val sealed: SealedModel? = null,
    val ref: RefModel?,
    val callback: CallbackModel?,
    val array: ArrayModel? = null,
    val uiState: UiStateModel? = null,
    val pagingState: PagingStateModel? = null,
    val nullable: Boolean = false,
)

internal data class EnumModel(
    val type: String,
    val typeName: String,
    val values: List<String>,
)

internal data class SealedModel(
    val type: String,
    val typeName: String,
    val variants: List<SealedVariantModel>,
)

internal data class SealedVariantModel(
    val type: String,
    val typeName: String,
    val tag: String,
    val objectLike: Boolean,
    val properties: List<SealedPropertyModel>,
)

internal data class SealedPropertyModel(
    val name: String,
    val webType: WebType?,
    val enum: EnumModel? = null,
    val sealed: SealedModel? = null,
    val ref: RefModel? = null,
    val array: ArrayModel? = null,
    val uiState: UiStateModel? = null,
    val pagingState: PagingStateModel? = null,
    val nullable: Boolean = false,
)

internal data class RefModel(
    val type: String,
    val typeName: String,
    val properties: List<RefPropertyModel>,
    val methods: List<RefMethodModel> = emptyList(),
    val kind: RefModelKind = RefModelKind.Object,
)

internal enum class RefModelKind(
    val manifestName: String,
) {
    Object("object"),
    Presenter("presenter"),
}

internal data class RefPropertyModel(
    val name: String,
    val webType: WebType?,
    val enum: EnumModel? = null,
    val sealed: SealedModel? = null,
    val ref: RefModel? = null,
    val array: ArrayModel? = null,
    val uiState: UiStateModel? = null,
    val pagingState: PagingStateModel? = null,
    val nullable: Boolean = false,
)

internal data class RefMethodModel(
    val name: String,
    val args: List<ArgumentModel>,
    val returnValue: ReturnModel?,
)

internal data class ReturnModel(
    val webType: WebType?,
    val enum: EnumModel? = null,
    val sealed: SealedModel? = null,
    val ref: RefModel?,
    val array: ArrayModel? = null,
    val uiState: UiStateModel? = null,
    val pagingState: PagingStateModel? = null,
    val nullable: Boolean = false,
)

internal data class CallbackModel(
    val args: List<CallbackArgumentModel>,
)

internal data class CallbackArgumentModel(
    val name: String,
    val webType: WebType?,
    val enum: EnumModel? = null,
    val sealed: SealedModel? = null,
    val array: ArrayModel? = null,
    val uiState: UiStateModel? = null,
    val pagingState: PagingStateModel? = null,
    val nullable: Boolean = false,
)

internal data class ArrayModel(
    val item: WebValueModel,
    val type: String = "kotlin.collections.List<${item.kotlinType()}>",
)

internal data class UiStateModel(
    val data: WebValueModel,
    val type: String = "dev.dimension.flare.ui.model.UiState<${data.kotlinType()}>",
)

internal data class PagingStateModel(
    val item: WebValueModel,
    val type: String = "dev.dimension.flare.common.PagingState<${item.kotlinType()}>",
)

private data class StateCodecValue(
    val codec: StateCodec,
    val type: String,
    val content: WebValueModel,
)

private interface StateCodec {
    val kind: String
    val encoderSuffix: String
    val manifestContentName: String
    val handlesDispatch: Boolean
        get() = false
    val handlesCall: Boolean
        get() = false

    fun model(value: WebValueModel): StateCodecValue?

    fun parse(
        type: KSType,
        visited: Set<String>,
        analyze: (KSType, Set<String>) -> WebValueModel?,
    ): WebValueModel?

    fun appendJsonFunctions(
        builder: StringBuilder,
        value: StateCodecValue,
    )

    fun appendDispatchCase(
        builder: StringBuilder,
        property: PropertyModel,
        value: StateCodecValue,
    ) = Unit

    fun appendCallCase(
        builder: StringBuilder,
        property: PropertyModel,
        value: StateCodecValue,
    ) = Unit

    fun readJsonElementExpression(
        value: StateCodecValue,
        nullable: Boolean,
        elementExpression: String,
        refsName: String,
    ): String = error("${kind.replaceFirstChar(Char::uppercaseChar)} values cannot be read from web.")

    fun writeJsonElementExpression(
        value: StateCodecValue,
        nullable: Boolean,
        valueExpression: String,
        refsName: String,
    ): String =
        if (nullable) {
            "$valueExpression?.let { ${encoderName(value)}(it, $refsName) } ?: JsonNull"
        } else {
            "${encoderName(value)}($valueExpression, $refsName)"
        }

    fun encoderName(value: StateCodecValue): String = "encode${value.type.toKotlinIdentifierSuffix()}Web$encoderSuffix"

    fun appendManifest(
        builder: StringBuilder,
        value: StateCodecValue,
        nullable: Boolean,
    ) {
        builder.append("\"kind\": ${kind.jsonString()}, \"${manifestContentName}\": { ")
        builder.appendValueManifest(value.content, includeValueKind = true)
        builder.append(" }")
        builder.appendNullable(nullable)
    }
}

private object UiStateCodec : StateCodec {
    override val kind: String = "uiState"
    override val encoderSuffix: String = "UiState"
    override val manifestContentName: String = "data"

    override fun model(value: WebValueModel): StateCodecValue? =
        value.uiState?.let {
            StateCodecValue(
                codec = this,
                type = it.type,
                content = it.data,
            )
        }

    override fun parse(
        type: KSType,
        visited: Set<String>,
        analyze: (KSType, Set<String>) -> WebValueModel?,
    ): WebValueModel? {
        if (type.declaration.qualifiedName?.asString() != UI_STATE) return null
        val dataType =
            type.arguments
                .firstOrNull()
                ?.type
                ?.resolve() ?: return null
        val data = analyze(dataType, visited) ?: return null
        return WebValueModel(
            uiState =
                UiStateModel(
                    data = data,
                    type = "$UI_STATE<${dataType.kotlinTypeName()}>",
                ),
            nullable = type.isMarkedNullable,
        )
    }

    override fun appendJsonFunctions(
        builder: StringBuilder,
        value: StateCodecValue,
    ) {
        builder.appendLine("private fun ${encoderName(value)}(")
        builder.appendLine("    value: ${value.type},")
        builder.appendLine("    refs: MutableList<JsReference<Any>>,")
        builder.appendLine("): JsonElement =")
        builder.appendLine("    when (value) {")
        builder.appendLine("        is dev.dimension.flare.ui.model.UiState.Loading -> buildJsonObject {")
        builder.appendLine("            put($SEALED_DISCRIMINATOR_JSON, \"Loading\")")
        builder.appendLine("        }")
        builder.appendLine("        is dev.dimension.flare.ui.model.UiState.Error -> buildJsonObject {")
        builder.appendLine("            put($SEALED_DISCRIMINATOR_JSON, \"Error\")")
        builder.appendLine("            put(\"message\", value.throwable.message)")
        builder.appendLine("        }")
        builder.appendLine("        is dev.dimension.flare.ui.model.UiState.Success -> buildJsonObject {")
        builder.appendLine("            put($SEALED_DISCRIMINATOR_JSON, \"Success\")")
        builder.appendLine(
            "            put(\"data\", ${value.content.writeJsonElementExpression(valueExpression = "value.data", refsName = "refs")})",
        )
        builder.appendLine("        }")
        builder.appendLine("    }")
    }
}

private object PagingStateCodec : StateCodec {
    override val kind: String = "pagingState"
    override val encoderSuffix: String = "PagingState"
    override val manifestContentName: String = "item"
    override val handlesDispatch: Boolean = true
    override val handlesCall: Boolean = true

    override fun model(value: WebValueModel): StateCodecValue? =
        value.pagingState?.let {
            StateCodecValue(
                codec = this,
                type = it.type,
                content = it.item,
            )
        }

    override fun parse(
        type: KSType,
        visited: Set<String>,
        analyze: (KSType, Set<String>) -> WebValueModel?,
    ): WebValueModel? {
        if (type.declaration.qualifiedName?.asString() != PAGING_STATE) return null
        val itemType =
            type.arguments
                .firstOrNull()
                ?.type
                ?.resolve() ?: return null
        val item = analyze(itemType, visited) ?: return null
        return WebValueModel(
            pagingState =
                PagingStateModel(
                    item = item,
                    type = "$PAGING_STATE<${itemType.kotlinTypeName()}>",
                ),
            nullable = type.isMarkedNullable,
        )
    }

    override fun appendJsonFunctions(
        builder: StringBuilder,
        value: StateCodecValue,
    ) {
        builder.appendLine("private fun ${encoderName(value)}(")
        builder.appendLine("    value: ${value.type},")
        builder.appendLine("    refs: MutableList<JsReference<Any>>,")
        builder.appendLine("): JsonElement =")
        builder.appendLine("    when (value) {")
        builder.appendLine("        is dev.dimension.flare.common.PagingState.Loading -> buildJsonObject {")
        builder.appendLine("            put($SEALED_DISCRIMINATOR_JSON, \"Loading\")")
        builder.appendLine("        }")
        builder.appendLine("        is dev.dimension.flare.common.PagingState.Error -> buildJsonObject {")
        builder.appendLine("            put($SEALED_DISCRIMINATOR_JSON, \"Error\")")
        builder.appendLine("            put(\"message\", value.error.message)")
        builder.appendLine("        }")
        builder.appendLine("        is dev.dimension.flare.common.PagingState.Empty -> buildJsonObject {")
        builder.appendLine("            put($SEALED_DISCRIMINATOR_JSON, \"Empty\")")
        builder.appendLine("        }")
        builder.appendLine("        is dev.dimension.flare.common.PagingState.Success -> buildJsonObject {")
        builder.appendLine("            put($SEALED_DISCRIMINATOR_JSON, \"Success\")")
        builder.appendLine("            put(\"itemCount\", value.itemCount)")
        builder.appendLine("            put(\"isRefreshing\", value.isRefreshing)")
        builder.appendLine("        }")
        builder.appendLine("    }")
    }

    override fun appendDispatchCase(
        builder: StringBuilder,
        property: PropertyModel,
        value: StateCodecValue,
    ) {
        builder.appendLine("                    ${property.pagingGetPath().kotlinString()} -> {")
        builder.appendLine("                        val index = requireNotNull(requireNotNull(args)[\"index\"]?.jsonPrimitive).int")
        builder.appendLine("                        val pagingState = state.${property.name}")
        builder.appendLine(
            "                        if (pagingState is dev.dimension.flare.common.PagingState.Success<*> && index >= 0 && index < pagingState.itemCount) {",
        )
        builder.appendLine("                            pagingState[index]")
        builder.appendLine("                        }")
        builder.appendLine("                    }")
    }

    override fun appendCallCase(
        builder: StringBuilder,
        property: PropertyModel,
        value: StateCodecValue,
    ) {
        val itemType = value.content.kotlinType().removeSuffix("?")
        val nullableItem = value.content.copy(nullable = true)
        builder.appendLine("                    ${property.pagingPeekPath().kotlinString()} -> {")
        builder.appendLine("                        val index = requireNotNull(requireNotNull(args)[\"index\"]?.jsonPrimitive).int")
        builder.appendLine(
            "                        val pagingState = state.${property.name} as? dev.dimension.flare.common.PagingState.Success<$itemType>",
        )
        builder.appendLine("                        val result =")
        builder.appendLine("                            if (pagingState != null && index >= 0 && index < pagingState.itemCount) {")
        builder.appendLine("                                pagingState.peek(index)")
        builder.appendLine("                            } else {")
        builder.appendLine("                                null")
        builder.appendLine("                            }")
        builder.appendLine("                        val resultRefs = mutableListOf<JsReference<Any>>()")
        builder.appendLine("                        val json = buildJsonObject {")
        builder.appendLine(
            "                            put(\"value\", ${nullableItem.writeJsonElementExpression(
                valueExpression = "result",
                refsName = "resultRefs",
            )})",
        )
        builder.appendLine("                        }.toString()")
        builder.appendLine("                        WebPresenterSnapshot(")
        builder.appendLine("                            json = json,")
        builder.appendLine("                            refs = resultRefs.toJsArray(),")
        builder.appendLine("                        )")
        builder.appendLine("                    }")
    }
}

private val stateCodecs: List<StateCodec> = listOf(UiStateCodec, PagingStateCodec)

private interface RefCodecContext {
    fun opaqueRef(
        type: KSType,
        declaration: KSClassDeclaration,
        kind: RefModelKind,
    ): RefModel

    fun objectRef(
        type: KSType,
        declaration: KSClassDeclaration,
        visited: Set<String>,
    ): RefModel?
}

private interface RefCodec {
    fun parse(
        type: KSType,
        declaration: KSClassDeclaration,
        visited: Set<String>,
        context: RefCodecContext,
    ): RefModel?
}

private object PresenterRefCodec : RefCodec {
    override fun parse(
        type: KSType,
        declaration: KSClassDeclaration,
        visited: Set<String>,
        context: RefCodecContext,
    ): RefModel? {
        if (!declaration.hasAnnotation(WEB_PRESENTER_SHORT_NAME)) return null
        return context.opaqueRef(
            type = type,
            declaration = declaration,
            kind = RefModelKind.Presenter,
        )
    }
}

private object ObjectRefCodec : RefCodec {
    override fun parse(
        type: KSType,
        declaration: KSClassDeclaration,
        visited: Set<String>,
        context: RefCodecContext,
    ): RefModel? {
        if (declaration.hasAnnotation(WEB_PRESENTER_SHORT_NAME)) return null
        if (!BridgePolicy.canBridgeAsRef(declaration)) return null
        return context.objectRef(
            type = type,
            declaration = declaration,
            visited = visited,
        )
    }
}

private val refCodecs: List<RefCodec> = listOf(PresenterRefCodec, ObjectRefCodec)

internal data class WebValueModel(
    val webType: WebType? = null,
    val enum: EnumModel? = null,
    val sealed: SealedModel? = null,
    val ref: RefModel? = null,
    val array: ArrayModel? = null,
    val uiState: UiStateModel? = null,
    val pagingState: PagingStateModel? = null,
    val nullable: Boolean = false,
)

internal enum class WebType(
    val tsType: String,
    val jsonPrimitiveProperty: String,
    val kotlinType: String,
) {
    Boolean("boolean", "boolean", "Boolean"),
    Double("number", "double", "Double"),
    Float("number", "float", "Float"),
    Int("number", "int", "Int"),
    Long("number", "long", "Long"),
    String("string", "content", "String"),
}

private fun PropertyModel.valueModel(): WebValueModel =
    WebValueModel(
        webType = webType,
        enum = enum,
        sealed = sealed,
        ref = ref,
        array = array,
        uiState = uiState,
        pagingState = pagingState,
        nullable = nullable,
    )

private fun ArgumentModel.valueModel(): WebValueModel =
    WebValueModel(
        webType = webType,
        enum = enum,
        sealed = sealed,
        ref = ref,
        array = array,
        uiState = uiState,
        pagingState = pagingState,
        nullable = nullable,
    )

private fun SealedPropertyModel.valueModel(): WebValueModel =
    WebValueModel(
        webType = webType,
        enum = enum,
        sealed = sealed,
        ref = ref,
        array = array,
        uiState = uiState,
        pagingState = pagingState,
        nullable = nullable,
    )

private fun RefPropertyModel.valueModel(): WebValueModel =
    WebValueModel(
        webType = webType,
        enum = enum,
        sealed = sealed,
        ref = ref,
        array = array,
        uiState = uiState,
        pagingState = pagingState,
        nullable = nullable,
    )

private fun ReturnModel.valueModel(): WebValueModel =
    WebValueModel(
        webType = webType,
        enum = enum,
        sealed = sealed,
        ref = ref,
        array = array,
        uiState = uiState,
        pagingState = pagingState,
        nullable = nullable,
    )

private fun CallbackArgumentModel.valueModel(): WebValueModel =
    WebValueModel(
        webType = webType,
        enum = enum,
        sealed = sealed,
        array = array,
        uiState = uiState,
        pagingState = pagingState,
        nullable = nullable,
    )

private fun PresenterModel.referencedRefs(): List<RefModel> =
    buildList {
        parameters.forEach { it.valueModel().collectRefs(this) }
        parameters.mapNotNull { it.callback }.flatMap { it.args }.forEach { it.valueModel().collectRefs(this) }
        properties.forEach { it.valueModel().collectRefs(this) }
        actions.forEach { action ->
            action.args.forEach { it.valueModel().collectRefs(this) }
            action.returnValue?.valueModel()?.collectRefs(this)
        }
    }

private fun PresenterModel.referencedSealeds(): List<SealedModel> =
    buildList {
        parameters.forEach { it.valueModel().collectSealeds(this) }
        parameters.mapNotNull { it.callback }.flatMap { it.args }.forEach { it.valueModel().collectSealeds(this) }
        properties.forEach { it.valueModel().collectSealeds(this) }
        actions.forEach { action ->
            action.args.forEach { it.valueModel().collectSealeds(this) }
            action.returnValue?.valueModel()?.collectSealeds(this)
        }
    }

private fun PresenterModel.referencedInputSealeds(): List<SealedModel> =
    buildList {
        parameters
            .filter { it.callback == null }
            .forEach { it.valueModel().collectSealeds(this) }
        actions.forEach { action ->
            action.args.forEach { it.valueModel().collectSealeds(this) }
        }
        referencedRefs().forEach { ref ->
            ref.methods.forEach { method ->
                method.args.forEach { it.valueModel().collectSealeds(this) }
            }
        }
    }

private fun PresenterModel.referencedArrays(): List<ArrayModel> =
    buildList {
        parameters.forEach { it.valueModel().collectArrays(this) }
        parameters.mapNotNull { it.callback }.flatMap { it.args }.forEach { it.valueModel().collectArrays(this) }
        properties.forEach { it.valueModel().collectArrays(this) }
        actions.forEach { action ->
            action.args.forEach { it.valueModel().collectArrays(this) }
            action.returnValue?.valueModel()?.collectArrays(this)
        }
    }

private fun PresenterModel.referencedStateCodecValues(): List<StateCodecValue> =
    buildList {
        parameters.forEach { it.valueModel().collectStateCodecValues(this) }
        parameters.mapNotNull { it.callback }.flatMap { it.args }.forEach { it.valueModel().collectStateCodecValues(this) }
        properties.forEach { it.valueModel().collectStateCodecValues(this) }
        actions.forEach { action ->
            action.args.forEach { it.valueModel().collectStateCodecValues(this) }
            action.returnValue?.valueModel()?.collectStateCodecValues(this)
        }
    }

private fun MutableList<RefModel>.addRef(ref: RefModel) {
    if (any { it.type == ref.type }) return
    add(ref)
    if (ref.kind == RefModelKind.Presenter) return
    ref.properties.forEach { it.valueModel().collectRefs(this) }
    ref.methods.forEach { method ->
        method.args.forEach { it.valueModel().collectRefs(this) }
        method.returnValue?.valueModel()?.collectRefs(this)
    }
}

private fun MutableList<SealedModel>.addSealed(sealed: SealedModel) {
    if (any { it.type == sealed.type }) return
    add(sealed)
    sealed.variants.forEach { variant ->
        variant.properties.forEach { it.valueModel().collectSealeds(this) }
    }
}

private fun MutableList<ArrayModel>.addArray(array: ArrayModel) {
    if (any { it.type == array.type }) return
    add(array)
    array.item.collectArrays(this)
}

private fun MutableList<StateCodecValue>.addStateCodecValue(value: StateCodecValue) {
    if (any { it.codec.kind == value.codec.kind && it.type == value.type }) return
    add(value)
    value.content.collectStateCodecValues(this)
}

private fun WebValueModel.collectRefs(refs: MutableList<RefModel>) {
    ref?.let(refs::addRef)
    sealed?.variants?.forEach { variant -> variant.properties.forEach { it.valueModel().collectRefs(refs) } }
    array?.item?.collectRefs(refs)
    stateCodecValue()?.content?.collectRefs(refs)
}

private fun WebValueModel.collectSealeds(sealeds: MutableList<SealedModel>) {
    sealed?.let(sealeds::addSealed)
    ref?.let { ref ->
        if (ref.kind == RefModelKind.Presenter) return@let
        ref.properties.forEach { it.valueModel().collectSealeds(sealeds) }
        ref.methods.forEach { method ->
            method.args.forEach { it.valueModel().collectSealeds(sealeds) }
            method.returnValue?.valueModel()?.collectSealeds(sealeds)
        }
    }
    array?.item?.collectSealeds(sealeds)
    stateCodecValue()?.content?.collectSealeds(sealeds)
}

private fun WebValueModel.collectArrays(arrays: MutableList<ArrayModel>) {
    array?.let(arrays::addArray)
    ref?.let { ref ->
        if (ref.kind == RefModelKind.Presenter) return@let
        ref.properties.forEach { it.valueModel().collectArrays(arrays) }
        ref.methods.forEach { method ->
            method.args.forEach { it.valueModel().collectArrays(arrays) }
            method.returnValue?.valueModel()?.collectArrays(arrays)
        }
    }
    sealed?.variants?.forEach { variant -> variant.properties.forEach { it.valueModel().collectArrays(arrays) } }
    stateCodecValue()?.content?.collectArrays(arrays)
}

private fun WebValueModel.collectStateCodecValues(values: MutableList<StateCodecValue>) {
    stateCodecValue()?.let(values::addStateCodecValue)
    ref?.let { ref ->
        if (ref.kind == RefModelKind.Presenter) return@let
        ref.properties.forEach { it.valueModel().collectStateCodecValues(values) }
        ref.methods.forEach { method ->
            method.args.forEach { it.valueModel().collectStateCodecValues(values) }
            method.returnValue?.valueModel()?.collectStateCodecValues(values)
        }
    }
    sealed?.variants?.forEach { variant -> variant.properties.forEach { it.valueModel().collectStateCodecValues(values) } }
    array?.item?.collectStateCodecValues(values)
}

private fun SealedModel.referencedProperties(): List<SealedPropertyModel> =
    buildList {
        variants.forEach { variant ->
            variant.properties.forEach { property ->
                add(property)
                property.sealed?.referencedProperties()?.let(::addAll)
            }
        }
    }

private fun RefModel.methodPath(method: RefMethodModel): String = "$type.${method.name}"

private fun PropertyModel.pagingGetPath(): String = "__webPagingGet:$name"

private fun PropertyModel.pagingPeekPath(): String = "__webPagingPeek:$name"

private fun WebValueModel.stateCodecValue(): StateCodecValue? = stateCodecs.firstNotNullOfOrNull { codec -> codec.model(this) }

private fun ReturnModel?.toManifestJson(): String {
    if (this == null) return """{ "kind": "void" }"""
    return buildString {
        append("{ ")
        appendValueManifest(valueModel(), includeValueKind = true)
        append(" }")
    }
}

private fun StringBuilder.appendRefManifestDetails(ref: RefModel) {
    if (ref.kind != RefModelKind.Object) {
        append(""", "codec": ${ref.kind.manifestName.jsonString()}""")
    }
    append(""", "properties": [""")
    ref.properties.forEachIndexed { index, property ->
        append("""{ "name": ${property.name.jsonString()}, """)
        appendValueManifest(property.valueModel(), includeValueKind = false)
        append(" }")
        if (index != ref.properties.lastIndex) {
            append(", ")
        }
    }
    append("]")
    if (ref.methods.isEmpty()) return
    append(""", "methods": [""")
    ref.methods.forEachIndexed { index, method ->
        append("{ \"name\": ${method.name.jsonString()}, ")
        append("\"path\": ${ref.methodPath(method).jsonString()}, ")
        append("\"args\": [")
        method.args.forEachIndexed { argumentIndex, argument ->
            append("{ \"name\": ${argument.name.jsonString()}, ")
            appendValueManifest(argument.valueModel(), includeValueKind = true)
            append(" }")
            if (argumentIndex != method.args.lastIndex) {
                append(", ")
            }
        }
        append("], ")
        append("\"return\": ")
        append(method.returnValue.toManifestJson())
        append(" }")
        if (index != ref.methods.lastIndex) {
            append(", ")
        }
    }
    append("]")
}

private fun StringBuilder.appendValueManifest(
    value: WebValueModel,
    includeValueKind: Boolean,
) {
    value.webType?.let {
        if (includeValueKind) {
            append("\"kind\": \"value\", ")
        }
        append("\"tsType\": ${it.tsType.jsonString()}")
        appendNullable(value.nullable)
        return
    }
    value.enum?.let { enumModel ->
        append("\"kind\": \"enum\", ")
        append("\"tsType\": ${enumModel.typeName.jsonString()}, ")
        append("\"values\": [")
        enumModel.values.forEachIndexed { index, value ->
            append(value.jsonString())
            if (index != enumModel.values.lastIndex) {
                append(", ")
            }
        }
        append("]")
        appendNullable(value.nullable)
        return
    }
    value.ref?.let { ref ->
        append("\"kind\": \"ref\", \"tsType\": ${ref.typeName.jsonString()}")
        appendNullable(value.nullable)
        appendRefManifestDetails(ref)
        return
    }
    value.array?.let { array ->
        append("\"kind\": \"array\", \"item\": { ")
        appendValueManifest(array.item, includeValueKind = true)
        append(" }")
        appendNullable(value.nullable)
        return
    }
    value.stateCodecValue()?.let { stateValue ->
        stateValue.codec.appendManifest(
            builder = this,
            value = stateValue,
            nullable = value.nullable,
        )
        return
    }
    val sealedModel = requireNotNull(value.sealed)
    append("\"kind\": \"sealed\", ")
    append("\"tsType\": ${sealedModel.typeName.jsonString()}, ")
    append("\"discriminator\": ${SEALED_DISCRIMINATOR.jsonString()}, ")
    append("\"variants\": [")
    sealedModel.variants.forEachIndexed { variantIndex, variant ->
        append("{ \"name\": ${variant.typeName.jsonString()}, ")
        append("\"tag\": ${variant.tag.jsonString()}, ")
        append("\"properties\": [")
        variant.properties.forEachIndexed { propertyIndex, property ->
            append("{ \"name\": ${property.name.jsonString()}, ")
            appendValueManifest(property.valueModel(), includeValueKind = false)
            append(" }")
            if (propertyIndex != variant.properties.lastIndex) {
                append(", ")
            }
        }
        append("] }")
        if (variantIndex != sealedModel.variants.lastIndex) {
            append(", ")
        }
    }
    append("]")
    appendNullable(value.nullable)
}

private fun StringBuilder.appendNullable(nullable: Boolean) {
    if (nullable) {
        append(""", "nullable": true""")
    }
}

private fun WebType.readJsonExpression(
    objectName: String,
    argumentName: String,
    nullable: Boolean = false,
): String {
    val base = "$objectName[${argumentName.kotlinString()}]"
    return if (nullable) {
        "$base?.takeUnless { it == JsonNull }?.jsonPrimitive?.$jsonPrimitiveProperty"
    } else {
        "requireNotNull($base?.jsonPrimitive).$jsonPrimitiveProperty"
    }
}

private fun EnumModel.readJsonExpression(
    objectName: String,
    argumentName: String,
    nullable: Boolean = false,
): String {
    val base = "$objectName[${argumentName.kotlinString()}]"
    return if (nullable) {
        "$base?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.let($type::valueOf)"
    } else {
        "$type.valueOf(requireNotNull($base?.jsonPrimitive).content)"
    }
}

private fun EnumModel.writeExpression(
    valueExpression: String,
    nullable: Boolean,
): String =
    if (nullable) {
        "$valueExpression?.name"
    } else {
        "$valueExpression.name"
    }

private fun WebValueModel.readJsonExpression(
    objectName: String,
    argumentName: String,
    refsName: String,
): String {
    val base = "$objectName[${argumentName.kotlinString()}]"
    return readJsonElementExpression(base, refsName)
}

private fun WebValueModel.readJsonElementExpression(
    elementExpression: String,
    refsName: String,
): String {
    webType?.let {
        return it.readJsonElementExpression(
            elementExpression = elementExpression,
            nullable = nullable,
        )
    }
    enum?.let {
        return it.readJsonElementExpression(
            elementExpression = elementExpression,
            nullable = nullable,
        )
    }
    sealed?.let {
        return if (nullable) {
            "$elementExpression?.takeUnless { it == JsonNull }?.let { value -> ${it.decoderName()}(value, $refsName) }"
        } else {
            "${it.decoderName()}(requireNotNull($elementExpression), $refsName)"
        }
    }
    ref?.let {
        return if (nullable) {
            "$elementExpression?.takeUnless { it == JsonNull }?.let { value -> requireNotNull($refsName[webPresenterRefIndex(value)]).get() as ${it.type} }"
        } else {
            "requireNotNull($refsName[webPresenterRefIndex(requireNotNull($elementExpression))]).get() as ${it.type}"
        }
    }
    array?.let {
        val expression = "$elementExpression?.takeUnless { it == JsonNull }?.jsonArray?.map { item -> ${it.item.readJsonElementExpression(
            "item",
            refsName,
        )} }"
        return if (nullable) {
            expression
        } else {
            "requireNotNull($expression)"
        }
    }
    stateCodecValue()?.let {
        return it.codec.readJsonElementExpression(
            value = it,
            nullable = nullable,
            elementExpression = elementExpression,
            refsName = refsName,
        )
    }
    error("Unsupported web value")
}

private fun WebValueModel.writeJsonElementExpression(
    valueExpression: String,
    refsName: String,
): String =
    when {
        webType != null -> {
            if (nullable) {
                "$valueExpression?.let { JsonPrimitive(it) } ?: JsonNull"
            } else {
                "JsonPrimitive($valueExpression)"
            }
        }

        enum != null -> {
            if (nullable) {
                "$valueExpression?.name?.let { JsonPrimitive(it) } ?: JsonNull"
            } else {
                "JsonPrimitive($valueExpression.name)"
            }
        }

        sealed != null -> {
            if (nullable) {
                "$valueExpression?.let { ${sealed.encoderName()}(it, $refsName) } ?: JsonNull"
            } else {
                "${sealed.encoderName()}($valueExpression, $refsName)"
            }
        }

        ref != null -> {
            if (nullable) {
                "$valueExpression?.let { ${ref.encoderName()}(it, $refsName) } ?: JsonNull"
            } else {
                "${ref.encoderName()}($valueExpression, $refsName)"
            }
        }

        array != null -> {
            if (nullable) {
                "$valueExpression?.let { ${array.encoderName()}(it, $refsName) } ?: JsonNull"
            } else {
                "${array.encoderName()}($valueExpression, $refsName)"
            }
        }

        stateCodecValue() != null -> {
            requireNotNull(stateCodecValue()).codec.writeJsonElementExpression(
                value = requireNotNull(stateCodecValue()),
                nullable = nullable,
                valueExpression = valueExpression,
                refsName = refsName,
            )
        }

        else -> {
            error("Unsupported web value")
        }
    }

private fun SealedModel.writeJsonStringExpression(
    valueExpression: String,
    nullable: Boolean,
): String =
    if (nullable) {
        "$valueExpression?.let { ${encoderName()}(it, mutableListOf()).toString() }"
    } else {
        "${encoderName()}($valueExpression, mutableListOf()).toString()"
    }

private fun WebType.readJsonElementExpression(
    elementExpression: String,
    nullable: Boolean = false,
): String =
    if (nullable) {
        "$elementExpression?.takeUnless { it == JsonNull }?.jsonPrimitive?.$jsonPrimitiveProperty"
    } else {
        "requireNotNull($elementExpression?.jsonPrimitive).$jsonPrimitiveProperty"
    }

private fun EnumModel.readJsonElementExpression(
    elementExpression: String,
    nullable: Boolean = false,
): String =
    if (nullable) {
        "$elementExpression?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.let($type::valueOf)"
    } else {
        "$type.valueOf(requireNotNull($elementExpression?.jsonPrimitive).content)"
    }

private fun RefModel.encoderName(): String = "encode${type.toKotlinIdentifierSuffix()}WebRef"

private fun SealedModel.encoderName(): String = "encode${type.toKotlinIdentifierSuffix()}WebSealed"

private fun SealedModel.decoderName(): String = "decode${type.toKotlinIdentifierSuffix()}WebSealed"

private fun ArrayModel.encoderName(): String = "encode${type.toKotlinIdentifierSuffix()}WebArray"

private fun WebValueModel.kotlinType(): String =
    when {
        webType != null -> webType.kotlinType
        enum != null -> enum.type
        sealed != null -> sealed.type
        ref != null -> ref.type
        array != null -> array.type
        stateCodecValue() != null -> requireNotNull(stateCodecValue()).type
        else -> "Any"
    } + if (nullable) "?" else ""

private class WebTypeAnalyzer(
    private val policy: BridgePolicy = BridgePolicy,
) : RefCodecContext {
    fun value(
        type: KSType,
        visited: Set<String> = emptySet(),
        allowRef: Boolean = true,
    ): WebValueModel? {
        policy.primitive(type)?.let {
            return WebValueModel(webType = it, nullable = type.isMarkedNullable)
        }
        enum(type)?.let {
            return WebValueModel(enum = it, nullable = type.isMarkedNullable)
        }
        stateCodecs
            .firstNotNullOfOrNull { codec ->
                codec.parse(type, visited) { nestedType, nestedVisited ->
                    value(nestedType, visited = nestedVisited, allowRef = true)
                }
            }?.let {
                return it
            }
        array(type, visited)?.let {
            return WebValueModel(array = it, nullable = type.isMarkedNullable)
        }
        sealed(type, visited)?.let {
            return WebValueModel(sealed = it, nullable = type.isMarkedNullable)
        }
        if (allowRef) {
            ref(type, visited)?.let {
                return WebValueModel(ref = it, nullable = type.isMarkedNullable)
            }
        }
        return null
    }

    fun returnValue(
        type: KSType,
        visited: Set<String> = emptySet(),
    ): ReturnModel? {
        if (type.isUnit()) return null
        val value = value(type, visited = visited, allowRef = true) ?: return null
        return ReturnModel(
            webType = value.webType,
            enum = value.enum,
            sealed = value.sealed,
            ref = value.ref,
            array = value.array,
            uiState = value.uiState,
            pagingState = value.pagingState,
            nullable = type.isMarkedNullable,
        )
    }

    fun callback(type: KSType): CallbackModel? {
        if (!policy.isFunctionType(type)) return null
        val resolvedArguments = type.arguments.map { it.type?.resolve() ?: return null }
        if (resolvedArguments.isEmpty()) return null
        val returnType = resolvedArguments.last()
        if (returnType.declaration.qualifiedName?.asString() != "kotlin.Unit") return null
        val callbackArgs =
            resolvedArguments
                .dropLast(1)
                .mapIndexed { index, argumentType ->
                    val value = value(argumentType, allowRef = true) ?: return null
                    CallbackArgumentModel(
                        name = if (resolvedArguments.size == 2) "value" else "value$index",
                        webType = value.webType,
                        enum = value.enum,
                        sealed = value.sealed,
                        array = value.array,
                        uiState = value.uiState,
                        pagingState = value.pagingState,
                        nullable = argumentType.isMarkedNullable,
                    )
                }
        return CallbackModel(callbackArgs)
    }

    private fun array(
        type: KSType,
        visited: Set<String>,
    ): ArrayModel? {
        if (!policy.isArrayLike(type)) return null
        val itemType =
            type.arguments
                .firstOrNull()
                ?.type
                ?.resolve() ?: return null
        val item = value(itemType, visited = visited, allowRef = true) ?: return null
        return ArrayModel(
            item = item,
            type = "kotlin.collections.Iterable<${item.kotlinType()}>",
        )
    }

    private fun enum(type: KSType): EnumModel? {
        val declaration = type.declaration as? KSClassDeclaration ?: return null
        if (declaration.classKind != ClassKind.ENUM_CLASS) return null
        return EnumModel(
            type = declaration.qualifiedName?.asString() ?: return null,
            typeName = declaration.simpleName.asString(),
            values =
                declaration.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.classKind == ClassKind.ENUM_ENTRY }
                    .map { it.simpleName.asString() }
                    .toList(),
        )
    }

    private fun sealed(
        type: KSType,
        visited: Set<String>,
    ): SealedModel? {
        val declaration = type.declaration as? KSClassDeclaration ?: return null
        if (!declaration.modifiers.contains(Modifier.SEALED)) return null
        val qualifiedName = declaration.qualifiedName?.asString() ?: return null
        if (qualifiedName in visited) {
            return SealedModel(
                type = qualifiedName,
                typeName = declaration.simpleName.asString(),
                variants = emptyList(),
            )
        }
        val leafSubclasses = declaration.leafSealedSubclasses().toList()
        val variants =
            leafSubclasses
                .asSequence()
                .mapNotNull { variant ->
                    sealedVariant(variant, visited + qualifiedName)
                }.sortedBy { it.tag }
                .toList()
        if (variants.isEmpty() || variants.size != leafSubclasses.size) return null
        return SealedModel(
            type = qualifiedName,
            typeName = declaration.simpleName.asString(),
            variants = variants,
        )
    }

    private fun sealedVariant(
        declaration: KSClassDeclaration,
        visited: Set<String>,
    ): SealedVariantModel? {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return null
        if (declaration.classKind != ClassKind.OBJECT && declaration.primaryConstructor?.isBridgeVisible() == false) return null
        val properties =
            declaration
                .primaryConstructor
                ?.parameters
                .orEmpty()
                .filter { it.isVal || it.isVar }
                .mapNotNull { parameter ->
                    val parameterName = parameter.name?.asString() ?: return null
                    if (parameterName == SEALED_DISCRIMINATOR) return@mapNotNull null
                    val propertyDeclaration =
                        declaration.getAllProperties().firstOrNull { it.simpleName.asString() == parameterName }
                    if (propertyDeclaration?.isBridgeVisible() == false) return@mapNotNull null
                    val parameterType = parameter.type.resolve()
                    val value = value(parameterType, visited = visited, allowRef = true) ?: return@mapNotNull null
                    SealedPropertyModel(
                        name = parameterName,
                        webType = value.webType,
                        enum = value.enum,
                        sealed = value.sealed,
                        ref = value.ref,
                        array = value.array,
                        uiState = value.uiState,
                        pagingState = value.pagingState,
                        nullable = parameterType.isMarkedNullable,
                    )
                }.sortedBy { it.name }
        return SealedVariantModel(
            type = qualifiedName,
            typeName = declaration.simpleName.asString(),
            tag = declaration.simpleName.asString(),
            objectLike = declaration.classKind == ClassKind.OBJECT,
            properties = properties,
        )
    }

    private fun ref(
        type: KSType,
        visited: Set<String>,
    ): RefModel? {
        val declaration = type.declaration as? KSClassDeclaration ?: return null
        return refCodecs.firstNotNullOfOrNull { codec ->
            codec.parse(
                type = type,
                declaration = declaration,
                visited = visited,
                context = this,
            )
        }
    }

    override fun opaqueRef(
        type: KSType,
        declaration: KSClassDeclaration,
        kind: RefModelKind,
    ): RefModel =
        RefModel(
            type = type.kotlinTypeName(includeNullable = false),
            typeName = declaration.simpleName.asString(),
            properties = emptyList(),
            methods = emptyList(),
            kind = kind,
        )

    override fun objectRef(
        type: KSType,
        declaration: KSClassDeclaration,
        visited: Set<String>,
    ): RefModel? {
        val qualifiedName = declaration.qualifiedName?.asString() ?: return null
        if (qualifiedName in visited) {
            return RefModel(
                type = type.kotlinTypeName(includeNullable = false),
                typeName = declaration.simpleName.asString(),
                properties = emptyList(),
                methods = emptyList(),
            )
        }
        val nestedVisited = visited + qualifiedName
        val properties = refProperties(declaration, nestedVisited)
        val methods = refMethods(declaration, nestedVisited)
        if (properties.isEmpty() && methods.isEmpty()) return null

        return RefModel(
            type = type.kotlinTypeName(includeNullable = false),
            typeName = declaration.simpleName.asString(),
            properties = properties,
            methods = methods,
        )
    }

    private fun refProperties(
        declaration: KSClassDeclaration,
        visited: Set<String>,
    ): List<RefPropertyModel> =
        declaration
            .declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .filter { policy.isVisibleProperty(it) }
            .mapNotNull { property ->
                val propertyType = property.type.resolve()
                val value = value(propertyType, visited = visited, allowRef = true) ?: return@mapNotNull null
                RefPropertyModel(
                    name = property.simpleName.asString(),
                    webType = value.webType,
                    enum = value.enum,
                    sealed = value.sealed,
                    ref = value.ref,
                    array = value.array,
                    uiState = value.uiState,
                    pagingState = value.pagingState,
                    nullable = propertyType.isMarkedNullable,
                )
            }.sortedBy { it.name }
            .toList()

    private fun refMethods(
        declaration: KSClassDeclaration,
        visited: Set<String>,
    ): List<RefMethodModel> =
        declaration
            .declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { policy.isVisibleMethod(it) }
            .groupBy { it.simpleName.asString() }
            .filter { (_, overloads) -> overloads.size == 1 }
            .values
            .asSequence()
            .map { it.single() }
            .mapNotNull { function -> refMethod(function, visited) }
            .sortedBy { it.name }
            .toList()

    private fun refMethod(
        function: KSFunctionDeclaration,
        visited: Set<String>,
    ): RefMethodModel? {
        if (function.typeParameters.isNotEmpty()) return null
        if (Modifier.SUSPEND in function.modifiers) return null
        if (function.parameters.any { it.isVararg }) return null
        if (function.parameters.any { hasUnsupportedGeneric(it.type.resolve()) }) return null
        if (function.returnType?.resolve()?.let(::hasUnsupportedGeneric) == true) return null
        val resolvedReturnType = function.returnType?.resolve()
        val returnDeclaration = resolvedReturnType?.declaration as? KSClassDeclaration
        if (
            returnDeclaration?.modifiers?.contains(Modifier.SEALED) == true &&
            returnDeclaration.qualifiedName?.asString() in visited
        ) {
            return null
        }
        val returnValue = resolvedReturnType?.let { returnValue(it, visited) }
        if (returnValue == null && !function.returnsUnit()) return null
        val args =
            function.parameters.map { parameter ->
                val parameterName = parameter.name?.asString() ?: return null
                val parameterType = parameter.type.resolve()
                val value = value(parameterType, visited = visited, allowRef = true)
                val callback = callback(parameterType)
                if (value == null && callback == null) return null
                ArgumentModel(
                    name = parameterName,
                    webType = value?.webType,
                    enum = value?.enum,
                    sealed = value?.sealed,
                    ref = value?.ref,
                    callback = callback,
                    array = value?.array,
                    uiState = value?.uiState,
                    pagingState = value?.pagingState,
                    nullable = parameterType.isMarkedNullable,
                )
            }
        if (args.any { it.callback != null }) return null
        return RefMethodModel(
            name = function.simpleName.asString(),
            args = args,
            returnValue = returnValue,
        )
    }

    private fun hasUnsupportedGeneric(type: KSType): Boolean {
        if (type.arguments.isEmpty()) return false
        val qualifiedName = type.declaration.qualifiedName?.asString()
        if (qualifiedName in supportedArrayTypes || qualifiedName == UI_STATE || qualifiedName == PAGING_STATE) {
            return type.arguments.any { argument -> argument.type?.resolve()?.let(::hasUnsupportedGeneric) == true }
        }
        val declaration = type.declaration as? KSClassDeclaration
        return declaration?.typeParameters?.isNotEmpty() == true ||
            type.arguments.any { argument -> argument.type?.resolve()?.let(::hasUnsupportedGeneric) == true }
    }

    private fun KSClassDeclaration.leafSealedSubclasses(): Sequence<KSClassDeclaration> =
        getSealedSubclasses().flatMap { subclass ->
            if (subclass.modifiers.contains(Modifier.SEALED)) {
                subclass.leafSealedSubclasses()
            } else {
                sequenceOf(subclass)
            }
        }
}

private object BridgePolicy {
    fun primitive(type: KSType): WebType? =
        when (type.declaration.qualifiedName?.asString()) {
            "kotlin.Boolean" -> WebType.Boolean
            "kotlin.Byte" -> WebType.Int
            "kotlin.Double" -> WebType.Double
            "kotlin.Float" -> WebType.Float
            "kotlin.Int" -> WebType.Int
            "kotlin.Long" -> WebType.Long
            "kotlin.Short" -> WebType.Int
            "kotlin.String" -> WebType.String
            "dev.dimension.flare.ui.render.PlatformText" -> WebType.String
            else -> null
        }

    fun isArrayLike(type: KSType): Boolean = type.declaration.qualifiedName?.asString() in supportedArrayTypes

    fun isFunctionType(type: KSType): Boolean =
        type.declaration.qualifiedName
            ?.asString()
            ?.startsWith("kotlin.Function") == true

    fun canBridgeAsRef(declaration: KSClassDeclaration): Boolean {
        if (declaration.classKind == ClassKind.ENUM_CLASS) return false
        val qualifiedName = declaration.qualifiedName?.asString() ?: return false
        return platformReferencePackagePrefixes.none { qualifiedName.startsWith(it) }
    }

    fun isVisibleProperty(property: KSPropertyDeclaration): Boolean =
        !property.hasAnnotation(WEB_IGNORE_ANNOTATION) &&
            property.isBridgeVisible()

    fun isVisibleMethod(function: KSFunctionDeclaration): Boolean =
        !function.hasAnnotation(WEB_IGNORE_ANNOTATION) &&
            !function.hasAnnotation(COMPOSABLE_ANNOTATION) &&
            function.isBridgeVisible() &&
            !function.simpleName.asString().isIgnoredFunctionName()
}

private fun KSType.kotlinTypeName(includeNullable: Boolean = true): String {
    val name = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
    val typeArguments =
        arguments
            .map { argument -> argument.type?.resolve()?.kotlinTypeName() ?: "*" }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "<", postfix = ">")
            .orEmpty()
    return "$name$typeArguments${if (includeNullable && isMarkedNullable) "?" else ""}"
}

private fun PresenterModel.callbackInterfaceName(parameter: ArgumentModel): String =
    "${specName.removeSuffix("WebSpec")}${parameter.name.replaceFirstChar(Char::uppercaseChar)}Callback"

private fun ArgumentModel.callbackVariableName(): String = "${name}Callback"

private fun CallbackModel.kotlinParameterDeclarations(): String =
    args.joinToString(", ") { argument ->
        val kotlinType =
            when {
                argument.webType != null -> argument.webType.kotlinType
                argument.enum != null || argument.sealed != null -> "String"
                else -> error("Unsupported callback argument: ${argument.name}")
            }
        "${argument.name}: $kotlinType${if (argument.nullable) "?" else ""}"
    }

private fun CallbackModel.kotlinParameterList(): String = args.joinToString(", ") { it.name }

private fun CallbackModel.kotlinArgumentList(): String =
    args.joinToString(", ") { argument ->
        if (argument.enum == null) {
            argument.sealed?.let { sealed ->
                return@joinToString sealed.writeJsonStringExpression(
                    valueExpression = argument.name,
                    nullable = argument.nullable,
                )
            }
            argument.name
        } else if (argument.nullable) {
            "${argument.name}?.name"
        } else {
            "${argument.name}.name"
        }
    }

private fun KSType.isUnit(): Boolean =
    declaration
        .qualifiedName
        ?.asString() == "kotlin.Unit"

private fun KSFunctionDeclaration.returnsUnit(): Boolean =
    returnType
        ?.resolve()
        ?.isUnit() == true

private fun KSDeclaration.hasAnnotation(shortName: String): Boolean = annotations.any { it.shortName.asString() == shortName }

private fun KSDeclaration.isBridgeVisible(): Boolean =
    Modifier.PRIVATE !in modifiers &&
        Modifier.PROTECTED !in modifiers &&
        Modifier.INTERNAL !in modifiers

private fun KSClassDeclaration.webStateTypeName(presenterDeclaration: KSClassDeclaration): String {
    val parent = parentDeclaration as? KSClassDeclaration
    return if (parent?.qualifiedName?.asString() == presenterDeclaration.qualifiedName?.asString()) {
        "${presenterDeclaration.simpleName.asString()}${simpleName.asString()}"
    } else {
        simpleName.asString()
    }
}

private fun String.isIgnoredFunctionName(): Boolean = this in ignoredFunctionNames || matches(Regex("component\\d+"))

private fun String.toFactoryName(): String = "create${replaceFirstChar(Char::uppercaseChar)}Presenter"

private fun String.toBindFactoryName(): String = "bind${replaceFirstChar(Char::uppercaseChar)}Presenter"

private fun String.toKotlinIdentifierSuffix(): String =
    split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase()
                } else {
                    char.toString()
                }
            }
        }

private fun String.kotlinString(): String =
    buildString {
        append('"')
        this@kotlinString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private fun String.jsonString(): String = kotlinString()

private const val WEB_PRESENTER_SHORT_NAME = "WebPresenter"
private const val WEB_PRESENTER_ANNOTATION = "dev.dimension.flare.web.shared.WebPresenter"
private const val WEB_IGNORE_ANNOTATION = "WebIgnore"
private const val COMPOSABLE_ANNOTATION = "Composable"
private const val PRESENTER_BASE = "dev.dimension.flare.ui.presenter.PresenterBase"
private const val UI_STATE = "dev.dimension.flare.ui.model.UiState"
private const val PAGING_STATE = "dev.dimension.flare.common.PagingState"
private const val GENERATED_PACKAGE = "dev.dimension.flare.web.shared.generated"
private const val RECEIVER_REF_ARGUMENT = "__receiver"
private const val SEALED_DISCRIMINATOR = "type"
private val SEALED_DISCRIMINATOR_JSON = SEALED_DISCRIMINATOR.kotlinString()

private val ignoredFunctionNames = setOf("<init>", "copy", "equals", "hashCode", "toString")
private val platformReferencePackagePrefixes =
    listOf(
        "android.",
        "androidx.compose.",
        "androidx.paging.",
        "java.",
        "javax.",
        "kotlin.",
        "kotlinx.collections.",
        "kotlinx.coroutines.",
        "kotlinx.serialization.",
        "org.koin.",
    )
private val supportedArrayTypes =
    setOf(
        "kotlin.Array",
        "kotlin.collections.Collection",
        "kotlin.collections.Iterable",
        "kotlin.collections.List",
        "kotlin.collections.MutableList",
        "dev.dimension.flare.common.SerializableImmutableList",
        "kotlinx.collections.immutable.ImmutableList",
        "kotlinx.collections.immutable.PersistentList",
    )
