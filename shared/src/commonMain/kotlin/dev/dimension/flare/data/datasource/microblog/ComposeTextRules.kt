package dev.dimension.flare.data.datasource.microblog

import de.cketti.codepoints.codePointCount
import dev.dimension.flare.common.graphemeCount

public object ComposeTextRules {
    public fun bluesky(): ComposeConfig.Text =
        ComposeConfig.Text.withValidation(300) { content, _ ->
            val remaining = 300 - content.graphemeCount()
            ComposeConfig.Text.Check(
                remainingLength = remaining,
                isValid = remaining >= 0 && content.encodeToByteArray().size <= 3000,
            )
        }

    public fun misskeyCheck(
        content: String,
        spoilerText: String?,
        maxLength: Int,
    ): ComposeConfig.Text.Check {
        val remaining = maxLength - content.codePointCount()
        return ComposeConfig.Text.Check(
            remainingLength = remaining,
            isValid = remaining >= 0 && spoilerText.orEmpty().codePointCount() <= 100,
        )
    }
}
