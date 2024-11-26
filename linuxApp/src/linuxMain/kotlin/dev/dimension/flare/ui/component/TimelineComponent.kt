package dev.dimension.flare.ui.component

import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import org.gtkkn.bindings.gtk.Align
import org.gtkkn.bindings.gtk.Box
import org.gtkkn.bindings.gtk.Image
import org.gtkkn.bindings.gtk.Label
import org.gtkkn.bindings.gtk.Orientation
import org.gtkkn.bindings.gtk.Widget

internal fun timelineItem(data: UiTimeline): Widget =
    Box(Orientation.VERTICAL, 0).apply {
        data.topMessage?.let {
            append(topMessageComponent(it))
        }
        when (val content = data.content) {
            is UiTimeline.ItemContent.Status -> append(statusComponent(content))
            is UiTimeline.ItemContent.User -> Unit
            is UiTimeline.ItemContent.UserList -> Unit
            null -> Unit
        }
    }

private fun statusComponent(data: UiTimeline.ItemContent.Status): Widget =
    Box(Orientation.VERTICAL, 0).apply {
        data.user?.let {
            append(userItemComponent(it))
        }
        when (val content = data.aboveTextContent) {
            is UiTimeline.ItemContent.Status.AboveTextContent.ReplyTo -> {
                append(Label("Reply to ${content.handle}"))
            }
            null -> Unit
        }
        data.contentWarning?.let {
            append(Label(it))
        }
        append(
            Label().apply {
                setMarkup(data.content.html)
                halign = Align.START
                hexpand = true
            },
        )
    }

private fun userItemComponent(data: UiUserV2): Widget =
    Box(Orientation.HORIZONTAL, 0).apply {
        append(
            Image(data.avatar).apply {
                setSizeRequest(48, 48)
            },
        )
        append(
            Box(Orientation.VERTICAL, 0).apply {
                append(
                    Label().apply {
                        setMarkup(data.name.html)
                        halign = Align.START
                        hexpand = true
                    },
                )
                append(
                    Label(data.handle).apply {
                        halign = Align.START
                        hexpand = true
                    },
                )
            },
        )
    }

private fun topMessageComponent(data: UiTimeline.TopMessage): Widget =
    Box(Orientation.HORIZONTAL, 0).apply {
        valign = Align.START
        data.user?.let {
            append(
                Label().apply {
                    setMarkup(it.name.html)
                },
            )
        }
        append(Label(data.type.toString()))
    }
