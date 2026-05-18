package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Stable

@Stable
public interface ExportState {
    public suspend fun export(): String
}

@Stable
public interface ImportState {
    public suspend fun import()
}
