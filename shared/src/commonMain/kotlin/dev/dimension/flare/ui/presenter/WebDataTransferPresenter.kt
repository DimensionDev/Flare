package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.coroutines.launch

@WebPresenter("dataTransfer")
public class WebDataTransferPresenter(
    private val onExported: (String) -> Unit,
    private val onImported: () -> Unit,
    private val onError: (String) -> Unit,
) : PresenterBase<WebDataTransferState>() {
    private val exportDataPresenter = ExportDataPresenter()

    @Composable
    override fun body(): WebDataTransferState {
        val scope = rememberCoroutineScope()
        return object : WebDataTransferState {
            override fun exportData() {
                scope.launch {
                    runCatching {
                        exportDataPresenter.export()
                    }.onSuccess(onExported)
                        .onFailure { throwable ->
                            onError(throwable.message ?: throwable::class.simpleName ?: "Export failed")
                        }
                }
            }

            override fun importData(jsonContent: String) {
                scope.launch {
                    runCatching {
                        ImportDataPresenter(jsonContent).import()
                    }.onSuccess {
                        onImported()
                    }.onFailure { throwable ->
                        onError(throwable.message ?: throwable::class.simpleName ?: "Import failed")
                    }
                }
            }
        }
    }
}

@Stable
public interface WebDataTransferState {
    public fun exportData()

    public fun importData(jsonContent: String)
}
