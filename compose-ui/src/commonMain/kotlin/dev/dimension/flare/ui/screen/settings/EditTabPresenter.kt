package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.res
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.compose.resources.getString

public class EditTabPresenter(
    private val tabItem: TabItem,
) : PresenterBase<EditTabPresenter.State>() {
    public interface State {
        public val availableIcons: ImmutableList<IconType>
        public val initialText: UiState<String>
        public val icon: IconType
        public val withAvatar: Boolean

        public fun setWithAvatar(value: Boolean)

        public fun setIcon(value: IconType)
    }

    @Composable
    override fun body(): State {
        var initialText: UiState<String> by remember {
            mutableStateOf(UiState.Loading())
        }
        var icon: IconType by remember {
            mutableStateOf(tabItem.metaData.icon)
        }
        var withAvatar by remember {
            mutableStateOf(tabItem.metaData.icon is IconType.Mixed)
        }
        LaunchedEffect(Unit) {
            val value =
                when (val title = tabItem.metaData.title) {
                    is TitleType.Localized -> getString(title.res)
                    is TitleType.Text -> title.content
                }
            initialText = UiState.Success(value)
        }
        return object : State {
            override val initialText: UiState<String> = initialText
            override val withAvatar: Boolean = withAvatar
            override val availableIcons: ImmutableList<IconType> =
                kotlin
                    .run {
                        when (val account = tabItem.account) {
                            is AccountType.Specific ->
                                listOf(
                                    IconType.Avatar(account.accountKey),
                                    IconType.Url(
                                        UiRssSource.favIconUrl(account.accountKey.host),
                                    ),
                                )

                            else -> emptyList()
                        } +
                            IconType.Material.MaterialIcon.entries.map {
                                IconType.Material(it)
                            } +
                            if (tabItem is RssTimelineTabItem) {
                                listOfNotNull(
                                    tabItem.favIcon?.let { IconType.Url(it) },
                                )
                            } else {
                                emptyList()
                            }
                    }.let {
                        it.toPersistentList()
                    }
            override val icon = icon

            override fun setWithAvatar(value: Boolean) {
                withAvatar = value
                setIcon(icon)
            }

            override fun setIcon(value: IconType) {
                val account = tabItem.account
                icon =
                    if (withAvatar && account is AccountType.Specific) {
                        when (value) {
                            is IconType.Avatar -> value
                            is IconType.Material ->
                                IconType.Mixed(value.icon, account.accountKey)

                            is IconType.Mixed ->
                                IconType.Mixed(value.icon, account.accountKey)

                            is IconType.Url -> value
                        }
                    } else {
                        when (value) {
                            is IconType.Avatar -> value
                            is IconType.Material -> value
                            is IconType.Mixed -> IconType.Material(value.icon)
                            is IconType.Url -> value
                        }
                    }
            }
        }
    }
}
