package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.res
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class EditTabPresenter(
    private val tabItem: TimelineTabItemV2,
) : PresenterBase<EditTabPresenter.State>(),
    KoinComponent {
    private val timelineResolver: TimelineResolver by inject()

    public interface State {
        public val availableIcons: ImmutableList<IconType>
        public val initialText: UiState<String>
        public val icon: IconType
        public val withAvatar: Boolean
        public val canUseAvatar: Boolean

        public fun setWithAvatar(value: Boolean)

        public fun setIcon(value: IconType)
    }

    @Composable
    override fun body(): State {
        var initialText: UiState<String> by remember {
            mutableStateOf(UiState.Loading())
        }
        var icon: IconType by remember(tabItem) {
            mutableStateOf(tabItem.icon)
        }
        var withAvatar by remember(tabItem) {
            mutableStateOf(tabItem.icon is IconType.Mixed)
        }
        val avatarAccountKey =
            remember(tabItem) {
                timelineResolver.resolveAccountKey(tabItem)
            }

        LaunchedEffect(tabItem) {
            initialText =
                UiState.Success(
                    when (val title = tabItem.title) {
                        is UiText.Localized -> getString(title.string.res)
                        is UiText.Raw -> title.string
                    },
                )
        }

        return object : State {
            override val initialText: UiState<String> = initialText
            override val withAvatar: Boolean = withAvatar
            override val canUseAvatar: Boolean = avatarAccountKey != null
            override val availableIcons: ImmutableList<IconType> =
                (
                    avatarAccountKey
                        ?.let {
                            listOf(
                                IconType.Avatar(it),
                                IconType.FavIcon(it.host),
                            )
                        }.orEmpty() +
                        UiIcon.entries.map {
                            IconType.Material(it)
                        } +
                        listOfNotNull(tabItem.icon as? IconType.Url)
                ).distinct()
                    .toPersistentList()
            override val icon = icon

            override fun setWithAvatar(value: Boolean) {
                if (avatarAccountKey == null) return
                withAvatar = value
                setIcon(icon)
            }

            override fun setIcon(value: IconType) {
                icon =
                    if (withAvatar && avatarAccountKey != null) {
                        when (value) {
                            is IconType.Avatar -> value
                            is IconType.Material -> IconType.Mixed(value.icon, avatarAccountKey)
                            is IconType.Mixed -> IconType.Mixed(value.icon, avatarAccountKey)
                            is IconType.Url -> value
                            is IconType.FavIcon -> value
                        }
                    } else {
                        when (value) {
                            is IconType.Avatar -> value
                            is IconType.Material -> value
                            is IconType.Mixed -> IconType.Material(value.icon)
                            is IconType.Url -> value
                            is IconType.FavIcon -> value
                        }
                    }
            }
        }
    }
}

private fun IconType.accountKeyOrNull(): MicroBlogKey? =
    when (this) {
        is IconType.Avatar -> accountKey
        is IconType.Mixed -> accountKey
        is IconType.FavIcon,
        is IconType.Material,
        is IconType.Url,
        -> null
    }
