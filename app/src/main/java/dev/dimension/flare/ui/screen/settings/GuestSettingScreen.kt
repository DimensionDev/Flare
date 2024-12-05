package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleQuestion
import dev.dimension.flare.R
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.GuestConfigPresenter
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.tlaster.precompose.molecule.producePresenter

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    style = DestinationStyle.Dialog::class,
)
@Composable
internal fun GuestSettingRoute(navigator: DestinationsNavigator) {
    GuestSettingScreen(
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestSettingScreen(onBack: () -> Unit) {
    val state by producePresenter {
        presenter()
    }
    AlertDialog(
        onDismissRequest = onBack,
        confirmButton = {
            TextButton(
                onClick = {
                    state.platformType.onSuccess {
                        state.save(
                            host = state.text.text.toString(),
                            platformType = it,
                        )
                    }
                    onBack.invoke()
                },
                enabled = state.canSave,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onBack,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            OutlinedTextField2(
                state = state.text,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.service_select_instance_input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier =
                    Modifier
                        .width(300.dp),
                leadingIcon = {
                    state.platformType
                        .onSuccess {
                            if (it in state.supportedPlatforms) {
                                NetworkImage(
                                    it.logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            } else {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.CircleQuestion,
                                    contentDescription = null,
                                )
                            }
                        }.onError {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.CircleQuestion,
                                contentDescription = null,
                            )
                        }.onLoading {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                            )
                        }
                },
            )
        },
        title = {
            Text(text = stringResource(id = R.string.settings_guest_setting_title))
        },
    )
}

@Composable
private fun presenter() =
    run {
        val state = remember { GuestConfigPresenter() }.invoke()
        val textState = rememberTextFieldState()
        state.data.onSuccess {
            LaunchedEffect(Unit) {
                textState.edit {
                    append(it)
                }
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { textState.text }
                .distinctUntilChanged()
                .collect {
                    state.setHost(it.toString())
                }
        }
        object : GuestConfigPresenter.State by state {
            val text = textState
        }
    }
