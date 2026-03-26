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
        public val secretKey: TextFieldState
        public val canLogin: Boolean
    }

    @Composable
    override fun body(): State {
        val secretKey = rememberTextFieldState()

        val canLogin by remember(secretKey) {
            derivedStateOf {
                secretKey.text.isNotEmpty()
            }
        }

        return object : State {
            override val secretKey: TextFieldState = secretKey
            override val canLogin: Boolean = canLogin
        }
    }
}
