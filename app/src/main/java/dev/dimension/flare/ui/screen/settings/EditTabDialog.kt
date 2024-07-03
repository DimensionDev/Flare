package dev.dimension.flare.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.OutlinedTextField2
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
    style = DestinationStyle.Dialog::class,
)
@Composable
internal fun EditTabDialogRoute(
    navigator: ProxyDestinationsNavigator,
    key: String,
) {
    EditTabDialog(
        key = key,
        onDismissRequest = navigator::navigateUp,
    )
}

@Composable
internal fun EditTabDialog(
    key: String,
    onDismissRequest: () -> Unit,
) {
    val state by producePresenter(key = "EditTabSheet_$key") {
        presenter(key = key)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                state.confirm()
                onDismissRequest.invoke()
            }) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { /*TODO*/ }) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        text = {
            OutlinedTextField2(state = state.text)
        },
    )
}

@Composable
private fun presenter(
    key: String,
    context: Context = koinInject(),
    repository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val tabSettings by repository.tabSettings.collectAsUiState()
    val tabData =
        tabSettings.map {
            it.items.find { it.key == key } ?: it.secondaryItems?.find { it.key == key } ?: error("Tab not found")
        }
    val text = rememberTextFieldState()
    tabData.onSuccess { data ->
        LaunchedEffect(Unit) {
            val value =
                when (val title = data.metaData.title) {
                    is TitleType.Localized -> context.getString(title.resId)
                    is TitleType.Text -> title.content
                }
            text.edit {
                append(value)
            }
        }
    }
    object {
        val text = text

        fun confirm() {
        }
    }
}
