package dev.dimension.flare.ui.screen.compose

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavBackStackEntry
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.moriatsushi.koject.compose.rememberInject
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.data.repository.ComposeData
import dev.dimension.flare.data.repository.ComposeUseCase
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.data.repository.app.activeAccountPresenter
import dev.dimension.flare.data.repository.cache.mastodonEmojiProvider
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.TextField2
import dev.dimension.flare.ui.component.status.VisibilityIcon
import dev.dimension.flare.ui.flatMap
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.localDescription
import dev.dimension.flare.ui.model.localName
import dev.dimension.flare.ui.onSuccess
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Composable
@Preview(showBackground = true, showSystemUi = true)
fun ComposeScreenPreview() {
    ComposeScreen(onBack = {})
}

@Destination
@Composable
fun ComposeRoute(
    navigator: DestinationsNavigator
) {
    ComposeScreen(
        onBack = {
            navigator.navigateUp()
        }
    )
}

object ComposeTransitions : DestinationStyle.Animated {
    override fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition(): EnterTransition? {
        return slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Up
        ) + fadeIn()
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition(): ExitTransition? {
        return slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Up
        ) + fadeOut()
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnterTransition(): EnterTransition? {
        return slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Down
        ) + fadeIn()
    }

    override fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition(): ExitTransition? {
        return slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Down
        ) + fadeOut()
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalPermissionsApi::class
)
@Composable
fun ComposeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by producePresenter {
        composePresenter()
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4),
        onResult = { uris ->
            state.mediaState.addMedia(uris)
        }
    )
    val focusRequester = remember { FocusRequester() }
    val contentWarningFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.contentWarningState.enabled) {
        if (state.contentWarningState.enabled) {
            contentWarningFocusRequester.requestFocus()
        } else {
            focusRequester.requestFocus()
        }
    }
    val permissionState = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS,
        onPermissionResult = {
            if (it) {
                state.send()
                onBack.invoke()
            }
        }
    )

    FlareTheme {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(id = R.string.compose_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (permissionState.status.isGranted) {
                                    state.send()
                                    onBack.invoke()
                                } else {
                                    permissionState.launchPermissionRequest()
                                }
                            },
                            enabled = state.canSend
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null)
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                        ) {
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                        )
                                    )
                                },
                                enabled = state.canMedia
                            ) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null)
                            }
                            IconButton(
                                onClick = {
                                    state.pollState.togglePoll()
                                },
                                enabled = state.canPoll
                            ) {
                                Icon(imageVector = Icons.Default.Poll, contentDescription = null)
                            }
                            IconButton(
                                onClick = {
                                    state.visibilityState.showVisibilityMenu()
                                }
                            ) {
                                VisibilityIcon(visibility = state.visibilityState.visibility)
                                DropdownMenu(
                                    expanded = state.visibilityState.showVisibilityMenu,
                                    onDismissRequest = {
                                        state.visibilityState.hideVisibilityMenu()
                                    },
                                    properties = PopupProperties(focusable = false)
                                ) {
                                    state.visibilityState.allVisibilities.forEach { visibility ->
                                        DropdownMenuItem(
                                            onClick = {
                                                state.visibilityState.setVisibility(visibility)
                                                state.visibilityState.hideVisibilityMenu()
                                            },
                                            text = {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    horizontalAlignment = Alignment.Start
                                                ) {
                                                    Text(
                                                        text = stringResource(id = visibility.localName),
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    Text(
                                                        text = stringResource(id = visibility.localDescription),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                VisibilityIcon(visibility = visibility)
                                            },
                                            contentPadding = PaddingValues(
                                                horizontal = 16.dp,
                                                vertical = 8.dp
                                            )
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    state.contentWarningState.toggle()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                            }
                            val isImeVisible = WindowInsets.isImeVisible
                            IconButton(
                                onClick = {
                                    if (isImeVisible) {
                                        keyboardController?.hide()
                                    } else {
                                        keyboardController?.show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = null
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(imeHeight())
                        ) {
                            if (!WindowInsets.isImeVisible) {
                                state.emojiState.onSuccess { emojis ->
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(48.dp),
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(emojis) { emoji ->
                                            NetworkImage(
                                                model = emoji.url,
                                                contentDescription = emoji.shortcode,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clickable {
                                                        state.selectEmoji(emoji)
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(it)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(state.contentWarningState.enabled) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField2(
                            state = state.contentWarningState.textFieldState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(
                                    focusRequester = contentWarningFocusRequester
                                ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            placeholder = {
                                Text(text = stringResource(id = R.string.compose_content_warning_hint))
                            }
                        )
                        HorizontalDivider()
                    }
                }
                TextField2(
                    state = state.textFieldState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(
                            focusRequester = focusRequester
                        ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    placeholder = {
                        Text(text = stringResource(id = R.string.compose_hint))
                    }
                )
                if (state.mediaState.medias.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = screenHorizontalPadding)
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.mediaState.medias.forEach { uri ->
                            Box {
                                NetworkImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(128.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick = {
                                        state.mediaState.removeMedia(uri)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                    val sensitiveInteractionSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = screenHorizontalPadding)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = sensitiveInteractionSource,
                                indication = null
                            ) {
                                state.mediaState.setMediaSensitive(!state.mediaState.isMediaSensitive)
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.mediaState.isMediaSensitive,
                            onCheckedChange = { state.mediaState.setMediaSensitive(it) },
                            interactionSource = sensitiveInteractionSource
                        )
                        Text(text = stringResource(id = R.string.compose_media_sensitive))
                    }
                }
                if (state.pollState.enabled) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = screenHorizontalPadding)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                SegmentedButton(
                                    selected = state.pollState.pollSingleChoice,
                                    onClick = {
                                        state.pollState.setPollSingleChoice(true)
                                    },
                                    shape = SegmentedButtonDefaults.shape(
                                        position = 0,
                                        count = 2
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.compose_poll_single_choice))
                                }
                                SegmentedButton(
                                    selected = !state.pollState.pollSingleChoice,
                                    onClick = {
                                        state.pollState.setPollSingleChoice(false)
                                    },
                                    shape = SegmentedButtonDefaults.shape(
                                        position = 1,
                                        count = 2
                                    )
                                ) {
                                    Text(text = stringResource(id = R.string.compose_poll_multiple_choice))
                                }
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    state.pollState.addPollOption()
                                },
                                enabled = state.pollState.canAddPollOption
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        state.pollState.options.forEachIndexed { index, textFieldState ->
                            PollOption(
                                textFieldState = textFieldState,
                                index = index,
                                onRemove = {
                                    state.pollState.removePollOption(index)
                                }
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                state.pollState.setShowExpirationMenu(true)
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.compose_poll_expiration_at,
                                    stringResource(id = state.pollState.expiredAt.textId)
                                )
                            )
                            DropdownMenu(
                                expanded = state.pollState.showExpirationMenu,
                                onDismissRequest = {
                                    state.pollState.setShowExpirationMenu(false)
                                },
                                properties = PopupProperties(focusable = false)
                            ) {
                                PollExpiration.values().forEach { expiration ->
                                    DropdownMenuItem(
                                        onClick = {
                                            state.pollState.setExpiredAt(expiration)
                                            state.pollState.setShowExpirationMenu(false)
                                        },
                                        text = {
                                            Text(text = stringResource(id = expiration.textId))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun imeHeight(): Dp {
    var height by remember { mutableIntStateOf(0) }
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    LaunchedEffect(imeHeight) {
        height = max(height, imeHeight - navigationBarHeight)
    }
    return with(LocalDensity.current) {
        height.toDp()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PollOption(
    textFieldState: TextFieldState,
    index: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField2(
        state = textFieldState,
        modifier = modifier
            .fillMaxWidth(),
        placeholder = {
            Text(text = stringResource(id = R.string.compose_poll_option_hint, index + 1))
        },
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                enabled = index > 1
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun composePresenter(
    composeUseCase: ComposeUseCase = rememberInject()
) = run {
    val account by activeAccountPresenter()
    val emojiState = account.flatMap {
        emojiPresenter(it).emojiState
    }
    val textFieldState by remember {
        mutableStateOf(TextFieldState(""))
    }
    val text by remember {
        textFieldState.textAsFlow()
    }.collectAsState(initial = "")
    val pollState = pollPresenter()
    val mediaState = mediaPresenter()
    val visibilityState = visibilityPresenter()
    val contentWarningState = contentWarningPresenter()
    val canSend = remember(text) {
        text.isNotBlank() && text.isNotEmpty()
    }
    val canPoll = remember(mediaState) {
        mediaState.medias.isEmpty()
    }
    val canMedia = remember(mediaState, pollState) {
        mediaState.medias.size < 4 && !pollState.enabled
    }
    object {
        val textFieldState = textFieldState
        val canSend = canSend
        val canPoll = canPoll
        val canMedia = canMedia
        val pollState = pollState
        val mediaState = mediaState
        val visibilityState = visibilityState
        val contentWarningState = contentWarningState
        val emojiState = emojiState
        fun selectEmoji(emoji: UiEmoji) {
            textFieldState.edit {
                append(" :${emoji.shortcode}: ")
            }
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        fun send() {
            account.onSuccess {
                val data = when (it) {
                    is UiAccount.Mastodon -> ComposeData.Mastodon(
                        content = textFieldState.text.toString(),
                        medias = mediaState.medias,
                        poll = if (pollState.enabled) {
                            ComposeData.Mastodon.Poll(
                                multiple = !pollState.pollSingleChoice,
                                expiresIn = pollState.expiredAt.duration.inWholeSeconds,
                                options = pollState.options.map { option ->
                                    option.text.toString()
                                }
                            )
                        } else {
                            null
                        },
                        sensitive = mediaState.isMediaSensitive,
                        spoilerText = contentWarningState.textFieldState.text.toString(),
                        visibility = visibilityState.visibility,
                        inReplyToID = null,
                        account = it
                    )
                }
                composeUseCase(data)
            }
        }
    }
}

@Composable
private fun emojiPresenter(
    account: UiAccount
) = run {
    val emojiState = when (account) {
        is UiAccount.Mastodon -> mastodonEmojiProvider(account = account).toUi()
    }
    object {
        val emojiState = emojiState
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun contentWarningPresenter() = run {
    val textFieldState by remember {
        mutableStateOf(TextFieldState(""))
    }
    var enabled by remember {
        mutableStateOf(false)
    }
    object {
        val textFieldState = textFieldState
        val enabled = enabled
        fun toggle() {
            enabled = !enabled
        }
    }
}

@Composable
private fun visibilityPresenter() = run {
    var showVisibilityMenu by remember {
        mutableStateOf(false)
    }
    var visibility by remember {
        mutableStateOf(UiStatus.Mastodon.Visibility.Public)
    }
    object {
        val visibility = visibility
        val showVisibilityMenu = showVisibilityMenu
        val allVisibilities = UiStatus.Mastodon.Visibility.values()
        fun setVisibility(value: UiStatus.Mastodon.Visibility) {
            visibility = value
        }

        fun showVisibilityMenu() {
            showVisibilityMenu = true
        }

        fun hideVisibilityMenu() {
            showVisibilityMenu = false
        }
    }
}

@Composable
private fun mediaPresenter() = run {
    var medias by remember {
        mutableStateOf(listOf<Uri>())
    }
    var isMediaSensitive by remember {
        mutableStateOf(false)
    }

    object {
        val medias = medias.toImmutableList()
        val isMediaSensitive = isMediaSensitive
        fun addMedia(uris: List<Uri>) {
            medias = (medias + uris).distinct().takeLast(4)
        }

        fun removeMedia(uri: Uri) {
            medias = medias.filterNot { it == uri }
            if (medias.isEmpty()) {
                isMediaSensitive = false
            }
        }

        fun setMediaSensitive(value: Boolean) {
            isMediaSensitive = value
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun pollPresenter() = run {
    var enabled by remember {
        mutableStateOf(false)
    }
    var options by remember {
        mutableStateOf(listOf<TextFieldState>(TextFieldState(), TextFieldState()))
    }
    var pollSingleChoice by remember {
        mutableStateOf(true)
    }
    var expiredAt by remember {
        mutableStateOf(PollExpiration.Minutes5)
    }
    var showExpirationMenu by remember {
        mutableStateOf(false)
    }
    val canAddPollOption = remember(options) {
        options.size < 4
    }

    object {
        val enabled = enabled
        val options = options.toImmutableList()
        val pollSingleChoice = pollSingleChoice
        val canAddPollOption = canAddPollOption
        val expiredAt = expiredAt
        val showExpirationMenu = showExpirationMenu
        fun togglePoll() {
            enabled = !enabled
            if (!enabled) {
                options = listOf(TextFieldState(), TextFieldState())
                pollSingleChoice = true
                expiredAt = PollExpiration.Minutes5
            }
        }

        fun addPollOption() {
            options = options + TextFieldState()
        }

        fun removePollOption(index: Int) {
            options = options.filterIndexed { i, _ -> i != index }
        }

        fun setPollSingleChoice(singleChoice: Boolean) {
            pollSingleChoice = singleChoice
        }

        fun setExpiredAt(value: PollExpiration) {
            expiredAt = value
        }

        fun setShowExpirationMenu(value: Boolean) {
            showExpirationMenu = value
        }
    }
}

internal enum class PollExpiration(val textId: Int, val duration: Duration) {
    Minutes5(R.string.compose_poll_expiration_5_minutes, 5.minutes),
    Minutes30(R.string.compose_poll_expiration_30_minutes, 30.minutes),
    Hours1(R.string.compose_poll_expiration_1_hour, 1.hours),
    Hours6(R.string.compose_poll_expiration_6_hours, 6.hours),
    Hours12(R.string.compose_poll_expiration_12_hours, 12.hours),
    Days1(R.string.compose_poll_expiration_1_day, 1.days),
    Days3(R.string.compose_poll_expiration_3_days, 3.days),
    Days7(R.string.compose_poll_expiration_7_days, 7.days)
}
