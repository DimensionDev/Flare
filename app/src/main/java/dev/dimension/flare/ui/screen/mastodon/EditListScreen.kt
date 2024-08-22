package dev.dimension.flare.ui.screen.mastodon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper

@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun EditListRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    listId: String,
) {
    EditListScreen(accountType, listId, onBack = navigator::navigateUp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditListScreen(
    accountType: AccountType,
    listId: String,
    onBack: () -> Unit,
) {
    val state by producePresenter {
        presenter(accountType, listId)
    }
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.list_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) {
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    listId: String,
) = run {
}
