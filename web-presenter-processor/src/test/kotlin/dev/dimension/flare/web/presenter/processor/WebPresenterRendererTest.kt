package dev.dimension.flare.web.presenter.processor

import kotlin.test.Test
import kotlin.test.assertTrue

class WebPresenterRendererTest {
    @Test
    fun rendersConstructorValueAndCallbackParameters() {
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(counterPresenter()))
        val manifest = WebPresenterRenderer.renderManifest(listOf(counterPresenter()))

        kotlin.assertContains("@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)")
        kotlin.assertContains(
            """@file:Suppress("DEPRECATION", "NATIVE_INVOKE", "REDUNDANT_ELSE_IN_WHEN", "UNCHECKED_CAST", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")""",
        )
        kotlin.assertContains("import kotlin.js.JsAny")
        kotlin.assertContains("import kotlin.js.nativeInvoke")
        kotlin.assertContains("private external interface CounterPresenterAlertCallback : JsAny {")
        kotlin.assertContains("    @nativeInvoke")
        kotlin.assertContains("    operator fun invoke(value: Int)")
        kotlin.assertContains("override fun create(")
        kotlin.assertContains("callbacks: JsArray<JsAny>,")
        kotlin.assertContains("""initialValue = requireNotNull(createArgs["initialValue"]?.jsonPrimitive).int,""")
        kotlin.assertContains("val alertCallback =")
        kotlin.assertContains("requireNotNull(callbacks[0]) as CounterPresenterAlertCallback")
        kotlin.assertContains("alert = { value ->")
        kotlin.assertContains("alertCallback(value)")

        manifest.assertContains("""{ "name": "initialValue", "kind": "value", "tsType": "number" }""")
        manifest.assertContains(
            """{ "name": "alert", "kind": "callback", "args": [{ "name": "value", "tsType": "number" }] }""",
        )
    }

    @Test
    fun rendersStateEncodingAndPrimitiveActionArguments() {
        val presenter =
            counterPresenter(
                actions =
                    listOf(
                        ActionModel(
                            name = "setLabel",
                            args =
                                listOf(
                                    ArgumentModel(
                                        name = "label",
                                        webType = WebType.String,
                                        ref = null,
                                        callback = null,
                                    ),
                                ),
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("""put("count", JsonPrimitive(state.count))""")
        kotlin.assertContains("val action = WebPresenterJson.parseToJsonElement(actionJson).jsonObject")
        kotlin.assertContains("val args = action[\"args\"]?.jsonObject")
        kotlin.assertContains(""""setLabel" -> state.setLabel(""")
        kotlin.assertContains("""label = requireNotNull(requireNotNull(args)["label"]?.jsonPrimitive).content,""")

        manifest.assertContains(
            """{ "name": "label", "kind": "value", "tsType": "string" }""",
        )
    }

    @Test
    fun rendersStateReferencesAndReferenceActionArguments() {
        val sampleRef =
            RefModel(
                type = "dev.dimension.flare.web.shared.Sample",
                typeName = "Sample",
                properties =
                    listOf(
                        RefPropertyModel(
                            name = "index",
                            webType = WebType.Int,
                        ),
                        RefPropertyModel(
                            name = "text",
                            webType = WebType.String,
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "count",
                            webType = WebType.Int,
                            ref = null,
                        ),
                        PropertyModel(
                            name = "sample",
                            webType = null,
                            ref = sampleRef,
                        ),
                    ),
                actions =
                    listOf(
                        ActionModel(
                            name = "showSample",
                            args =
                                listOf(
                                    ArgumentModel(
                                        name = "sample",
                                        webType = null,
                                        ref = sampleRef,
                                        callback = null,
                                    ),
                                ),
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("val refs = mutableListOf<JsReference<Any>>()")
        kotlin.assertContains("""put("sample", encodeDevDimensionFlareWebSharedSampleWebRef(state.sample, refs))""")
        kotlin.assertContains("""put("__webPresenterRef", refs.size)""")
        kotlin.assertContains("refs += (value as Any).toJsReference()")
        kotlin.assertContains("""put("index", JsonPrimitive(value.index))""")
        kotlin.assertContains("""put("text", JsonPrimitive(value.text))""")
        kotlin.assertContains("refs: JsArray<JsReference<Any>>,")
        kotlin.assertContains("sample =")
        kotlin.assertContains("requireNotNull(refs[webPresenterRefIndex(requireNotNull(requireNotNull(args)[\"sample\"]))])")
        kotlin.assertContains(".get() as dev.dimension.flare.web.shared.Sample,")

        manifest.assertContains(
            """{ "name": "sample", "kind": "ref", "tsType": "Sample", "properties": [{ "name": "index", "tsType": "number" }, { "name": "text", "tsType": "string" }] }""",
        )
        manifest.assertContains(
            """{ "name": "sample", "kind": "ref", "tsType": "Sample", "properties":""",
        )
    }

    @Test
    fun rendersReturnValueCallsAndReferenceMethods() {
        val sampleRef =
            RefModel(
                type = "dev.dimension.flare.web.shared.Sample",
                typeName = "Sample",
                properties =
                    listOf(
                        RefPropertyModel(
                            name = "index",
                            webType = WebType.Int,
                        ),
                        RefPropertyModel(
                            name = "text",
                            webType = WebType.String,
                        ),
                    ),
                methods =
                    listOf(
                        RefMethodModel(
                            name = "export",
                            args = emptyList(),
                            returnValue =
                                ReturnModel(
                                    webType = null,
                                    ref =
                                        RefModel(
                                            type = "dev.dimension.flare.web.shared.Sample",
                                            typeName = "Sample",
                                            properties =
                                                listOf(
                                                    RefPropertyModel(
                                                        name = "index",
                                                        webType = WebType.Int,
                                                    ),
                                                    RefPropertyModel(
                                                        name = "text",
                                                        webType = WebType.String,
                                                    ),
                                                ),
                                        ),
                                ),
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "sample",
                            webType = null,
                            ref = sampleRef,
                        ),
                    ),
                actions =
                    listOf(
                        ActionModel(
                            name = "exportSample",
                            args = emptyList(),
                            returnValue =
                                ReturnModel(
                                    webType = null,
                                    ref = sampleRef,
                                ),
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("override fun call(")
        kotlin.assertContains(""""exportSample" -> {""")
        kotlin.assertContains("val result = state.exportSample(")
        kotlin.assertContains("""put("value", encodeDevDimensionFlareWebSharedSampleWebRef(result, resultRefs))""")
        kotlin.assertContains("refs += (value as Any).toJsReference()")
        kotlin.assertContains(""""dev.dimension.flare.web.shared.Sample.export" -> {""")
        kotlin.assertContains("val receiver =")
        kotlin.assertContains("val result = receiver.export(")

        manifest.assertContains(
            """"return": { "kind": "ref", "tsType": "Sample", "properties":""",
        )
        manifest.assertContains(
            """"methods": [{ "name": "export", "path": "dev.dimension.flare.web.shared.Sample.export"""",
        )
    }

    @Test
    fun rendersReferenceModelsFromArbitraryPackages() {
        val profileRef =
            RefModel(
                type = "com.example.shared.Profile",
                typeName = "Profile",
                properties =
                    listOf(
                        RefPropertyModel(
                            name = "avatar",
                            webType = WebType.String,
                            nullable = true,
                        ),
                        RefPropertyModel(
                            name = "name",
                            webType = WebType.String,
                        ),
                    ),
                methods =
                    listOf(
                        RefMethodModel(
                            name = "displayName",
                            args = emptyList(),
                            returnValue =
                                ReturnModel(
                                    webType = WebType.String,
                                    ref = null,
                                ),
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "profile",
                            webType = null,
                            ref = profileRef,
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("""put("profile", encodeComExampleSharedProfileWebRef(state.profile, refs))""")
        kotlin.assertContains("private fun encodeComExampleSharedProfileWebRef(")
        kotlin.assertContains("value: com.example.shared.Profile,")
        kotlin.assertContains("""put("avatar", value.avatar?.let { JsonPrimitive(it) } ?: JsonNull)""")
        kotlin.assertContains("""put("name", JsonPrimitive(value.name))""")
        kotlin.assertContains(""""com.example.shared.Profile.displayName" -> {""")
        kotlin.assertContains("val result = receiver.displayName(")
        kotlin.assertContains("""put("value", JsonPrimitive(result))""")

        manifest.assertContains(
            """{ "name": "profile", "kind": "ref", "tsType": "Profile", "properties": [{ "name": "avatar", "tsType": "string", "nullable": true }, { "name": "name", "tsType": "string" }], "methods": [{ "name": "displayName", "path": "com.example.shared.Profile.displayName", "args": [], "return": { "kind": "value", "tsType": "string" } }] }""",
        )
    }

    @Test
    fun rendersOpaquePresenterReferences() {
        val timelinePresenterRef =
            RefModel(
                type = "dev.dimension.flare.ui.presenter.home.TimelinePresenter",
                typeName = "TimelinePresenter",
                properties = emptyList(),
                kind = RefModelKind.Presenter,
            )
        val tabRef =
            RefModel(
                type = "com.example.shared.SampleTab",
                typeName = "SampleTab",
                properties = emptyList(),
                methods =
                    listOf(
                        RefMethodModel(
                            name = "openTimeline",
                            args = emptyList(),
                            returnValue =
                                ReturnModel(
                                    webType = null,
                                    ref = timelinePresenterRef,
                                ),
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "tab",
                            webType = null,
                            ref = tabRef,
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("private fun encodeDevDimensionFlareUiPresenterHomeTimelinePresenterWebRef(")
        kotlin.assertContains("""put("value", encodeDevDimensionFlareUiPresenterHomeTimelinePresenterWebRef(result, resultRefs))""")
        kotlin.assertContains(""""com.example.shared.SampleTab.openTimeline" -> {""")
        assertTrue(!kotlin.contains("org.koin"), kotlin)

        manifest.assertContains(
            """"return": { "kind": "ref", "tsType": "TimelinePresenter", "codec": "presenter", "properties": [] }""",
        )
    }

    @Test
    fun rendersNullableValuesAndReferences() {
        val sampleRef =
            RefModel(
                type = "dev.dimension.flare.web.shared.Sample",
                typeName = "Sample",
                properties =
                    listOf(
                        RefPropertyModel(
                            name = "index",
                            webType = WebType.Int,
                        ),
                        RefPropertyModel(
                            name = "note",
                            webType = WebType.String,
                            nullable = true,
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "optionalCount",
                            webType = WebType.Int,
                            ref = null,
                            nullable = true,
                        ),
                        PropertyModel(
                            name = "optionalSample",
                            webType = null,
                            ref = sampleRef,
                            nullable = true,
                        ),
                    ),
                actions =
                    listOf(
                        ActionModel(
                            name = "showOptionalLabel",
                            args =
                                listOf(
                                    ArgumentModel(
                                        name = "label",
                                        webType = WebType.String,
                                        ref = null,
                                        callback = null,
                                        nullable = true,
                                    ),
                                ),
                        ),
                        ActionModel(
                            name = "showOptionalSample",
                            args =
                                listOf(
                                    ArgumentModel(
                                        name = "sample",
                                        webType = null,
                                        ref = sampleRef,
                                        callback = null,
                                        nullable = true,
                                    ),
                                ),
                        ),
                        ActionModel(
                            name = "exportOptionalSample",
                            args = emptyList(),
                            returnValue =
                                ReturnModel(
                                    webType = null,
                                    ref = sampleRef,
                                    nullable = true,
                                ),
                        ),
                    ),
                parameters =
                    listOf(
                        ArgumentModel(
                            name = "alert",
                            webType = null,
                            ref = null,
                            callback =
                                CallbackModel(
                                    args =
                                        listOf(
                                            CallbackArgumentModel(
                                                name = "value",
                                                webType = WebType.Int,
                                                nullable = true,
                                            ),
                                        ),
                                ),
                            nullable = true,
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("import kotlinx.serialization.json.JsonNull")
        kotlin.assertContains("operator fun invoke(value: Int?)")
        kotlin.assertContains("callbacks[0] as CounterPresenterAlertCallback?")
        kotlin.assertContains("alert = alertCallback?.let { callback ->")
        kotlin.assertContains("callback(value)")
        kotlin.assertContains("""put("optionalCount", state.optionalCount?.let { JsonPrimitive(it) } ?: JsonNull)""")
        kotlin.assertContains(
            """put("optionalSample", state.optionalSample?.let { encodeDevDimensionFlareWebSharedSampleWebRef(it, refs) } ?: JsonNull)""",
        )
        kotlin.assertContains("""put("note", value.note?.let { JsonPrimitive(it) } ?: JsonNull)""")
        kotlin.assertContains(
            """label = requireNotNull(args)["label"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,""",
        )
        kotlin.assertContains(
            """requireNotNull(args)["sample"]?.takeUnless { it == JsonNull }?.let { value -> requireNotNull(refs[webPresenterRefIndex(value)]).get() as dev.dimension.flare.web.shared.Sample },""",
        )
        kotlin.assertContains(
            """put("value", result?.let { encodeDevDimensionFlareWebSharedSampleWebRef(it, resultRefs) } ?: JsonNull)""",
        )

        manifest.assertContains("""{ "name": "optionalCount", "kind": "value", "tsType": "number", "nullable": true }""")
        manifest.assertContains(
            """{ "name": "alert", "kind": "callback", "args": [{ "name": "value", "tsType": "number", "nullable": true }], "nullable": true }""",
        )
        manifest.assertContains(
            """{ "name": "optionalSample", "kind": "ref", "tsType": "Sample", "nullable": true""",
        )
        manifest.assertContains("""{ "name": "note", "tsType": "string", "nullable": true }""")
        manifest.assertContains("""{ "name": "label", "kind": "value", "tsType": "string", "nullable": true }""")
        manifest.assertContains("""{ "name": "sample", "kind": "ref", "tsType": "Sample", "nullable": true, "properties":""")
        manifest.assertContains(""""return": { "kind": "ref", "tsType": "Sample", "nullable": true""")
    }

    @Test
    fun rendersEnumsAsJsonStrings() {
        val counterMode =
            EnumModel(
                type = "dev.dimension.flare.web.shared.CounterMode",
                typeName = "CounterMode",
                values = listOf("Even", "Odd"),
            )
        val sampleRef =
            RefModel(
                type = "dev.dimension.flare.web.shared.Sample",
                typeName = "Sample",
                properties =
                    listOf(
                        RefPropertyModel(
                            name = "mode",
                            webType = null,
                            enum = counterMode,
                            nullable = true,
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "mode",
                            webType = null,
                            enum = counterMode,
                            ref = null,
                        ),
                        PropertyModel(
                            name = "optionalMode",
                            webType = null,
                            enum = counterMode,
                            ref = null,
                            nullable = true,
                        ),
                        PropertyModel(
                            name = "sample",
                            webType = null,
                            ref = sampleRef,
                        ),
                    ),
                actions =
                    listOf(
                        ActionModel(
                            name = "setMode",
                            args =
                                listOf(
                                    ArgumentModel(
                                        name = "mode",
                                        webType = null,
                                        enum = counterMode,
                                        ref = null,
                                        callback = null,
                                        nullable = true,
                                    ),
                                ),
                        ),
                        ActionModel(
                            name = "exportMode",
                            args = emptyList(),
                            returnValue =
                                ReturnModel(
                                    webType = null,
                                    enum = counterMode,
                                    ref = null,
                                ),
                        ),
                    ),
                parameters =
                    listOf(
                        ArgumentModel(
                            name = "initialMode",
                            webType = null,
                            enum = counterMode,
                            ref = null,
                            callback = null,
                        ),
                        ArgumentModel(
                            name = "modeAlert",
                            webType = null,
                            ref = null,
                            callback =
                                CallbackModel(
                                    args =
                                        listOf(
                                            CallbackArgumentModel(
                                                name = "mode",
                                                webType = null,
                                                enum = counterMode,
                                                nullable = true,
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("operator fun invoke(mode: String?)")
        kotlin.assertContains(
            """initialMode = dev.dimension.flare.web.shared.CounterMode.valueOf(requireNotNull(createArgs["initialMode"]?.jsonPrimitive).content),""",
        )
        kotlin.assertContains("modeAlertCallback(mode?.name)")
        kotlin.assertContains("""put("mode", JsonPrimitive(state.mode.name))""")
        kotlin.assertContains("""put("optionalMode", state.optionalMode?.name?.let { JsonPrimitive(it) } ?: JsonNull)""")
        kotlin.assertContains("""put("mode", value.mode?.name?.let { JsonPrimitive(it) } ?: JsonNull)""")
        kotlin.assertContains(
            """mode = requireNotNull(args)["mode"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content?.let(dev.dimension.flare.web.shared.CounterMode::valueOf),""",
        )
        kotlin.assertContains("""put("value", JsonPrimitive(result.name))""")

        manifest.assertContains(
            """{ "name": "initialMode", "kind": "enum", "tsType": "CounterMode", "values": ["Even", "Odd"] }""",
        )
        manifest.assertContains(
            """{ "name": "modeAlert", "kind": "callback", "args": [{ "name": "mode", "kind": "enum", "tsType": "CounterMode", "values": ["Even", "Odd"], "nullable": true }] }""",
        )
        manifest.assertContains(
            """{ "name": "optionalMode", "kind": "enum", "tsType": "CounterMode", "values": ["Even", "Odd"], "nullable": true }""",
        )
        manifest.assertContains(
            """{ "name": "mode", "kind": "enum", "tsType": "CounterMode", "values": ["Even", "Odd"], "nullable": true }""",
        )
        manifest.assertContains(
            """"return": { "kind": "enum", "tsType": "CounterMode", "values": ["Even", "Odd"] }""",
        )
    }

    @Test
    fun rendersSealedTypesAsDiscriminatedJsonObjects() {
        val counterMode =
            EnumModel(
                type = "dev.dimension.flare.web.shared.CounterMode",
                typeName = "CounterMode",
                values = listOf("Even", "Odd"),
            )
        val counterStatus =
            SealedModel(
                type = "dev.dimension.flare.web.shared.CounterStatus",
                typeName = "CounterStatus",
                variants =
                    listOf(
                        SealedVariantModel(
                            type = "dev.dimension.flare.web.shared.CounterStatus.Stable",
                            typeName = "Stable",
                            tag = "Stable",
                            objectLike = true,
                            properties = emptyList(),
                        ),
                        SealedVariantModel(
                            type = "dev.dimension.flare.web.shared.CounterStatus.Threshold",
                            typeName = "Threshold",
                            tag = "Threshold",
                            objectLike = false,
                            properties =
                                listOf(
                                    SealedPropertyModel(
                                        name = "count",
                                        webType = WebType.Int,
                                    ),
                                    SealedPropertyModel(
                                        name = "label",
                                        webType = WebType.String,
                                        nullable = true,
                                    ),
                                    SealedPropertyModel(
                                        name = "mode",
                                        webType = null,
                                        enum = counterMode,
                                    ),
                                ),
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "status",
                            webType = null,
                            sealed = counterStatus,
                            ref = null,
                        ),
                        PropertyModel(
                            name = "optionalStatus",
                            webType = null,
                            sealed = counterStatus,
                            ref = null,
                            nullable = true,
                        ),
                    ),
                actions =
                    listOf(
                        ActionModel(
                            name = "showStatus",
                            args =
                                listOf(
                                    ArgumentModel(
                                        name = "status",
                                        webType = null,
                                        sealed = counterStatus,
                                        ref = null,
                                        callback = null,
                                        nullable = true,
                                    ),
                                ),
                        ),
                        ActionModel(
                            name = "exportStatus",
                            args = emptyList(),
                            returnValue =
                                ReturnModel(
                                    webType = null,
                                    sealed = counterStatus,
                                    ref = null,
                                ),
                        ),
                    ),
                parameters =
                    listOf(
                        ArgumentModel(
                            name = "statusAlert",
                            webType = null,
                            ref = null,
                            callback =
                                CallbackModel(
                                    args =
                                        listOf(
                                            CallbackArgumentModel(
                                                name = "status",
                                                webType = null,
                                                sealed = counterStatus,
                                                nullable = true,
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("private fun encodeDevDimensionFlareWebSharedCounterStatusWebSealed")
        kotlin.assertContains("is dev.dimension.flare.web.shared.CounterStatus.Stable -> buildJsonObject")
        kotlin.assertContains("""put("type", "Threshold")""")
        kotlin.assertContains("""put("mode", JsonPrimitive(value.mode.name))""")
        kotlin.assertContains("private fun decodeDevDimensionFlareWebSharedCounterStatusWebSealed")
        kotlin.assertContains(""""Stable" -> dev.dimension.flare.web.shared.CounterStatus.Stable""")
        kotlin.assertContains(""""Threshold" -> dev.dimension.flare.web.shared.CounterStatus.Threshold(""")
        kotlin.assertContains("""label = json["label"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,""")
        kotlin.assertContains(
            """mode = dev.dimension.flare.web.shared.CounterMode.valueOf(requireNotNull(json["mode"]?.jsonPrimitive).content),""",
        )
        kotlin.assertContains("operator fun invoke(status: String?)")
        kotlin.assertContains(
            "statusAlertCallback(status?.let { encodeDevDimensionFlareWebSharedCounterStatusWebSealed(it, mutableListOf()).toString() })",
        )
        kotlin.assertContains("""put("status", encodeDevDimensionFlareWebSharedCounterStatusWebSealed(state.status, refs))""")
        kotlin.assertContains(
            """put("optionalStatus", state.optionalStatus?.let { encodeDevDimensionFlareWebSharedCounterStatusWebSealed(it, refs) } ?: JsonNull)""",
        )
        kotlin.assertContains(
            """status = requireNotNull(args)["status"]?.takeUnless { it == JsonNull }?.let { value -> decodeDevDimensionFlareWebSharedCounterStatusWebSealed(value, refs) },""",
        )
        kotlin.assertContains("""put("value", encodeDevDimensionFlareWebSharedCounterStatusWebSealed(result, resultRefs))""")

        manifest.assertContains(
            """"status", "kind": "sealed", "tsType": "CounterStatus", "discriminator": "type"""",
        )
        manifest.assertContains(
            """{ "name": "Stable", "tag": "Stable", "properties": [] }""",
        )
        manifest.assertContains(
            """{ "name": "Threshold", "tag": "Threshold", "properties": [{ "name": "count", "tsType": "number" }, { "name": "label", "tsType": "string", "nullable": true }, { "name": "mode", "kind": "enum", "tsType": "CounterMode", "values": ["Even", "Odd"] }] }""",
        )
        manifest.assertContains(
            """"return": { "kind": "sealed", "tsType": "CounterStatus"""",
        )
    }

    @Test
    fun rendersSealedPropertyNamesThatConflictWithTheDiscriminator() {
        val payload =
            SealedModel(
                type = "dev.dimension.flare.web.shared.NotificationPayload",
                typeName = "NotificationPayload",
                variants =
                    listOf(
                        SealedVariantModel(
                            type = "dev.dimension.flare.web.shared.NotificationPayload.Message",
                            typeName = "Message",
                            tag = "Message",
                            objectLike = false,
                            properties =
                                listOf(
                                    SealedPropertyModel(
                                        name = "type_",
                                        kotlinName = "type",
                                        webType = WebType.String,
                                    ),
                                ),
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "payload",
                            webType = null,
                            sealed = payload,
                            ref = null,
                        ),
                    ),
                actions =
                    listOf(
                        ActionModel(
                            name = "showPayload",
                            args =
                                listOf(
                                    ArgumentModel(
                                        name = "payload",
                                        webType = null,
                                        sealed = payload,
                                        ref = null,
                                        callback = null,
                                    ),
                                ),
                        ),
                    ),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        kotlin.assertContains("""put("type", "Message")""")
        kotlin.assertContains("""put("type_", JsonPrimitive(value.type))""")
        kotlin.assertContains(""""Message" -> dev.dimension.flare.web.shared.NotificationPayload.Message(""")
        kotlin.assertContains("""type = requireNotNull(json["type_"]?.jsonPrimitive).content,""")
        manifest.assertContains(
            """{ "name": "Message", "tag": "Message", "properties": [{ "name": "type_", "tsType": "string" }] }""",
        )
    }

    @Test
    fun rendersPagingStateWithExplicitPeekCallAndGetDispatch() {
        val item =
            RefModel(
                type = "dev.dimension.flare.web.shared.Sample",
                typeName = "Sample",
                properties =
                    listOf(
                        RefPropertyModel(
                            name = "text",
                            webType = WebType.String,
                        ),
                    ),
            )
        val presenter =
            counterPresenter(
                properties =
                    listOf(
                        PropertyModel(
                            name = "items",
                            webType = null,
                            ref = null,
                            pagingState = PagingStateModel(WebValueModel(ref = item)),
                        ),
                    ),
                actions = emptyList(),
            )
        val kotlin = WebPresenterRenderer.renderKotlin(listOf(presenter))
        val manifest = WebPresenterRenderer.renderManifest(listOf(presenter))

        assertTrue(!kotlin.contains("""put("items", buildJsonArray"""), kotlin)
        kotlin.assertContains(
            """put("appendState", encodeDevDimensionFlareCommonPagingStateDevDimensionFlareWebSharedSampleWebPagingStateLoadState(value.appendState))""",
        )
        kotlin.assertContains(
            """private fun encodeDevDimensionFlareCommonPagingStateDevDimensionFlareWebSharedSampleWebPagingStateLoadState(value: androidx.paging.LoadState): JsonElement =""",
        )
        kotlin.assertContains(""""__webPagingPeek:items" -> {""")
        kotlin.assertContains("pagingState.peek(index)")
        kotlin.assertContains(""""__webPagingGet:items" -> {""")
        kotlin.assertContains("""val index = requireNotNull(requireNotNull(args)["index"]?.jsonPrimitive).int""")
        kotlin.assertContains("val pagingState = state.items")
        kotlin.assertContains("pagingState[index]")
        kotlin.assertContains(""""__webPagingRetry:items" -> {""")
        kotlin.assertContains("pagingState.retry()")

        manifest.assertContains(
            """"items", "kind": "pagingState", "item": { "kind": "ref", "tsType": "Sample"""",
        )
    }

    private fun counterPresenter(
        properties: List<PropertyModel> =
            listOf(
                PropertyModel(
                    name = "count",
                    webType = WebType.Int,
                    ref = null,
                ),
            ),
        actions: List<ActionModel> = emptyList(),
        parameters: List<ArgumentModel> = counterPresenterParameters(),
    ): PresenterModel =
        PresenterModel(
            name = "counter",
            factoryName = "createCounterPresenter",
            presenterType = "dev.dimension.flare.web.shared.CounterPresenter",
            specName = "CounterPresenterWebSpec",
            stateType = "dev.dimension.flare.web.shared.CounterState",
            stateTypeName = "CounterState",
            parameters = parameters,
            properties = properties,
            actions = actions,
        )

    private fun counterPresenterParameters(): List<ArgumentModel> =
        listOf(
            ArgumentModel(
                name = "initialValue",
                webType = WebType.Int,
                ref = null,
                callback = null,
            ),
            ArgumentModel(
                name = "alert",
                webType = null,
                ref = null,
                callback =
                    CallbackModel(
                        args =
                            listOf(
                                CallbackArgumentModel(
                                    name = "value",
                                    webType = WebType.Int,
                                ),
                            ),
                    ),
            ),
        )

    private fun String.assertContains(expected: String) {
        assertTrue(
            actual = contains(expected),
            message = "Expected generated output to contain:\n$expected\n\nActual output:\n$this",
        )
    }
}
