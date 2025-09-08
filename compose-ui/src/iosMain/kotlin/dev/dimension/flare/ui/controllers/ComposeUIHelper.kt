package dev.dimension.flare.ui.controllers

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade

public object ComposeUIHelper {
    public fun initialize() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .crossfade(true)
                .build()
        }
    }
}