package dev.dimension.flare.ui.screen.list

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditListMemberRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.Trash
import compose.icons.fontawesomeicons.solid.UserPen
import dev.dimension.flare.R
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onError
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.data.datasource.microblog.ListMetaData
import dev.dimension.flare.data.datasource.microblog.ListMetaDataType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.EditListState
import dev.dimension.flare.ui.presenter.list.ListEditPresenter
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun EditListRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    listId: String,
) {
    EditListScreen(
        accountType,
        listId,
        onBack = navigator::navigateUp,
        toEditUser = {
            navigator.navigate(EditListMemberRouteDestination(accountType, listId))
        },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
private fun EditListScreen(
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
    toEditUser: () -> Unit,
) {
    val context = LocalContext.current
    val state by producePresenter {
        presenter(context, accountType, listId, onBack)
    }
    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                state.setAvatar(uri)
            },
        )
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.list_edit))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(
                        onClick = state::confirm,
                        enabled = state.listInfo is UiState.Success && !state.isLoading,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Check,
                            contentDescription = stringResource(id = android.R.string.ok),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
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
                                    if (state.avatar != null) {
                                        NetworkImage(
                                            model = state.avatar,
                                            contentDescription = null,
                                            modifier =
                                                Modifier
                                                    .size(AvatarComponentDefaults.size)
                                                    .clip(MaterialTheme.shapes.medium),
                                        )
                                    } else {
                                        state.listInfo
                                            .onSuccess {
                                                if (it.avatar != null) {
                                                    NetworkImage(
                                                        model = it.avatar,
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
                                                                ),
                                                    )
                                                }
                                            }.onLoading {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .size(AvatarComponentDefaults.size)
                                                            .clip(MaterialTheme.shapes.medium)
                                                            .placeholder(true),
                                                )
                                            }
                                    }
                                }
                            }
                        }

                        OutlinedTextField2(
                            state = state.text,
                            label = { Text(text = stringResource(id = R.string.list_create_name)) },
                            placeholder = { Text(text = stringResource(id = R.string.list_create_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading && state.listInfo is UiState.Success,
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
                                enabled = !state.isLoading && state.listInfo is UiState.Success,
                            )
                        }
                    }
                }
            }
            stickyHeader {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.list_edit_members))
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            toEditUser.invoke()
                        }) {
                            FAIcon(
                                FontAwesomeIcons.Solid.UserPen,
                                contentDescription = stringResource(id = R.string.list_edit_edit_members),
                            )
                        }
                    },
                )
            }
            state.memberInfo
                .onSuccess {
                    items(itemCount) { index ->
                        val item = get(index)
                        val userState =
                            item?.let {
                                UiState.Success(item)
                            } ?: UiState.Loading()
                        AccountItem(
                            userState = userState,
                            onClick = {},
                            toLogin = { },
                            trailingContent = {
                                var showMenu by remember {
                                    mutableStateOf(false)
                                }
                                IconButton(onClick = {
                                    showMenu = true
                                }) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.EllipsisVertical,
                                        contentDescription = stringResource(id = R.string.more),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = {
                                        showMenu = false
                                    },
                                ) {
                                    DropdownMenuItem(
                                        onClick = {
                                            showMenu = false
                                            if (item != null) {
                                                state.removeMember(item.key)
                                            }
                                        },
                                        text = {
                                            Text(
                                                text = stringResource(id = R.string.delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.Trash,
                                                contentDescription = stringResource(id = R.string.delete),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    }
                }.onLoading {
                    items(10) {
                        AccountItem(userState = UiState.Loading(), onClick = {}, toLogin = { })
                    }
                }.onEmpty {
                    item {
                        Text(text = stringResource(id = R.string.list_edit_no_members))
                    }
                }.onError {
                    item {
                        Text(text = stringResource(id = R.string.list_edit_members_error))
                    }
                }
        }
    }
}

@Composable
private fun presenter(
    context: Context,
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
) = run {
    val scope = rememberCoroutineScope()
    val state =
        remember(accountType, listId) {
            ListEditPresenter(accountType, listId)
        }.invoke()
    val text = rememberTextFieldState()
    val description = rememberTextFieldState()
    state.listInfo.onSuccess {
        LaunchedEffect(Unit) {
            text.edit {
                append(it.title)
            }
        }
    }
    var avatar by remember {
        mutableStateOf<Uri?>(null)
    }
    var isLoading by remember {
        mutableStateOf(false)
    }

    object : EditListState by state {
        val text = text
        val description = description
        val isLoading = isLoading
        val avatar = avatar

        fun setAvatar(value: Uri?) {
            avatar = value
        }

        fun confirm() {
            scope.launch {
                isLoading = true
                state.updateList(
                    listMetaData =
                        ListMetaData(
                            title = text.text.toString(),
                            description = description.text.toString(),
                            avatar = avatar?.let { FileItem(context, it) },
                        ),
                )
                isLoading = false
                onBack.invoke()
            }
        }
    }
}
