package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.ok
import dev.dimension.flare.opml_import
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.ImportOPMLPresenter
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
internal fun ImportOPMLScreen(
    onDismissRequest: () -> Unit,
    filePath: String,
) {
    val state by producePresenter("import_rss_$filePath") {
        presenter(filePath)
    }

    ContentDialog(
        title = stringResource(Res.string.opml_import),
        visible = true,
        primaryButtonText = stringResource(Res.string.ok),
        onButtonClick = {
            state
                .onSuccess {
                    if (!it.importing) {
                        onDismissRequest()
                    }
                }.onError {
                    onDismissRequest()
                }
        },
        content = {
            state
                .onLoading {
                    Box(
                        contentAlignment = Alignment.Center,
                    ) {
                        ProgressRing()
                    }
                }.onError {
                    Text(text = it.message ?: "Unknown error")
                }.onSuccess { state ->
                    ImportOPMLContent(
                        state = state,
                        onGoBack = onDismissRequest,
                        modifier = Modifier.height(400.dp),
                    )
                }
        },
    )
}

@Composable
private fun presenter(filePath: String): UiState<ImportOPMLPresenter.State> {
    var contentState by remember { mutableStateOf<UiState<String>>(UiState.Loading()) }

    LaunchedEffect(filePath) {
        contentState =
            try {
                val content =
                    withContext(Dispatchers.IO) {
                        File(filePath).readText()
                    }
                UiState.Success(content)
            } catch (e: Exception) {
                UiState.Error(e)
            }
    }

    return contentState.map {
        remember(it) { ImportOPMLPresenter(it) }.body()
    }
}
