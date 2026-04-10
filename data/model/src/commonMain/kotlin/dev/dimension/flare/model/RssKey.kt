package dev.dimension.flare.model

public fun MicroBlogKey.Companion.fromRss(url: String): MicroBlogKey = MicroBlogKey(id = url, host = "rss")
