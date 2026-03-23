package dev.dimension.flare.ui.screen.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.network.nostr.defaultNostrRelays
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class NostrInputPresenter : PresenterBase<NostrInputPresenter.State>() {
    @Immutable
    public interface State {
        public val publicKey: TextFieldState
        public val secretKey: TextFieldState
        public val relays: TextFieldState
        public val canLogin: Boolean
    }

    @Composable
    override fun body(): State {
        val publicKey = rememberTextFieldState()
        val secretKey = rememberTextFieldState()
        val relays =
            rememberTextFieldState(
                initialText = defaultNostrRelays.joinToString(", "),
            )

        val canLogin by remember(publicKey, secretKey) {
            derivedStateOf {
                publicKey.text.isNotEmpty() || secretKey.text.isNotEmpty()
            }
        }

        return object : State {
            override val publicKey: TextFieldState = publicKey
            override val secretKey: TextFieldState = secretKey
            override val relays: TextFieldState = relays
            override val canLogin: Boolean = canLogin
        }
    }
}
