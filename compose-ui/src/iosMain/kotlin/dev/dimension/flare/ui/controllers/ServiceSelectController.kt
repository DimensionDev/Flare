package dev.dimension.flare.ui.controllers

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.ui.screen.login.ServiceSelectionScreenContent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

public data class ServiceSelectControllerState(
    val onXQT: () -> Unit,
    val onVVO: () -> Unit,
    val onBack: (() -> Unit),
    val openUri: (url: String, callback: (String) -> Unit) -> Unit,
)

@Suppress("FunctionName")
public fun ServiceSelectController(state: ComposeUIStateProxy<ServiceSelectControllerState>): UIViewController =
    FlareComposeUIViewController(state) { state ->
        val kotlinCallback = remember { MutableSharedFlow<String>() }
        val scope = rememberCoroutineScope()
        ServiceSelectionScreenContent(
            contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            onXQT = state.onXQT,
            onVVO = state.onVVO,
            openUri = {
                state.openUri.invoke(
                    it,
                ) {
                    scope.launch {
                        kotlinCallback.emit(it)
                    }
                }
            },
            onBack = state.onBack,
            registerDeeplinkCallback = { callback ->
                LaunchedEffect(callback) {
                    kotlinCallback.collect {
                        callback.invoke(it)
                    }
                }
            },
        )
    }
