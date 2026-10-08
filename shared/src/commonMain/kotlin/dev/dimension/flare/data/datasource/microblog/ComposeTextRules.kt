package dev.dimension.flare.data.datasource.microblog

import de.cketti.codepoints.codePointCount
import dev.dimension.flare.common.graphemeCount

public object ComposeTextRules {
    public fun bluesky(): ComposeConfig.Text =
        ComposeConfig.Text.withValidation(300) { content, _ ->
            val remaining = 300 - content.graphemeCount()
            ComposeConfig.Text.Check(
                remaining,
                when {
                    remaining < 0 -> "Bluesky posts must be at most 300 characters."
                    content.encodeToByteArray().size > 3000 -> "Bluesky posts must be at most 3000 UTF-8 bytes."
                    else -> null
                },
            )
        }

    public fun misskeyCheck(
        content: String,
        spoilerText: String?,
        maxLength: Int,
    ): ComposeConfig.Text.Check =
        ComposeConfig.Text.Check(
            maxLength - content.codePointCount(),
            when {
                content.codePointCount() > maxLength -> "Misskey posts exceed the instance character limit."
                spoilerText.orEmpty().codePointCount() > 100 -> "Misskey content warnings must be at most 100 characters."
                else -> null
            },
        )
}
