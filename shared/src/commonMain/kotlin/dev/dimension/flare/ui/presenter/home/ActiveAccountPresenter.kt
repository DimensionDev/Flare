package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2

public class ActiveAccountPresenter : UserPresenter(accountType = AccountType.Active, userKey = null)

@Immutable
public interface UserState {
    public val user: UiState<UiUserV2>
}
