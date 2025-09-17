package dev.dimension.flare.ui.screen.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.presenter.PresenterBase

public class BlueskyInputPresenter : PresenterBase<BlueskyInputPresenter.State>() {
    public interface State {
        public val username: TextFieldState
        public val password: TextFieldState
        public val authFactorToken: TextFieldState
        public val usePasswordLogin: Boolean
        public val canLogin: Boolean

        public fun setUsePasswordLogin(value: Boolean)

        public fun clear()
    }

    @Composable
    override fun body(): State {
        var usePasswordLoginInternal by remember { mutableStateOf(true) }
        val username = rememberTextFieldState()
        val password = rememberTextFieldState()
        val authFactorToken = rememberTextFieldState()

        val canLogin by remember(username, password) {
            derivedStateOf {
                username.text.isNotEmpty() &&
                    (if (usePasswordLoginInternal) password.text.isNotEmpty() else true)
            }
        }

        return object : State {
            override val username: TextFieldState = username
            override val password: TextFieldState = password
            override val authFactorToken: TextFieldState = authFactorToken
            override val usePasswordLogin: Boolean
                get() = usePasswordLoginInternal
            override val canLogin: Boolean
                get() = canLogin

            override fun setUsePasswordLogin(value: Boolean) {
                usePasswordLoginInternal = value
            }

            override fun clear() {
                username.edit { replace(0, username.text.length, "") }
                password.edit { replace(0, password.text.length, "") }
                authFactorToken.edit { replace(0, authFactorToken.text.length, "") }
            }
        }
    }
}
