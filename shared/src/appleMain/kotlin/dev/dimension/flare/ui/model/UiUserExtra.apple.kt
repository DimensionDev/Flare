package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import dev.dimension.flare.ui.render.toMarkdown
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
actual class UiUserExtra(
    val nameMarkdown: String,
    val descriptionMarkdown: String?,
    val fieldsMarkdown: ImmutableListWrapper<Pair<String, String>>,
)

internal actual fun createUiUserExtra(user: UiUser): UiUserExtra =
    UiUserExtra(
        nameMarkdown = user.nameElement.toMarkdown(),
        descriptionMarkdown = user.descriptionElement?.toMarkdown(),
        fieldsMarkdown =
            when (user) {
                is UiUser.Mastodon ->
                    user.fieldsParsed.mapValues { (_, value) ->
                        value.toMarkdown()
                    }
                is UiUser.Misskey ->
                    user.fieldsParsed.mapValues { (_, value) ->
                        value.toMarkdown()
                    }
                is UiUser.Bluesky -> persistentMapOf()
                is UiUser.XQT ->
                    user.fieldsParsed.mapValues { (_, value) ->
                        value.toMarkdown()
                    }
                is UiUser.VVO -> persistentMapOf()
            }.map { (key, value) ->
                key to value
            }.toImmutableList()
                .toImmutableListWrapper(),
    )
