package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

public class LoginFlowPresenter(
    private val handler: LoginMethodHandler,
) : PresenterBase<LoginFlowPresenter.State>() {
    @Immutable
    public interface State {
        public val flowState: LoginFlowState
        public val effects: Flow<LoginEffect>

        public fun updateField(
            id: String,
            value: String,
        )

        public fun perform(actionId: String)

        public fun resume(value: String)

        public fun canResume(value: String): Boolean

        public fun onExternalAuthenticationDismissed(error: String?)

        public fun clear()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val flowState by handler.state.collectAsState()
        DisposableEffect(handler) {
            onDispose {
                handler.close()
            }
        }
        return object : State {
            override val flowState: LoginFlowState = flowState
            override val effects: Flow<LoginEffect> = handler.effects

            override fun updateField(
                id: String,
                value: String,
            ) {
                handler.updateField(id, value)
            }

            override fun perform(actionId: String) {
                scope.launch {
                    handler.perform(actionId)
                }
            }

            override fun resume(value: String) {
                scope.launch {
                    handler.resume(value)
                }
            }

            override fun canResume(value: String): Boolean = handler.canResume(value)

            override fun onExternalAuthenticationDismissed(error: String?) {
                handler.onExternalAuthenticationDismissed(error)
            }

            override fun clear() {
                handler.clear()
            }
        }
    }
}
