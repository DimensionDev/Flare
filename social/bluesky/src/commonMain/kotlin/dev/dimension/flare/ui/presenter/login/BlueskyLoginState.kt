package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Immutable

@Immutable
public interface BlueskyLoginState {
    public val loading: Boolean
    public val error: Throwable?
    public val errorMessage: String?
    public val require2FA: Boolean

    public fun login(
        baseUrl: String,
        username: String,
        password: String,
        authFactorToken: String? = null,
    )

    public fun clear()
}

@Immutable
public interface BlueskyOAuthLoginState {
    public val loading: Boolean
    public val error: String?

    public fun login(
        baseUrl: String,
        userName: String,
        launchUrl: (String) -> Unit,
    )

    public fun resume(url: String)

    public fun clear()
}
