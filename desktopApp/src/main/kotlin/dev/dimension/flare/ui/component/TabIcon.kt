package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.component.Text
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.res
import dev.dimension.flare.data.model.toIcon
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
fun TabTitle(
    title: TitleType,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            when (title) {
                is TitleType.Localized -> stringResource(title.res)
                is TitleType.Text -> title.content
            },
        modifier = modifier,
    )
}

@Composable
fun TabIcon(
    tabItem: TabItem,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
) {
    val accountType = tabItem.account
    val icon = tabItem.metaData.icon
    val title = tabItem.metaData.title
    when (icon) {
        is IconType.Avatar -> {
            val userState by producePresenter(key = "$accountType:${icon.userKey}") {
                remember(accountType, icon.userKey) {
                    UserPresenter(
                        accountType,
                        icon.userKey,
                    )
                }.invoke()
            }
            userState.user
                .onSuccess {
                    AvatarComponent(it.avatar, size = 24.dp, modifier = modifier)
                }.onLoading {
                    AvatarComponent(null, size = 24.dp, modifier = modifier.placeholder(true))
                }
        }

        is IconType.Material -> {
            FAIcon(
                imageVector = icon.icon.toIcon(),
                contentDescription =
                    when (title) {
                        is TitleType.Localized -> stringResource(title.res)
                        is TitleType.Text -> title.content
                    },
                modifier =
                    modifier
                        .size(24.dp),
            )
        }

        is IconType.Mixed -> {
            if (iconOnly) {
                FAIcon(
                    imageVector = icon.icon.toIcon(),
                    contentDescription =
                        when (title) {
                            is TitleType.Localized -> stringResource(title.res)
                            is TitleType.Text -> title.content
                        },
                    modifier =
                        modifier
                            .size(24.dp),
                )
            } else {
                val userState by producePresenter(key = "$accountType:${icon.userKey}") {
                    remember(accountType, icon.userKey) {
                        UserPresenter(
                            accountType,
                            icon.userKey,
                        )
                    }.invoke()
                }
                Box(
                    modifier = modifier,
                ) {
                    userState.user
                        .onSuccess {
                            AvatarComponent(it.avatar, size = 24.dp)
                        }.onLoading {
                            AvatarComponent(
                                null,
                                size = 24.dp,
                                modifier = Modifier.placeholder(true),
                            )
                        }
                    FAIcon(
                        imageVector = icon.icon.toIcon(),
                        contentDescription =
                            when (title) {
                                is TitleType.Localized -> stringResource(title.res)
                                is TitleType.Text -> title.content
                            },
                        modifier =
                            Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .background(FluentTheme.colors.background.solid.base, shape = CircleShape)
                                .padding(2.dp),
                    )
                }
            }
        }
    }
}
