package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.data.repository.SubscriptionRepository
import dev.dimension.flare.ui.presenter.home.rss.FakeSubscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
internal class SubscriptionTestKoinModule {
    @Single
    fun subscriptionRepository(): SubscriptionRepository = FakeSubscriptionRepository()

    @Single
    fun coroutineScope(): CoroutineScope = TestScope()

    @Single
    fun inAppNotification(): InAppNotification = TestInAppNotification
}

private object TestInAppNotification : InAppNotification {
    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) = Unit

    override fun onSuccess(message: Message) = Unit

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) = Unit
}
