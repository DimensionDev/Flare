package dev.dimension.flare.ui.screen.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.presenter.profile.BlockUserPresenter
import dev.dimension.flare.ui.presenter.profile.MuteUserPresenter
import dev.dimension.flare.ui.presenter.profile.UnblockUserPresenter
import dev.dimension.flare.ui.presenter.profile.UnmuteUserPresenter
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun BlockUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter("block_user_${accountType}_$userKey") {
        remember {
            BlockUserPresenter(accountType, userKey)
        }.body()
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(text = stringResource(id = R.string.block_user_title))
        },
        text = {
            Text(text = stringResource(id = R.string.block_user_description))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.confirm()
                    onBack.invoke()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun MuteUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter("mute_user_${accountType}_$userKey") {
        remember {
            MuteUserPresenter(accountType, userKey)
        }.body()
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(text = stringResource(id = R.string.mute_user_title))
        },
        text = {
            Text(text = stringResource(id = R.string.mute_user_description))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.confirm()
                    onBack.invoke()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun UnblockUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter("unblock_user_${accountType}_$userKey") {
        remember {
            UnblockUserPresenter(accountType, userKey)
        }.body()
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(text = stringResource(id = R.string.unblock_user_title))
        },
        text = {
            Text(text = stringResource(id = R.string.unblock_user_description))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.confirm()
                    onBack.invoke()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun UnmuteUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    val state by producePresenter("unmute_user_${accountType}_$userKey") {
        remember {
            UnmuteUserPresenter(accountType, userKey)
        }.body()
    }
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(text = stringResource(id = R.string.unmute_user_title))
        },
        text = {
            Text(text = stringResource(id = R.string.unmute_user_description))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.confirm()
                    onBack.invoke()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun ReportUserDialog(
    accountType: AccountType?,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Text(text = stringResource(id = R.string.report_user_title))
        },
        text = {
            Text(text = stringResource(id = R.string.report_user_description))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onBack.invoke()
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}
