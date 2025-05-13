package dev.dimension.flare.data.repository

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.flare.FlareDataSource
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import kotlinx.coroutines.flow.map

@Composable
internal fun flareDataSource(appDataStore: AppDataStore): UiState<FlareDataSource> =
    remember {
        appDataStore.flareDataStore.data.map { FlareDataSource(it.serverUrl) }
    }.collectAsUiState(UiState.Loading()).value
