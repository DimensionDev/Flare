package dev.dimension.flare.feature.plugin.login

import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.LoginInteractionV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.LoginBeginRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.LoginTransitionV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

public class PluginFormLoginCoordinatorV1(
    private val runtimePool: PluginRuntimePool,
    private val entropy: PluginLoginEntropyV1 = PlatformPluginLoginEntropyV1,
) {
    public suspend fun login(
        plugin: RunningPluginV1,
        methodId: String,
        origin: String,
        locale: String,
        values: Map<String, String>,
        expectedAccountId: String? = null,
    ): LoginSuccessV1 {
        val method =
            plugin.installed.manifest.platform.loginMethods
                .singleOrNull { it.id == methodId }
                ?: throw PluginLoginException("login.method", "Login method is not declared")
        require(
            method.interaction == LoginInteractionV1.Password ||
                method.interaction == LoginInteractionV1.CredentialImport ||
                method.interaction == LoginInteractionV1.Form,
        ) { "Login method is not a form interaction" }
        val fields = method.fields.associateBy { it.id }
        require(values.keys.all(fields::containsKey)) { "Login contains undeclared fields" }
        require(fields.values.filter { it.required }.all { !values[it.id].isNullOrBlank() }) { "Required login field is missing" }
        require(values.size <= 32 && values.values.all { it.length <= MAX_FIELD_VALUE_LENGTH }) { "Invalid login field values" }

        val canonicalOrigin = PluginUrlPolicy.requireOrigin(origin)
        val flowId = entropy.newFlowId()
        val key = plugin.loginKey(flowId)
        val transition =
            try {
                runtimePool.invoke(
                    plugin = plugin,
                    key = key,
                    context = plugin.loginContext(canonicalOrigin, locale),
                    method = "login.$methodId.begin",
                    request =
                        LoginBeginRequestV1(
                            methodId = methodId,
                            origin = canonicalOrigin,
                            flowId = flowId,
                            values = values,
                        ),
                    requestSerializer = LoginBeginRequestV1.serializer(),
                    responseSerializer = LoginTransitionV1.serializer(),
                    timeout = PluginCallTimeoutV1.Extended,
                    validate = LoginTransitionV1::requireValid,
                )
            } finally {
                withContext(NonCancellable) { runtimePool.close(key) }
            }
        val success =
            transition as? LoginTransitionV1.Success
                ?: throw PluginLoginException("login.transition", "Form login did not complete")
        return plugin.requireLoginSuccess(success.value, canonicalOrigin, expectedAccountId)
    }
}

private const val MAX_FIELD_VALUE_LENGTH = 16 * 1_024
