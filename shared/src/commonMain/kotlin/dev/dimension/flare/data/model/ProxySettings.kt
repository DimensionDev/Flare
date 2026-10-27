package dev.dimension.flare.data.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public sealed class ProxyRef {
    @Serializable public data object Global : ProxyRef()

    @Serializable public data class Account(
        val accountKey: MicroBlogKey,
    ) : ProxyRef()

    @Serializable public data object System : ProxyRef()

    @Serializable public data object Unbound : ProxyRef()

    public companion object {
        public fun forAccount(accountKey: MicroBlogKey?): ProxyRef =
            when {
                accountKey == null -> Global
                accountKey.host == "nostr" -> System
                else -> Account(accountKey)
            }

        public fun forAccountType(accountType: AccountType): ProxyRef = forAccount((accountType as? AccountType.Specific)?.accountKey)
    }
}

public enum class ProxySettingsError { NAME_REQUIRED, INVALID_HOST, INVALID_PORT, IN_USE, INVALID_POLICY, REQUEST_FAILED }

internal class ProxySettingsException(
    val reason: ProxySettingsError,
) : IllegalArgumentException(reason.name)

private fun requireProxy(
    condition: Boolean,
    reason: ProxySettingsError,
) {
    if (!condition) throw ProxySettingsException(reason)
}

@Serializable
public enum class ProxyProtocol { HTTPS, SOCKS5 }

@Serializable
public enum class ProxyMode { SYSTEM, DIRECT, PROXY, INHERIT }

@Serializable
public data class ProxyPolicy(
    val mode: ProxyMode = ProxyMode.INHERIT,
    val proxyId: String? = null,
)

@Serializable
public data class ProxyProfile(
    val id: String,
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
) {
    internal fun validate() {
        requireProxy(id.isNotBlank() && name.isNotBlank(), ProxySettingsError.NAME_REQUIRED)
        requireProxy(
            host.isNotBlank() && host == host.trim() && host.none { it.isWhitespace() || it in "/\\@?#" },
            ProxySettingsError.INVALID_HOST,
        )
        requireProxy(!host.contains("://") && (':' !in host || host.count { it == ':' } >= 2), ProxySettingsError.INVALID_HOST)
        requireProxy(port in 1..65535, ProxySettingsError.INVALID_PORT)
    }

    internal fun connection(): ProxyConnection {
        validate()
        return ProxyConnection(ProxyMode.PROXY, protocol, host.removeSurrounding("[", "]"), port)
    }
}

// A connection deliberately excludes the display name, so renaming does not interrupt requests.
@Immutable
public data class ProxyConnection internal constructor(
    val mode: ProxyMode,
    val protocol: ProxyProtocol? = null,
    val host: String? = null,
    val port: Int? = null,
)

@Serializable
public data class ProxySettings(
    val profiles: List<ProxyProfile> = emptyList(),
    val global: ProxyPolicy = ProxyPolicy(ProxyMode.SYSTEM),
    val accounts: Map<String, ProxyPolicy> = emptyMap(),
    val removedAccounts: Set<String> = emptySet(),
) {
    public fun policy(accountKey: MicroBlogKey?): ProxyPolicy =
        if (accountKey == null) global else accounts[accountKey.toString()] ?: ProxyPolicy()

    internal fun resolve(ref: ProxyRef): ProxyConnection {
        if (ref == ProxyRef.System) return ProxyConnection(ProxyMode.SYSTEM)
        check(ref != ProxyRef.Unbound) { "Media is missing its account proxy scope" }
        check((ref as? ProxyRef.Account)?.accountKey?.toString() !in removedAccounts) { "Account has been removed" }
        val selected = policy((ref as? ProxyRef.Account)?.accountKey).let { if (it.mode == ProxyMode.INHERIT) global else it }
        return when (selected.mode) {
            ProxyMode.SYSTEM, ProxyMode.DIRECT -> {
                ProxyConnection(selected.mode)
            }

            ProxyMode.PROXY -> {
                requireNotNull(
                    profiles.find { it.id == selected.proxyId },
                ) { "Selected proxy no longer exists" }.connection()
            }

            ProxyMode.INHERIT -> {
                error("Global proxy cannot inherit")
            }
        }
    }

    internal fun validatePolicy(
        policy: ProxyPolicy,
        account: Boolean,
    ) {
        requireProxy(account || policy.mode != ProxyMode.INHERIT, ProxySettingsError.INVALID_POLICY)
        requireProxy(!account || policy.mode != ProxyMode.SYSTEM, ProxySettingsError.INVALID_POLICY)
        if (policy.mode == ProxyMode.PROXY) {
            requireProxy(profiles.any { it.id == policy.proxyId }, ProxySettingsError.INVALID_POLICY)
        } else {
            requireProxy(policy.proxyId == null, ProxySettingsError.INVALID_POLICY)
        }
    }

    internal fun save(profile: ProxyProfile): ProxySettings {
        profile.validate()
        return copy(profiles = profiles.filterNot { it.id == profile.id } + profile)
    }

    internal fun remove(id: String): ProxySettings {
        requireProxy(
            global.proxyId != id &&
                accounts.values.none {
                    it.proxyId == id
                },
            ProxySettingsError.IN_USE,
        )
        return copy(profiles = profiles.filterNot { it.id == id })
    }
}
