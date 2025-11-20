package dev.dimension.flare.ui.screen.rss

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.ImportOPMLPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OPMLImportSheet(
    uri: Uri,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by producePresenter("import_rss_$uri") {
        presenter(uri, context)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProvideTextStyle(
            MaterialTheme.typography.titleMedium,
        ) {
            Text(
                stringResource(R.string.opml_import),
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            )
        }
        state
            .onLoading {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                    )
                }
            }.onError {
                Text(text = it.message ?: "Unknown error")
            }.onSuccess { state ->
                Column {
                    ImportOPMLContent(
                        state = state,
                        onGoBack = onBack,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            onBack.invoke()
                        },
                        enabled = !state.importing,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = screenHorizontalPadding,
                                    vertical = 8.dp,
                                ),
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
    }
}

@Composable
private fun presenter(
    uri: Uri,
    context: Context,
) = run {
    var contentState by remember { mutableStateOf<UiState<String>>(UiState.Loading()) }

    LaunchedEffect(uri, context) {
        contentState =
            try {
                val content =
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }
                    }
                if (content.isNullOrEmpty()) {
                    UiState.Error(Exception("Empty content"))
                } else {
                    UiState.Success(content)
                }
            } catch (e: Exception) {
                UiState.Error(e)
            }
    }

    contentState.map {
        remember(it) { ImportOPMLPresenter(it) }.body()
    }
}
