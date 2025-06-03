package dev.dimension.flare.ui.screen.list

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Rss
import dev.dimension.flare.R
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.datasource.microblog.ListMetaData
import dev.dimension.flare.data.datasource.microblog.ListMetaDataType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.CreateListPresenter
import dev.dimension.flare.ui.presenter.list.CreateListState
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun CreateListDialog(
    accountType: AccountType,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val state by producePresenter {
        presenter(context, accountType, onDismissRequest)
    }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                state.setUri(uri)
            },
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = state.canConfirm,
                onClick = {
                    state.confirm()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !state.isLoading,
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.supportedMetaData.onSuccess {
                        if (it.contains(ListMetaDataType.AVATAR)) {
                            Box(
                                modifier =
                                    Modifier
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(
                                                    mediaType =
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                ),
                                            )
                                        },
                            ) {
                                if (state.uri != null) {
                                    NetworkImage(
                                        model = state.uri,
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(AvatarComponentDefaults.size)
                                                .clip(MaterialTheme.shapes.medium),
                                    )
                                } else {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Rss,
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(AvatarComponentDefaults.size)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = MaterialTheme.shapes.medium,
                                                ).padding(8.dp),
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField2(
                        state = state.text,
                        label = { Text(text = stringResource(id = R.string.list_create_name)) },
                        placeholder = { Text(text = stringResource(id = R.string.list_create_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
                state.supportedMetaData.onSuccess {
                    if (it.contains(ListMetaDataType.DESCRIPTION)) {
                        OutlinedTextField2(
                            state = state.description,
                            label = { Text(text = stringResource(id = R.string.list_create_description)) },
                            placeholder = { Text(text = stringResource(id = R.string.list_create_description_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                        )
                    }
                }
            }
        },
        title = {
            Text(text = stringResource(id = R.string.list_create))
        },
    )
}

@Composable
private fun presenter(
    context: Context,
    accountType: AccountType,
    onBack: () -> Unit,
) = run {
    val scope = rememberCoroutineScope()
    val presenter =
        remember {
            CreateListPresenter(accountType)
        }.invoke()
    var isLoading by remember {
        mutableStateOf(false)
    }
    val textState = rememberTextFieldState()
    val descriptionState = rememberTextFieldState()
    var uri by remember {
        mutableStateOf<Uri?>(null)
    }

    object : CreateListState by presenter {
        val text = textState
        val description = descriptionState
        val canConfirm = textState.text.isNotEmpty() && !isLoading
        val isLoading = isLoading
        val uri = uri

        fun setUri(value: Uri?) {
            uri = value
        }

        fun confirm() {
            scope.launch {
                isLoading = true
                presenter.createList(
                    listMetaData =
                        ListMetaData(
                            title = text.text.toString(),
                            description = description.text.toString(),
                            avatar = uri?.let { FileItem(context, it) },
                        ),
                )
                isLoading = false
                onBack.invoke()
            }
        }
    }
}
