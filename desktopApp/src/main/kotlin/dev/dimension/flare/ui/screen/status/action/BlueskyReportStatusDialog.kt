package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.konyaco.fluent.component.ContentDialog
import com.konyaco.fluent.component.ContentDialogButton
import com.konyaco.fluent.component.ListItem
import com.konyaco.fluent.component.RadioButton
import com.konyaco.fluent.component.Text
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ok
import dev.dimension.flare.report_description
import dev.dimension.flare.report_reason_misleading_description
import dev.dimension.flare.report_reason_misleading_title
import dev.dimension.flare.report_reason_other_description
import dev.dimension.flare.report_reason_other_title
import dev.dimension.flare.report_reason_rude_description
import dev.dimension.flare.report_reason_rude_title
import dev.dimension.flare.report_reason_sexual_description
import dev.dimension.flare.report_reason_sexual_title
import dev.dimension.flare.report_reason_spam_description
import dev.dimension.flare.report_reason_spam_title
import dev.dimension.flare.report_reason_violation_description
import dev.dimension.flare.report_reason_violation_title
import dev.dimension.flare.report_title
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusPresenter
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusState
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BlueskyReportStatusDialog(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "BlueskyReportStatusPresenter_${accountType}_$statusKey") {
        blueskyReportStatusPresenter(
            statusKey = statusKey,
            accountType = accountType,
        )
    }

    ContentDialog(
        title = stringResource(Res.string.report_title),
        visible = true,
        content = {
            Column {
                Text(text = stringResource(Res.string.report_description))
                state.allReasons.forEach {
                    val interactionSource =
                        remember(it) {
                            MutableInteractionSource()
                        }
                    ListItem(
                        text = {
                            Text(text = stringResource(it.stringRes))
                        },
                        onClick = {
                            state.selectReason(it)
                        },
                        selectionIcon = {
                            RadioButton(
                                selected = state.reason == it,
                                interactionSource = interactionSource,
                                onClick = {
                                    state.selectReason(it)
                                },
                            )
                        },
//                        supportingContent = {
//                            Text(text = stringResource(id = it.descriptionRes))
//                        },
//                        leadingContent = {
//                            RadioButton(
//                                selected = state.reason == it,
//                                interactionSource = interactionSource,
//                                onClick = {
//                                    state.selectReason(it)
//                                },
//                            )
//                        },
                    )
                }
            }
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary ->
                    state.status.onSuccess { status ->
                        state.reason?.let {
                            state.report(it, status)
                            onBack.invoke()
                        }
                    }
                ContentDialogButton.Secondary -> Unit
                ContentDialogButton.Close -> onBack.invoke()
            }
        },
    )
}

@Composable
private fun blueskyReportStatusPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val state =
        remember(statusKey, accountType) {
            BlueskyReportStatusPresenter(
                accountType = accountType,
                statusKey = statusKey,
            )
        }.invoke()

    object : BlueskyReportStatusState by state {
    }
}

private val BlueskyReportStatusState.ReportReason.stringRes: StringResource
    get() =
        when (this) {
            BlueskyReportStatusState.ReportReason.Spam -> Res.string.report_reason_spam_title
            BlueskyReportStatusState.ReportReason.Violation -> Res.string.report_reason_violation_title
            BlueskyReportStatusState.ReportReason.Misleading -> Res.string.report_reason_misleading_title
            BlueskyReportStatusState.ReportReason.Sexual -> Res.string.report_reason_sexual_title
            BlueskyReportStatusState.ReportReason.Rude -> Res.string.report_reason_rude_title
            BlueskyReportStatusState.ReportReason.Other -> Res.string.report_reason_other_title
        }

private val BlueskyReportStatusState.ReportReason.descriptionRes: StringResource
    get() =
        when (this) {
            BlueskyReportStatusState.ReportReason.Spam -> Res.string.report_reason_spam_description
            BlueskyReportStatusState.ReportReason.Violation -> Res.string.report_reason_violation_description
            BlueskyReportStatusState.ReportReason.Misleading -> Res.string.report_reason_misleading_description
            BlueskyReportStatusState.ReportReason.Sexual -> Res.string.report_reason_sexual_description
            BlueskyReportStatusState.ReportReason.Rude -> Res.string.report_reason_rude_description
            BlueskyReportStatusState.ReportReason.Other -> Res.string.report_reason_other_description
        }
