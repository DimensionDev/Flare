package dev.dimension.flare.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FloppyDisk
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.DevModePresenter
import dev.dimension.flare.ui.screen.media.saveByteArrayToDownloads
import dev.dimension.flare.ui.theme.listCardContainer
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppLoggingScreen(onBack: () -> Unit) {
    val state by producePresenter { presenter() }
    val context = LocalContext.current
    var selectedMessage by remember { mutableStateOf<String?>(null) }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_app_logging_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(
                        onClick = {
                            saveByteArrayToDownloads(
                                context = context,
                                byteArray = state.printMessageToString().encodeToByteArray(),
                                fileName = "flare-log-${Clock.System.now().toEpochMilliseconds()}",
                                mimeType = "text/plain",
                            )
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.media_save_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.FloppyDisk,
                            contentDescription = stringResource(id = R.string.settings_app_logging_save),
                        )
                    }
                    IconButton(
                        onClick = {
                            state.clear()
                        },
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Trash,
                            contentDescription = stringResource(id = R.string.settings_app_logging_clear),
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        LazyColumn(
            contentPadding = it,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_app_logging_enable_network_logging))
                    },
                    trailingContent = {
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = {
                                state.setEnabled(it)
                            },
                        )
                    },
                    modifier =
                        Modifier
                            .listCardContainer()
                            .clickable {
                                state.setEnabled(!state.enabled)
                            },
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            itemsIndexed(state.messages) { index, it ->
                ListItem(
                    headlineContent = {
                        Text(it, maxLines = 3)
                    },
                    modifier =
                        Modifier
                            .listCard(index = index, totalCount = state.messages.size)
                            .clickable {
                                selectedMessage = it
                            },
                )
            }
        }
        if (selectedMessage != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    selectedMessage = null
                },
            ) {
                Text(
                    text = selectedMessage ?: "",
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
private fun presenter() =
    run {
        remember { DevModePresenter() }.invoke()
    }
