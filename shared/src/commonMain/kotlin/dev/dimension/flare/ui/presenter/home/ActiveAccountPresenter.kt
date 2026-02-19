package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState

public class ActiveAccountPresenter : UserPresenter(accountType = AccountType.Active, userKey = null)

@Immutable
public interface UserState {
    public val user: UiState<UiProfile>
}
