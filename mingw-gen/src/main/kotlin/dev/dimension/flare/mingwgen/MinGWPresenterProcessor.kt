package dev.dimension.flare.mingwgen

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import dev.dimension.flare.mingwgen.annotation.MinGWPresenter
import kotlinx.coroutines.CoroutineScope

class MinGWPresenterProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(MinGWPresenter::class.qualifiedName!!)
        symbols.filterIsInstance<KSClassDeclaration>().forEach {
            val className = "${it.simpleName.asString()}MinGWPresenter"
            val stateType = it.getAllSuperTypes().first().arguments.first().type!!.resolve()
            val stableRefClassName =
                ClassName("kotlinx.cinterop", "StableRef").parameterizedBy(stateType.toTypeName())
            val callbackType = ClassName("kotlinx.cinterop", "CPointer")
                .parameterizedBy(
                    ClassName("kotlinx.cinterop", "CFunction").parameterizedBy(
                        LambdaTypeName.get(
                            parameters = arrayOf(
                                ClassName("kotlinx.cinterop", "COpaquePointer")
                            ),
                            returnType = Unit::class.asClassName()
                        )
                    )
                )
            val autoCloseable = ClassName("kotlin", "AutoCloseable")
            val ctor = it.primaryConstructor?.let {
                FunSpec.constructorBuilder().apply {
                    it.parameters.forEach { param ->
                        if (param.type.resolve().isFunctionType) {
                            addParameter(
                                param.name!!.asString(),
                                ClassName("kotlinx.cinterop", "CPointer")
                                    .parameterizedBy(
                                        ClassName("kotlinx.cinterop", "CFunction").parameterizedBy(
                                            LambdaTypeName.get(
                                                parameters = param.type.resolve().arguments
                                                    .map {
                                                        it.type!!.resolve().toTypeName()
                                                    }
                                                    .filter { it != Unit::class.asClassName() }
                                                    .toTypedArray(),
                                                returnType = Unit::class.asClassName()
                                            )
                                        )
                                    )
                            )
                        } else {
                            addParameter(param.name!!.asString(), param.type.resolve().toTypeName())
                        }
                    }
                }.build()
            }
            FileSpec.builder(
                packageName = it.packageName.asString(),
                fileName = "${className}.kt",
            ).apply {
                addImport("kotlinx.coroutines", "Dispatchers")
                addImport("kotlinx.coroutines", "launch")
                addImport("kotlinx.cinterop", "asStableRef")
                addImport("kotlinx.cinterop", "invoke")
                addType(
                    TypeSpec.classBuilder(className)
                        .primaryConstructor(ctor)
                        .addSuperinterface(autoCloseable)
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                                .addMember(
                                    "%T::class",
                                    ClassName("kotlin", "ExperimentalStdlibApi")
                                )
                                .addMember(
                                    "%T::class",
                                    ClassName("kotlinx.cinterop", "ExperimentalForeignApi")
                                )
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder("scope", CoroutineScope::class, KModifier.PRIVATE)
                                .initializer("CoroutineScope(Dispatchers.Main)")
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "stableRef",
                                stableRefClassName.copy(nullable = true),
                                KModifier.PRIVATE
                            )
                                .initializer("null")
                                .mutable()
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "job",
                                ClassName("kotlinx.coroutines", "Job").copy(nullable = true),
                                KModifier.PRIVATE
                            )
                                .initializer("null")
                                .mutable()
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "presenter",
                                it.toClassName(),
                                KModifier.PRIVATE
                            )
                                .initializer(
                                    "%T(%L)",
                                    it.toClassName(),
                                    it.primaryConstructor!!.parameters.joinToString(", ") { param ->
                                        if (param.type.resolve().isFunctionType) {
                                            param.name!!.asString() + "::invoke"
                                        } else {
                                            param.name!!.asString()
                                        }
                                    }
                                )
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("connect")
                                .addParameter("callback", callbackType)
                                .addCode(
                                    CodeBlock.of(
                                    """
                                    job = scope.launch {
                                        presenter.models.collect {
                                            stableRef?.dispose()
                                            stableRef = StableRef.create(it).apply {
                                                callback.invoke(asCPointer())
                                            }
                                        }
                                    }
                                    """.trimIndent()
                                    )
                                )
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("unwrap")
                                .addParameter(
                                    "ptr",
                                    ClassName("kotlinx.cinterop", "COpaquePointer")
                                )
                                .returns(stateType.toTypeName())
                                .addStatement(
                                    "return ptr.asStableRef<%T>().get()",
                                    stateType.toTypeName()
                                )
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("close")
                                .addModifiers(KModifier.OVERRIDE)
                                .addStatement("stableRef?.dispose()")
                                .addStatement("job?.cancel()")
                                .build()
                        )
                        .build()
                )
            }.build().writeTo(codeGenerator, Dependencies(false, it.containingFile!!))
        }
        return emptyList()
    }
}


