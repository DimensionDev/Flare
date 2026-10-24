package dev.dimension.flare.ui.screen.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.dimension.flare.R
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.ProxySettingsPresenter
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import moe.tlaster.precompose.molecule.producePresenter

/** First Android release that gates local-network access behind a runtime permission. */
private const val ANDROID_17_SDK = 37

/**
 * Manifest permission introduced in Android 17. Kept as a literal so this file compiles against
 * older SDKs as well; the constant would be inlined anyway.
 */
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** Debounce for persisting proxy text edits while the user is typing. */
private const val PROXY_EDIT_DEBOUNCE_MS = 400L

private fun hasLocalNetworkPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < ANDROID_17_SDK ||
        ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

private fun String.toPortOrZero(): Int = filter { it.isDigit() }.toIntOrNull() ?: 0

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)
@Composable
internal fun NetworkProxyScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter("network_proxy_settings") {
        remember { ProxySettingsPresenter() }.invoke()
    }
    val proxy = state.proxy
    val isHttp = proxy.type == AppSettings.ProxyConfig.Type.Http

    val context = LocalContext.current
    var localNetworkPermissionGranted by remember {
        mutableStateOf(hasLocalNetworkPermission(context))
    }
    val localNetworkPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            localNetworkPermissionGranted = granted
        }
    LaunchedEffect(Unit) {
        // Android 17 blocks TCP to local addresses (e.g. a Clash instance on 192.168.x.x) unless
        // this permission is granted. Ask on entry: both "custom proxy" and "follow system" can
        // point at a proxy on the local network.
        if (!localNetworkPermissionGranted) {
            localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        }
    }

    // The text fields are backed by TextFieldState, not by the persisted value: the IME has to own
    // the cursor while typing. Persisting is debounced below, and updates never flow back into the
    // fields, so characters can no longer be inserted at the wrong position.
    val hostState = rememberTextFieldState()
    val portState = rememberTextFieldState()
    var seeded by remember { mutableStateOf(false) }

    LaunchedEffect(state.proxyLoaded) {
        // Seed the fields once the persisted settings have been read; after that the TextFieldState
        // owns the text, so the IME cursor is never reset by a datastore round-trip.
        if (state.proxyLoaded && !seeded) {
            if (hostState.text.toString() != proxy.host) {
                hostState.setTextAndPlaceCursorAtEnd(proxy.host)
            }
            val portText = if (proxy.port == 0) "" else proxy.port.toString()
            if (portState.text.toString() != portText) {
                portState.setTextAndPlaceCursorAtEnd(portText)
            }
            seeded = true
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { hostState.text.toString().trim() to portState.text.toString() }
            .debounce(PROXY_EDIT_DEBOUNCE_MS)
            .collect { (host, rawPort) -> state.updateHostPort(host, rawPort.toPortOrZero()) }
    }

    LaunchedEffect(Unit) {
        // Keep the port field numeric without touching the host field.
        snapshotFlow { portState.text.toString() }.collect { raw ->
            val digits = raw.filter { it.isDigit() }
            if (digits != raw) {
                portState.setTextAndPlaceCursorAtEnd(digits)
            }
        }
    }

    fun flushTextEdits() {
        state.updateHostPort(hostState.text.toString().trim(), portState.text.toString().toPortOrZero())
    }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_network_proxy_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            SingleChoiceSettingsItem(
                headline = {
                    Text(text = stringResource(id = R.string.settings_network_proxy_mode))
                },
                supporting = {
                    Text(text = stringResource(id = R.string.settings_network_proxy_mode_description))
                },
                items =
                    persistentMapOf(
                        AppSettings.ProxyConfig.Type.System to
                            stringResource(id = R.string.settings_network_proxy_mode_system),
                        AppSettings.ProxyConfig.Type.Direct to
                            stringResource(id = R.string.settings_network_proxy_mode_direct),
                        AppSettings.ProxyConfig.Type.Http to
                            stringResource(id = R.string.settings_network_proxy_mode_http),
                    ),
                selected = proxy.type,
                onSelected = { type ->
                    state.update(
                        proxy.copy(
                            type = type,
                            host = hostState.text.toString().trim(),
                            port = portState.text.toString().toPortOrZero(),
                        ),
                    )
                },
                shapes = if (isHttp) ListItemDefaults.first() else ListItemDefaults.single(),
            )
            if (isHttp) {
                OutlinedTextField(
                    state = hostState,
                    label = {
                        Text(text = stringResource(id = R.string.settings_network_proxy_host))
                    },
                    supportingText = {
                        Text(text = stringResource(id = R.string.settings_network_proxy_host_description))
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && seeded) {
                                    flushTextEdits()
                                }
                            },
                )
                OutlinedTextField(
                    state = portState,
                    label = {
                        Text(text = stringResource(id = R.string.settings_network_proxy_port))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier =
                        Modifier.onFocusChanged { focusState ->
                            if (!focusState.isFocused && seeded) {
                                flushTextEdits()
                            }
                        },
                )
                if (!localNetworkPermissionGranted) {
                    LocalNetworkPermissionItem(
                        onRequest = { localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION) },
                    )
                }
                ProxyTestItem(
                    state = state,
                    canTest = localNetworkPermissionGranted,
                    onRequestPermission = {
                        localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                    },
                    onTest = {
                        state.test(
                            hostState.text.toString().trim(),
                            portState.text.toString().toPortOrZero(),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalNetworkPermissionItem(onRequest: () -> Unit) {
    SegmentedListItem(
        onClick = onRequest,
        shapes = ListItemDefaults.last(),
        content = {
            Text(text = stringResource(id = R.string.settings_network_proxy_local_network_permission))
        },
        supportingContent = {
            Text(
                text =
                    stringResource(
                        id = R.string.settings_network_proxy_local_network_permission_description,
                    ),
            )
        },
        trailingContent = {
            Button(onClick = onRequest) {
                Text(
                    text =
                        stringResource(
                            id = R.string.settings_network_proxy_local_network_permission_action,
                        ),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProxyTestItem(
    state: ProxySettingsPresenter.State,
    canTest: Boolean,
    onRequestPermission: () -> Unit,
    onTest: () -> Unit,
) {
    val onClick = {
        if (canTest) {
            onTest()
        } else {
            onRequestPermission()
        }
    }
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.last(),
        enabled = !state.isTesting,
        content = {
            Text(text = stringResource(id = R.string.settings_network_proxy_test))
        },
        supportingContent = {
            Text(
                text =
                    if (!canTest) {
                        stringResource(
                            id = R.string.settings_network_proxy_local_network_permission_required,
                        )
                    } else {
                        testResultText(state)
                    },
            )
        },
        trailingContent = {
            Button(
                onClick = onClick,
                enabled = !state.isTesting,
            ) {
                Text(text = stringResource(id = R.string.settings_network_proxy_test_action))
            }
        },
    )
}

@Composable
private fun testResultText(state: ProxySettingsPresenter.State): String =
    when (val result = state.testResult) {
        null -> {
            stringResource(id = R.string.settings_network_proxy_test_description)
        }

        is ProxySettingsPresenter.TestResult.Connected -> {
            stringResource(id = R.string.settings_network_proxy_test_success, result.status)
        }

        is ProxySettingsPresenter.TestResult.HttpError -> {
            stringResource(id = R.string.settings_network_proxy_test_http_error, result.status)
        }

        ProxySettingsPresenter.TestResult.ProxyNotUsed -> {
            stringResource(id = R.string.settings_network_proxy_test_not_used)
        }

        is ProxySettingsPresenter.TestResult.Failed -> {
            stringResource(id = R.string.settings_network_proxy_test_failed, result.message)
        }
    }
