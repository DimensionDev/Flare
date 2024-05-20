package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class VVOLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<VVOLoginState>() {
    @Composable
    override fun body(): VVOLoginState {
        val accountRepository: AccountRepository = koinInject()
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        val scope = rememberCoroutineScope()

        return object : VVOLoginState {
            override val loading = loading
            override val error = error

            override fun checkChocolate(cookie: String): Boolean {
                return VVOService.checkChocolates(cookie)
            }

            override fun login(chocolate: String) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        vvoLoginUseCase(
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

    private suspend fun vvoLoginUseCase(
        chocolate: String,
        accountRepository: AccountRepository,
    ) {
        val service = VVOService(chocolate)
        val config = service.config()
        val uid = config.data?.uid
        requireNotNull(uid) { "uid is null" }
        val profile = service.profileInfo(uid)
        requireNotNull(profile.data) { "profile is null" }
        accountRepository.addAccount(
            UiAccount.VVo(
                accountKey =
                    MicroBlogKey(
                        id = uid,
                        host = vvoHost,
                    ),
                credential =
                    UiAccount.VVo.Credential(
                        chocolate = chocolate,
                    ),
            ),
        )
    }
}

interface VVOLoginState {
    val loading: Boolean
    val error: Throwable?

    fun checkChocolate(cookie: String): Boolean

    fun login(chocolate: String)
}
