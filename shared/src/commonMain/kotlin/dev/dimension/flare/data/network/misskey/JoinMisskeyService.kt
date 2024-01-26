package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.misskey.api.MisskeyInstanceAppApi

internal object JoinMisskeyService :
    MisskeyInstanceAppApi by ktorfit("https://instanceapp.misskey.page/").create()
