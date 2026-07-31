package dev.dimension.flare.ui.screen.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.R
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.ChangeLogPresenter
import dev.dimension.flare.ui.presenter.invoke
import org.koin.compose.koinInject

@Composable
internal fun changeLogPresenter(context: Context = koinInject()): ChangeLogState {
    val state =
        remember {
            ChangeLogPresenter(currentVersion = BuildConfig.VERSION_NAME)
        }.invoke()
    val changeLog =
        remember(BuildConfig.VERSION_NAME) {
            runCatching {
                context.getString(R.string.changelog_current, BuildConfig.VERSION_NAME)
            }.getOrNull()
                ?.takeIf {
                    it.isNotBlank() && it.isNotEmpty()
                }?.let {
                    AnnotatedString.fromHtml(it)
                }
        }
    return object : ChangeLogState {
        override val shouldShowChangeLog: UiState<Boolean> = state.shouldShowChangeLog
        override val changeLog: AnnotatedString? = changeLog

        override fun dismissChangeLog() = state.dismissChangeLog()
    }
}

@Immutable
interface ChangeLogState {
    val shouldShowChangeLog: UiState<Boolean>
    val changeLog: AnnotatedString?

    fun dismissChangeLog()
}
