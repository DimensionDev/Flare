package dev.dimension.flare.data.datasource.microblog

public fun StatusMutation.nextActionMenu(): ActionMenu.Item? {
    val toggled = this.toggled
    val count = this.count
    val nextToggled = !toggled
    val nextCount = (count + if (nextToggled) 1 else -1).coerceAtLeast(0)
    val nextParams = params.toMutableMap().apply {
        put(StatusMutation.PARAM_TOGGLED, nextToggled.toString())
        put(StatusMutation.PARAM_COUNT, nextCount.toString())
    }
    val nextMutation = copy(params = nextParams)
    return when (type) {
        StatusMutation.TYPE_LIKE ->
            ActionMenu.like(
                statusKey = statusKey,
                accountKey = accountKey,
                toggled = nextToggled,
                count = nextCount,
                extras = nextMutation.params - StatusMutation.PARAM_TOGGLED - StatusMutation.PARAM_COUNT,
            )

        StatusMutation.TYPE_REPOST ->
            ActionMenu.repost(
                statusKey = statusKey,
                accountKey = accountKey,
                toggled = nextToggled,
                count = nextCount,
                extras = nextMutation.params - StatusMutation.PARAM_TOGGLED - StatusMutation.PARAM_COUNT,
            )

        StatusMutation.TYPE_BOOKMARK ->
            ActionMenu.bookmark(
                statusKey = statusKey,
                accountKey = accountKey,
                toggled = nextToggled,
                count = nextCount,
                extras = nextMutation.params - StatusMutation.PARAM_TOGGLED - StatusMutation.PARAM_COUNT,
            )

        StatusMutation.TYPE_REACT ->
            ActionMenu.react(
                statusKey = statusKey,
                accountKey = accountKey,
                toggled = nextToggled,
                count = nextCount,
                clickEvent = dev.dimension.flare.ui.model.ClickEvent.mutation(nextMutation),
            )

        StatusMutation.TYPE_FAVOURITE ->
            ActionMenu.favourite(
                statusKey = statusKey,
                accountKey = accountKey,
                toggled = nextToggled,
                extras = nextMutation.params - StatusMutation.PARAM_TOGGLED - StatusMutation.PARAM_COUNT,
            )

        StatusMutation.TYPE_LIKE_COMMENT ->
            ActionMenu.likeComment(
                statusKey = statusKey,
                accountKey = accountKey,
                toggled = nextToggled,
                count = nextCount,
                extras = nextMutation.params - StatusMutation.PARAM_TOGGLED - StatusMutation.PARAM_COUNT,
            )

        else -> null
    }
}
