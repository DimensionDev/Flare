package dev.dimension.flare.data.account

import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.flow.Flow

public interface AccountProfileProvider {
    public val accounts: Flow<List<AccountProfile>>

    public data class AccountProfile(
        val account: UiAccount,
        val avatar: String?,
    )
}
