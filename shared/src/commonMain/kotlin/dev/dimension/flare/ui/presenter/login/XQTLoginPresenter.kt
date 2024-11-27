package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datasource.xqt.userByScreenName
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class XQTLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<XQTLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

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
                            accountRepository = accountRepository,
                        )
                        toHome.invoke()
                    }.onFailure {
                        it.printStackTrace()
                        error = it
                    }
                    loading = false
                }
            }
        }
    }

    private suspend fun xqtLoginUseCase(
        chocolate: String,
        accountRepository: AccountRepository,
    ) {
        val xqtService = XQTService(chocolate)
        val accountSettings = xqtService.getAccountSettings()
        requireNotNull(accountSettings.screenName)
        val account =
            xqtService
                .userByScreenName(accountSettings.screenName)
                .body()
                ?.data
                ?.user
                ?.result
        requireNotNull(account)
        require(account is User)
        accountRepository.addAccount(
            UiAccount.XQT(
                credential =
                    UiAccount.XQT.Credential(
                        chocolate = chocolate,
                    ),
                accountKey =
                    MicroBlogKey(
                        id = account.restId,
                        host = xqtHost,
                    ),
            ),
        )
    }
}

@Immutable
interface XQTLoginState {
    val loading: Boolean
    val error: Throwable?

    fun checkChocolate(cookie: String): Boolean

    fun login(chocolate: String)
}
