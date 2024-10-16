package dev.dimension.flare.ui.presenter.dm

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.presenter.PresenterBase

class DMConversationPresenter(
    private val accountType: AccountType,
    private val id: String,
) : PresenterBase<DMConversationState>() {
    @Composable
    override fun body(): DMConversationState {
        TODO()
    }
}

interface DMConversationState {
    val items: PagingState<UiDMItem>
    val user: UiState<UiUserV2>

    fun send(message: String)
}
