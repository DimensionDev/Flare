package dev.dimension.flare.ui.render

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.Element
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun Element.resolvedHref(): String? {
    val href = attr("href").trim()
    if (href.isEmpty()) return null
    return StringUtil.resolve(baseUri(), href).takeIf { it.isNotEmpty() }
}
