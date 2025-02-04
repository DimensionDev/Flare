package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleXmark
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.EditRssSourcePresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    style = DestinationStyle.Dialog::class,
)
@Composable
internal fun CreateRssSourceRoute(navigator: DestinationsNavigator) {
    RssSourceEditDialog(onDismissRequest = navigator::navigateUp, id = null)
}

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    style = DestinationStyle.Dialog::class,
)
@Composable
internal fun EditRssSourceRoute(
    navigator: DestinationsNavigator,
    id: Int,
) {
    RssSourceEditDialog(onDismissRequest = navigator::navigateUp, id = id)
}

@Composable
private fun RssSourceEditDialog(
    onDismissRequest: () -> Unit,
    id: Int?,
) {
    val state by producePresenter("rss_source_edit_$id") { presenter(id) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            Column(
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(8.dp),
            ) {
                OutlinedTextField2(
                    state = state.title,
                    label = { Text(text = stringResource(id = R.string.rss_sources_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                OutlinedTextField2(
                    state = state.url,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(id = R.string.rss_sources_url_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    trailingIcon = {
                        state.isValid
                            .onSuccess {
                                if (it) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.CircleCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } else {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.CircleXmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }.onError {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleXmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp),
                                )
                            }.onLoading {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                    },
                )
            }
        },
        title = {
            if (id == null) {
                Text(text = stringResource(id = R.string.add_rss_source))
            } else {
                Text(text = stringResource(id = R.string.edit_rss_source))
            }
        },
        confirmButton = {
            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    state.update(
                        url = state.url.text.toString(),
                        title = state.title.text.toString(),
                    )
                    onDismissRequest.invoke()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun presenter(id: Int?) =
    run {
        val state = remember(id) { EditRssSourcePresenter(id) }.invoke()
        val titleText = rememberTextFieldState()
        val urlText = rememberTextFieldState()
        state.data.onSuccess {
            LaunchedEffect(Unit) {
                titleText.edit {
                    append(it.title)
                }
                urlText.edit {
                    append(it.url)
                }
            }
        }
        state.defaultTitle.onSuccess {
            DisposableEffect(it) {
                if (titleText.text.isEmpty()) {
                    titleText.edit {
                        append(it)
                    }
                }
                onDispose {
                    if (titleText.text == it) {
                        titleText.edit {
                            delete(0, it.length)
                        }
                    }
                }
            }
        }
        LaunchedEffect(urlText.text) {
            state.setUrl(urlText.text.toString())
        }
        object : EditRssSourcePresenter.State by state {
            val title = titleText
            val url = urlText
        }
    }
