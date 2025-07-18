package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun AccountSelectionModal(
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val state by producePresenter { presenter() }
        state.accounts.onSuccess {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (index in 0 until it.size) {
                    val (accountKey, data) = it[index]
                    AccountItem(
                        modifier =
                            Modifier
                                .listCard(
                                    index = index,
                                    totalCount = it.size,
                                ),
                        userState = data,
                        onClick = {
                            state.setActiveAccount(it)
                            onBack.invoke()
                        },
                        toLogin = {
                            navigate(Route.ServiceSelect.Selection)
                        },
                        trailingContent = { user ->
                            state.activeAccount.onSuccess {
                                RadioButton(
                                    selected = it.accountKey == user.key,
                                    onClick = {
                                        state.setActiveAccount(
                                            user.key,
                                        )
                                        onBack.invoke()
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
        Button(
            onClick = {
                onBack.invoke()
                navigate(Route.ServiceSelect.Selection)
            },
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.quick_menu_add_account))
        }
    }
}

@Composable
private fun presenter() =
    run {
        remember {
            AccountsPresenter()
        }.invoke()
    }
