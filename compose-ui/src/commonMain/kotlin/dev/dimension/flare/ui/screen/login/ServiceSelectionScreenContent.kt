package dev.dimension.flare.ui.screen.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleQuestion
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.common.onEmpty
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.eula_privacy_policy
import dev.dimension.flare.compose.ui.login_agreement
import dev.dimension.flare.compose.ui.mastodon_login_verify_message
import dev.dimension.flare.compose.ui.nostr_login_qr_button
import dev.dimension.flare.compose.ui.nostr_login_qr_hint
import dev.dimension.flare.compose.ui.nostr_login_qr_link_label
import dev.dimension.flare.compose.ui.nostr_login_qr_waiting
import dev.dimension.flare.compose.ui.service_select_compatibility_warning
import dev.dimension.flare.compose.ui.service_select_empty_message
import dev.dimension.flare.compose.ui.service_select_instance_input_placeholder
import dev.dimension.flare.compose.ui.service_select_welcome_hint
import dev.dimension.flare.compose.ui.service_select_welcome_list_hint
import dev.dimension.flare.compose.ui.service_select_welcome_message
import dev.dimension.flare.compose.ui.service_select_welcome_title
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.platform.PlatformCircularProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformFilledTonalButton
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformLinearProgressIndicator
import dev.dimension.flare.ui.component.platform.PlatformPicker
import dev.dimension.flare.ui.component.platform.PlatformSecureTextField
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextField
import dev.dimension.flare.ui.component.res
import dev.dimension.flare.ui.component.status.AdaptiveCard
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.toImageVector
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.login.LoginEffect
import dev.dimension.flare.ui.presenter.login.LoginField
import dev.dimension.flare.ui.presenter.login.LoginFieldType
import dev.dimension.flare.ui.presenter.login.LoginFlowPresenter
import dev.dimension.flare.ui.presenter.login.LoginMethodSpec
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ServiceSelectionScreenContent(
    onWebViewLogin: (url: String, cookieCallback: (cookies: String?) -> Boolean) -> Unit,
    onBack: (() -> Unit),
    openUri: (String) -> Unit,
    registerDeeplinkCallback: @Composable ((url: String) -> Unit) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val state by producePresenter {
        remember { SelectionPresenter(onBack) }.body()
    }
    LazyStatusVerticalStaggeredGrid(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        columns = StaggeredGridCells.Adaptive(300.dp),
        contentPadding = contentPadding,
        forceCardMode = true,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PlatformText(
                    text = stringResource(Res.string.service_select_welcome_title),
                    style = PlatformTheme.typography.headline,
                )
                PlatformText(
                    text = stringResource(Res.string.service_select_welcome_message),
                    textAlign = TextAlign.Center,
                )
                PlatformTextField(
                    state = state.instanceInputState,
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                        ),
                    placeholder = {
                        PlatformText(text = stringResource(Res.string.service_select_instance_input_placeholder))
                    },
                    trailingIcon = {
                        PlatformIconButton(onClick = {
                            if (state.instanceInputState.text.any()) {
                                state.clearInstance()
                            }
                        }) {
                            if (state.instanceInputState.text.any()) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Xmark,
                                    contentDescription = null,
                                )
                            } else {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    modifier = Modifier.width(300.dp),
                    leadingIcon = {
                        state.detectedPlatformType
                            .onSuccess {
                                FAIcon(
                                    imageVector = state.platformIcon(it.platformType).toImageVector(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }.onError {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.CircleQuestion,
                                    contentDescription = null,
                                )
                            }.onLoading {
                                PlatformCircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                    },
                    enabled = !state.loading,
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                PlatformText(
                    stringResource(Res.string.service_select_welcome_hint),
                    textAlign = TextAlign.Center,
                    style = PlatformTheme.typography.caption,
                )
                AnimatedVisibility(state.canNext && state.detectedPlatformType.isSuccess && state.instanceInputState.text.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.imePadding(),
                    ) {
                        state.detectedPlatformType.takeSuccess()?.let {
                            if (it.compatibleMode) {
                                PlatformText(
                                    stringResource(
                                        Res.string.service_select_compatibility_warning,
                                        it.software,
                                    ),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        state.detectedPlatformType.onSuccess { nodeData ->
                            GenericLoginContent(
                                state = state,
                                platformType = nodeData.platformType,
                                host = nodeData.host,
                                openUri = openUri,
                                onWebViewLogin = onWebViewLogin,
                                registerDeeplinkCallback = registerDeeplinkCallback,
                            )
                            LoginAgreement(
                                platformType = nodeData.platformType,
                                host = nodeData.host,
                                agreementUrl = state::agreementUrl,
                                openUri = openUri,
                            )
                        }
                    }
                }
            }
        }

        if (!(state.canNext && state.detectedPlatformType.isSuccess && state.instanceInputState.text.isNotEmpty())) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                PlatformText(
                    text = stringResource(Res.string.service_select_welcome_list_hint),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.instances
                .onSuccess {
                    items(count = itemCount) {
                        val instance = get(it)
                        ServiceSelectItem(
                            instance = instance,
                            platformIcon = state::platformIcon,
                            index = it,
                            totalCount = itemCount,
                            onClick = {
                                if (instance != null) {
                                    state.selectInstance(instance)
                                }
                            },
                        )
                    }
                }.onLoading {
                    items(10) {
                        ServiceSelectItem(
                            index = it,
                            totalCount = 10,
                            instance = null,
                            platformIcon = state::platformIcon,
                            onClick = {},
                        )
                    }
                }.onEmpty {
                    items(1) {
                        PlatformText(
                            text = stringResource(Res.string.service_select_empty_message),
                            style = PlatformTheme.typography.title,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = screenHorizontalPadding),
                        )
                    }
                }
        }
    }
}

@Composable
private fun GenericLoginContent(
    state: SelectionPresenter.State,
    platformType: PlatformType,
    host: String,
    openUri: (String) -> Unit,
    onWebViewLogin: (url: String, cookieCallback: (cookies: String?) -> Boolean) -> Unit,
    registerDeeplinkCallback: @Composable ((url: String) -> Unit) -> Unit,
) {
    val methods = state.loginMethods(platformType)
    if (methods.isEmpty()) return
    var selectedMethod by remember(platformType, host) { mutableStateOf(methods.first().type) }
    val selectedSpec = methods.firstOrNull { it.type == selectedMethod } ?: methods.first()
    val handler =
        remember(platformType, host, selectedMethod) {
            state.createLoginHandler(
                platformType = platformType,
                host = host,
                methodType = selectedMethod,
            )
        }
    val loginState by producePresenter("login_flow_${platformType.name}_${host}_$selectedMethod") {
        remember(handler) { LoginFlowPresenter(handler) }.body()
    }
    var qrContent by remember(handler) { mutableStateOf<String?>(null) }
    registerDeeplinkCallback {
        loginState.resume(it)
    }
    LaunchedEffect(loginState.effects) {
        loginState.effects.collect { effect ->
            when (effect) {
                is LoginEffect.OpenUrl -> {
                    openUri(effect.url)
                }

                is LoginEffect.ShowQr -> {
                    qrContent = effect.content
                }

                is LoginEffect.OpenWebCookieLogin -> {
                    onWebViewLogin(effect.url) { cookies ->
                        if (cookies.isNullOrBlank()) {
                            false
                        } else if (!loginState.canResume(cookies)) {
                            false
                        } else {
                            loginState.resume(cookies)
                            true
                        }
                    }
                }
            }
        }
    }
    if (methods.size > 1) {
        LoginMethodPicker(
            methods = methods,
            selectedSpec = selectedSpec,
            onSelected = {
                selectedMethod = it.type
            },
        )
    }
    LoginFlowContent(
        state = loginState,
        qrContent = qrContent,
        onQrDismiss = {
            qrContent = null
        },
    )
}

@Composable
private fun LoginMethodPicker(
    methods: List<LoginMethodSpec>,
    selectedSpec: LoginMethodSpec,
    onSelected: (LoginMethodSpec) -> Unit,
) {
    val labels =
        methods
            .map { stringResource(it.title.res) }
            .toImmutableList()
    PlatformPicker(
        modifier =
            Modifier.width(300.dp),
        options = labels,
        onSelected = { index ->
            methods.getOrNull(index)?.let(onSelected)
        },
    )
}

@Composable
private fun LoginFlowContent(
    state: LoginFlowPresenter.State,
    qrContent: String?,
    onQrDismiss: () -> Unit,
) {
    val flowState = state.flowState
    Column(
        modifier =
            Modifier
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        flowState.fields.fastForEach { field ->
            key(field.id) {
                LoginFieldInput(
                    field = field,
                    enabled = !flowState.loading && !field.readOnly,
                    onUpdate = state::updateField,
                    onSubmit = {
                        flowState.actions.firstOrNull { it.enabled }?.let {
                            state.perform(it.id)
                        }
                    },
                )
            }
        }
        qrContent?.let {
            QrLoginContent(
                content = it,
                onDismiss = onQrDismiss,
            )
        }
        flowState.actions.fastForEach { action ->
            PlatformFilledTonalButton(
                onClick = {
                    state.perform(action.id)
                },
                modifier = Modifier.width(300.dp),
                enabled = action.enabled && !flowState.loading,
            ) {
                PlatformText(text = stringResource(action.label.res))
            }
        }
        flowState.error?.let {
            PlatformText(
                text = it,
                textAlign = TextAlign.Center,
            )
        }
        if (flowState.loading) {
            PlatformText(
                text = stringResource(Res.string.mastodon_login_verify_message),
                textAlign = TextAlign.Center,
            )
            PlatformLinearProgressIndicator()
        }
    }
}

@Composable
private fun LoginFieldInput(
    field: LoginField,
    enabled: Boolean,
    onUpdate: (String, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val textState = rememberTextFieldState(field.value)
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    LaunchedEffect(field.value) {
        if (textState.text.toString() != field.value) {
            textState.edit {
                replace(0, textState.text.length, field.value)
            }
        }
    }
    if (field.type != LoginFieldType.DisplayText) {
        LaunchedEffect(textState, field.id) {
            snapshotFlow { textState.text.toString() }
                .distinctUntilChanged()
                .collect {
                    currentOnUpdate(field.id, it)
                }
        }
    }
    val label: @Composable () -> Unit = {
        PlatformText(text = stringResource(field.label.res))
    }
    val placeholder: (@Composable () -> Unit)? =
        field.placeholder?.let { placeholder ->
            {
                PlatformText(text = stringResource(placeholder.res))
            }
        }
    val keyboardOptions =
        KeyboardOptions(
            keyboardType =
                when (field.type) {
                    LoginFieldType.PasswordInput -> KeyboardType.Password

                    LoginFieldType.OtpInput -> KeyboardType.Text

                    LoginFieldType.TextInput,
                    LoginFieldType.DisplayText,
                    -> KeyboardType.Text
                },
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        )
    when (field.type) {
        LoginFieldType.PasswordInput -> {
            PlatformSecureTextField(
                state = textState,
                label = label,
                placeholder = placeholder,
                enabled = enabled,
                modifier = Modifier.width(300.dp),
                keyboardOptions = keyboardOptions,
                onKeyboardAction = {
                    onSubmit()
                },
            )
        }

        LoginFieldType.TextInput,
        LoginFieldType.OtpInput,
        -> {
            PlatformTextField(
                state = textState,
                label = label,
                placeholder = placeholder,
                enabled = enabled,
                modifier = Modifier.width(300.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = {
                    onSubmit()
                },
            )
        }

        LoginFieldType.DisplayText -> {
            PlatformText(
                text = field.value,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(300.dp),
            )
        }
    }
    field.error?.let {
        PlatformText(
            text = it,
            textAlign = TextAlign.Center,
            style = PlatformTheme.typography.caption,
        )
    }
}

@Composable
private fun QrLoginContent(
    content: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.width(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlatformText(
            text = stringResource(Res.string.nostr_login_qr_hint),
            textAlign = TextAlign.Center,
            style = PlatformTheme.typography.caption,
        )
        Image(
            painter = rememberQrCodePainter(content),
            contentDescription = stringResource(Res.string.nostr_login_qr_button),
            modifier = Modifier.size(220.dp),
        )
        PlatformText(
            text = stringResource(Res.string.nostr_login_qr_waiting),
            textAlign = TextAlign.Center,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlatformText(
                text = stringResource(Res.string.nostr_login_qr_link_label),
                style = PlatformTheme.typography.caption,
            )
            SelectionContainer {
                PlatformText(
                    text = content,
                    textAlign = TextAlign.Center,
                    style = PlatformTheme.typography.caption,
                )
            }
        }
        PlatformFilledTonalButton(
            onClick = onDismiss,
            modifier = Modifier.width(300.dp),
        ) {
            PlatformText(text = stringResource(UiStrings.Cancel.res))
        }
    }
}

@Composable
private fun ServiceSelectItem(
    instance: UiInstance?,
    platformIcon: (PlatformType) -> UiIcon,
    onClick: () -> Unit,
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    AdaptiveCard(
        index = index,
        totalCount = totalCount,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .clickable {
                        onClick.invoke()
                    }.fillMaxWidth()
                    .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            instance?.bannerUrl?.let {
                NetworkImage(
                    it,
                    contentDescription = null,
                    modifier = Modifier.clip(PlatformTheme.shapes.medium),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!instance?.iconUrl.isNullOrEmpty()) {
                    NetworkImage(
                        instance.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                } else if (instance != null) {
                    FAIcon(
                        imageVector = platformIcon(instance.type).toImageVector(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                PlatformText(
                    text = instance?.name ?: "Loading...",
                    style = PlatformTheme.typography.title,
                    modifier = Modifier.placeholder(instance == null),
                )
            }
            PlatformText(
                text = instance?.domain ?: "Loading...",
                style = PlatformTheme.typography.caption,
                modifier = Modifier.placeholder(instance == null),
            )
            PlatformText(
                text =
                    instance?.description
                        ?: "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                style = PlatformTheme.typography.caption,
                modifier = Modifier.placeholder(instance == null),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoginAgreement(
    platformType: PlatformType,
    host: String,
    agreementUrl: (PlatformType, String) -> String?,
    openUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = agreementUrl(platformType, host) ?: return
    val linkText = stringResource(Res.string.eula_privacy_policy)
    val fullText = stringResource(Res.string.login_agreement, linkText)
    val color = PlatformTheme.colorScheme.primary
    val annotatedString =
        remember(platformType, host, url, linkText, fullText, color) {
            buildAnnotatedString {
                append(fullText)
                val startIndex = fullText.indexOf(linkText)
                if (startIndex != -1) {
                    val endIndex = startIndex + linkText.length
                    addStyle(
                        style = SpanStyle(color = color),
                        start = startIndex,
                        end = endIndex,
                    )
                    addLink(
                        url = LinkAnnotation.Url(url),
                        start = startIndex,
                        end = endIndex,
                    )
                }
            }
        }

    PlatformText(
        text = annotatedString,
        modifier = modifier,
        style = PlatformTheme.typography.caption,
        textAlign = TextAlign.Center,
    )
}
