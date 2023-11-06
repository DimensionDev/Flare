package dev.dimension.flare.di

import dev.dimension.flare.data.repository.ComposeUseCase
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.bluesky.BlueskyStatusEvent
import dev.dimension.flare.ui.component.status.bluesky.DefaultBlueskyStatusEvent
import dev.dimension.flare.ui.component.status.mastodon.DefaultMastodonStatusEvent
import dev.dimension.flare.ui.component.status.mastodon.MastodonStatusEvent
import dev.dimension.flare.ui.component.status.misskey.DefaultMisskeyStatusEvent
import dev.dimension.flare.ui.component.status.misskey.MisskeyStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.binds
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

val androidModule =
    module {
        singleOf(::DefaultBlueskyStatusEvent) withOptions {
            binds(listOf(BlueskyStatusEvent::class))
        }
        singleOf(::DefaultMisskeyStatusEvent) withOptions {
            binds(listOf(MisskeyStatusEvent::class))
        }
        singleOf(::DefaultMastodonStatusEvent) withOptions {
            binds(listOf(MastodonStatusEvent::class))
        }
        single { CoroutineScope(Dispatchers.IO) }
        singleOf(::ComposeUseCase)
        singleOf(::StatusEvent)
    }
