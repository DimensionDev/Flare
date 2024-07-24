package dev.dimension.flare.ui.render

import moe.tlaster.ktml.dom.Element

expect class UiRichText {
    val raw: String
}

expect fun Element.toUi(): UiRichText
