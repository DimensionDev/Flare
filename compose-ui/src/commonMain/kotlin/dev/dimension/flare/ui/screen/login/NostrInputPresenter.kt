package dev.dimension.flare.ui.screen.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class NostrInputPresenter : PresenterBase<NostrInputPresenter.State>() {
    @Immutable
    public interface State {
        public val accountInput: TextFieldState
        public val bunkerInput: TextFieldState
        public val canLoginAccount: Boolean
        public val canLoginBunker: Boolean
    }

    @Composable
    override fun body(): State {
        val accountInput = rememberTextFieldState()
        val bunkerInput = rememberTextFieldState()

        val canLoginAccount by remember(accountInput) {
            derivedStateOf {
                accountInput.text.isNotEmpty()
            }
        }
        val canLoginBunker by remember(bunkerInput) {
            derivedStateOf {
                bunkerInput.text.isNotEmpty()
            }
        }

        return object : State {
            override val accountInput: TextFieldState = accountInput
            override val bunkerInput: TextFieldState = bunkerInput
            override val canLoginAccount: Boolean = canLoginAccount
            override val canLoginBunker: Boolean = canLoginBunker
        }
    }
}
