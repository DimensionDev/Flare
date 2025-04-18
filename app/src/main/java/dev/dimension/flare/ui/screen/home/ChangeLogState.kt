package dev.dimension.flare.ui.screen.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun changeLogPresenter(
    context: Context = koinInject(),
    repository: SettingsRepository = koinInject(),
): ChangeLogState {
    val scope = rememberCoroutineScope()
    val appSettings by repository.appSettings.collectAsUiState()
    val shouldShowChangeLog =
        remember(appSettings) {
            appSettings.map {
                it.version != BuildConfig.VERSION_NAME
            }
        }
    val changeLog =
        remember(BuildConfig.VERSION_NAME) {
            runCatching {
                val id = context.resources.getIdentifier("changelog_${BuildConfig.VERSION_NAME}", "string", context.packageName)
                context.getString(id)
            }.getOrNull()?.let {
                AnnotatedString.fromHtml(it)
            }
        }
    return object : ChangeLogState {
        override val shouldShowChangeLog: UiState<Boolean> = shouldShowChangeLog
        override val changeLog: AnnotatedString? = changeLog

        override fun dismissChangeLog() {
            scope.launch {
                repository.updateAppSettings {
                    copy(version = BuildConfig.VERSION_NAME)
                }
            }
        }
    }
}

interface ChangeLogState {
    val shouldShowChangeLog: UiState<Boolean>
    val changeLog: AnnotatedString?

    fun dismissChangeLog()
}
