package dev.dimension.flare.feature.plugin.login

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.host.PluginUrlPolicy
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.LoginInteractionV1
import dev.dimension.flare.feature.plugin.manifest.LoginMethodManifestV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import io.ktor.http.Url

internal fun RunningPluginV1.requireLoginMethod(
    methodId: String,
    interaction: LoginInteractionV1,
): LoginMethodManifestV1 =
    installed.manifest.platform.loginMethods
        .singleOrNull { it.id == methodId && it.interaction == interaction }
        ?: throw PluginLoginException("login.method", "Login method is not declared")

internal fun RunningPluginV1.requireLoginSuccess(
    value: LoginSuccessV1,
    expectedOrigin: String,
    expectedAccountId: String?,
): LoginSuccessV1 {
    value.requireValid()
    val origin = PluginUrlPolicy.requireOrigin(value.origin)
    require(origin == expectedOrigin) { "Login origin does not match the selected instance" }
    require(value.accountId == value.profile.key.id) { "Login account ID does not match the profile" }
    require(
        value.profile.key.host
            .equals(Url(origin).accountHost(), ignoreCase = true),
    ) { "Login profile host does not match the selected instance" }
    require(expectedAccountId == null || value.accountId == expectedAccountId) { "Relogin account does not match" }
    val declared = installed.manifest.platform.capabilities
    require(
        value.capabilities.all { (capability, operations) ->
            capability in PluginAbiV1.knownCapabilityOperations &&
                declared[capability]?.operations?.keys?.containsAll(operations) == true &&
                PluginAbiV1.hasRequiredOperations(capability, operations)
        },
    ) { "Login returned undeclared or incomplete capabilities" }
    require(value.capabilities.any { (capability, operations) -> PluginAbiV1.isDisplayableCapability(capability, operations) }) {
        "Login did not negotiate a displayable capability"
    }
    return value.copy(origin = origin)
}

internal fun resolveManifestUrl(
    value: String,
    accountOrigin: String,
    approvedOrigins: Set<String>,
): String {
    val resolved =
        when {
            value == PluginAbiV1.ACCOUNT_ORIGIN -> accountOrigin
            value.startsWith("${PluginAbiV1.ACCOUNT_ORIGIN}/") -> accountOrigin + value.removePrefix(PluginAbiV1.ACCOUNT_ORIGIN)
            else -> value
        }
    return PluginUrlPolicy.requireRequestUrl(resolved, approvedOrigins).toString()
}

public class PluginLoginException(
    public val code: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal fun Url.accountHost(): String = host + if (specifiedPort == 0) "" else ":$specifiedPort"
