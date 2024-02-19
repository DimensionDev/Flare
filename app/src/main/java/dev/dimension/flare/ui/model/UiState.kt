package dev.dimension.flare.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow

@Composable
internal fun <T : Any> Flow<T>.collectAsUiState(initial: UiState<T> = UiState.Loading()): State<UiState<T>> =
    remember(this) { toUiState() }.collectAsState(initial)
