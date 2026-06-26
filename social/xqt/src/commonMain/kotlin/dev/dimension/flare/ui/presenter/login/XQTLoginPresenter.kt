package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datasource.xqt.userById
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.platform.XQTCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import dev.dimension.flare.di.koinInject

internal class XQTLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<XQTLoginState>() {
    private val accountService: AccountService by koinInject()

    @Composable
    override fun body(): XQTLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        val scope = rememberCoroutineScope()
        return object : XQTLoginState {
            override val loading = loading
            override val error = error

            override fun checkChocolate(cookie: String): Boolean = XQTService.checkChocolate(cookie)

            override fun login(chocolate: String) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        xqtLoginUseCase(
                            chocolate = chocolate,
                            accountService = accountService,
                        )
                        toHome.invoke()
                    }.onFailure {
                        error = it
                    }
                    loading = false
                }
            }
        }
    }

    private suspend fun xqtLoginUseCase(
        chocolate: String,
        accountService: AccountService,
    ) {
        val xqtService = XQTService(flowOf(chocolate))
        val userId = xqtService.getInitialUserId(chocolate = chocolate)
        requireNotNull(userId)
        val account =
            xqtService
                .userById(userId)
                .body()
                ?.data
                ?.user
                ?.result
        requireNotNull(account)
        require(account is User)
        accountService.addAccount(
            UiAccount(
                accountKey =
                    MicroBlogKey(
                        id = account.restId,
                        host = xqtHost,
                    ),
                platformType = PlatformType.xQt,
            ),
            credential =
                XQTCredential(
                    chocolate = chocolate,
                ),
        )
    }
}

@Immutable
internal interface XQTLoginState {
    public val loading: Boolean
    public val error: Throwable?

    public fun checkChocolate(cookie: String): Boolean

    public fun login(chocolate: String)
}
