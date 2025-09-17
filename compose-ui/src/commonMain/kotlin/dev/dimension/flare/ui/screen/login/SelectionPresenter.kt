package dev.dimension.flare.ui.screen.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.login.ServiceSelectPresenter
import dev.dimension.flare.ui.presenter.login.ServiceSelectState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged

public class SelectionPresenter(
    private val onBack: () -> Unit,
) : PresenterBase<SelectionPresenter.State>() {
    @Immutable
    public interface State : ServiceSelectState {
        public val instanceInputState: TextFieldState
        public val blueskyInputState: BlueskyInputPresenter.State

        public fun selectInstance(instance: UiInstance)

        public fun clearInstance()
    }

    @OptIn(FlowPreview::class)
    @Composable
    override fun body(): State {
        val instanceInputState = rememberTextFieldState()

        val baseState: ServiceSelectState =
            remember {
                ServiceSelectPresenter(
                    toHome = {
                        instanceInputState.edit {
                            replace(0, instanceInputState.text.length, "")
                        }
                        onBack.invoke()
                    },
                )
            }.body()

        LaunchedEffect(Unit) {
            snapshotFlow { instanceInputState.text }
                .distinctUntilChanged()
                .collect { baseState.setFilter(it.toString()) }
        }

        val blueskyInputState = remember { BlueskyInputPresenter() }.body()

        return object : State, ServiceSelectState by baseState {
            override val instanceInputState: TextFieldState = instanceInputState
            override val blueskyInputState: BlueskyInputPresenter.State = blueskyInputState

            override fun selectInstance(instance: UiInstance) {
                instanceInputState.edit {
                    replace(0, instanceInputState.text.length, instance.domain)
                }
                baseState.setFilter(instance.domain)
            }

            override fun clearInstance() {
                instanceInputState.edit {
                    replace(0, instanceInputState.text.length, "")
                }
                baseState.setFilter("")
            }
        }
    }
}
