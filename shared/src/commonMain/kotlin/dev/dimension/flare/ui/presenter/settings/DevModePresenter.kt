package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.dimension.flare.data.repository.DebugRepository
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

public class DevModePresenter : PresenterBase<DevModePresenter.State>() {
    public interface State {
        public val enabled: Boolean

        public fun setEnabled(value: Boolean)

        public val messages: ImmutableList<String>

        public fun printMessageToString(): String

        public fun clear()
    }

    @Composable
    override fun body(): State {
        val enabled by remember {
            DebugRepository.enabled
        }.collectAsState(false)
        val messages by remember {
            DebugRepository.messages.map {
                it.toImmutableList()
            }
        }.collectAsState(persistentListOf())
        return object : State {
            override val enabled: Boolean
                get() = enabled

            override fun setEnabled(value: Boolean) {
                DebugRepository.setEnabled(value)
            }

            override val messages: ImmutableList<String> get() = messages

            override fun printMessageToString(): String = DebugRepository.printToString()

            override fun clear() {
                DebugRepository.clear()
            }
        }
    }
}
