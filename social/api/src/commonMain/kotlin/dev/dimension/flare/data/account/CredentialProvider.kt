package dev.dimension.flare.data.account

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public interface CredentialProvider {
    public fun credentialJsonFlow(accountKey: MicroBlogKey): Flow<String>
}

public inline fun <reified T : UiAccount.Credential> CredentialProvider.credentialFlow(accountKey: MicroBlogKey): Flow<T> =
    credentialJsonFlow(accountKey).map { it.decodeJson<T>() }
