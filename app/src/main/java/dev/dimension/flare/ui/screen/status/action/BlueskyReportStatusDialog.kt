package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.DestinationStyle
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusPresenter
import dev.dimension.flare.ui.presenter.status.action.BlueskyReportStatusState

@Composable
@Destination(
    style = DestinationStyle.Dialog::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
fun BlueskyReportStatusRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    statusKey: MicroBlogKey,
) {
    BlueskyReportStatusDialog(
        statusKey = statusKey,
        accountType = accountType,
        onBack = {
            navigator.navigateUp()
        },
    )
}

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

    AlertDialog(
        onDismissRequest = onBack,
        confirmButton = {
            TextButton(
                enabled = state.reason != null,
                onClick = {
                    state.status.onSuccess { status ->
                        state.reason?.let {
                            state.report(it, status)
                            onBack.invoke()
                        }
                    }
                },
            ) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        title = {
            Text(text = stringResource(id = R.string.report_title))
        },
        text = {
            Column {
                Text(text = stringResource(id = R.string.report_description))
                state.allReasons.forEach {
                    val interactionSource =
                        remember(it) {
                            MutableInteractionSource()
                        }
                    ListItem(
                        modifier =
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    state.selectReason(it)
                                },
                            ),
                        headlineContent = {
                            Text(text = stringResource(id = it.stringRes))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = it.descriptionRes))
                        },
                        leadingContent = {
                            RadioButton(
                                selected = state.reason == it,
                                interactionSource = interactionSource,
                                onClick = {
                                    state.selectReason(it)
                                },
                            )
                        },
                    )
                }
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

private val BlueskyReportStatusState.ReportReason.stringRes: Int
    get() =
        when (this) {
            BlueskyReportStatusState.ReportReason.Spam -> R.string.report_reason_spam_title
            BlueskyReportStatusState.ReportReason.Violation -> R.string.report_reason_violation_title
            BlueskyReportStatusState.ReportReason.Misleading -> R.string.report_reason_misleading_title
            BlueskyReportStatusState.ReportReason.Sexual -> R.string.report_reason_sexual_title
            BlueskyReportStatusState.ReportReason.Rude -> R.string.report_reason_rude_title
            BlueskyReportStatusState.ReportReason.Other -> R.string.report_reason_other_title
        }

private val BlueskyReportStatusState.ReportReason.descriptionRes: Int
    get() =
        when (this) {
            BlueskyReportStatusState.ReportReason.Spam -> R.string.report_reason_spam_description
            BlueskyReportStatusState.ReportReason.Violation -> R.string.report_reason_violation_description
            BlueskyReportStatusState.ReportReason.Misleading -> R.string.report_reason_misleading_description
            BlueskyReportStatusState.ReportReason.Sexual -> R.string.report_reason_sexual_description
            BlueskyReportStatusState.ReportReason.Rude -> R.string.report_reason_rude_description
            BlueskyReportStatusState.ReportReason.Other -> R.string.report_reason_other_description
        }
