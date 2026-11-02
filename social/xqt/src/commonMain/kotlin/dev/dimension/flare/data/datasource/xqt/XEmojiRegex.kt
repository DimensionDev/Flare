package dev.dimension.flare.data.datasource.xqt

internal fun xEmojiLengthAt(
    text: String,
    index: Int,
): Int = xEmojiRegex.matchAt(text, index)?.value?.length ?: 0
