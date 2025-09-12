package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class VVOLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<VVOLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): VVOLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        val scope = rememberCoroutineScope()

        return object : VVOLoginState {
            override val loading = loading
            override val error = error

            override fun checkChocolate(cookie: String): Boolean = VVOService.checkChocolates(cookie)

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
        val service = VVOService(flowOf(chocolate))
        val config = service.config()
        val uid = config.data?.uid
        requireNotNull(uid) { "uid is null" }
        val st = config.data.st
        requireNotNull(st) { "st is null" }
        val profile = service.profileInfo(uid, st)
        requireNotNull(profile.data) { "profile is null" }
        accountRepository.addAccount(
            UiAccount.VVo(
                accountKey =
                    MicroBlogKey(
                        id = uid,
                        host = vvoHost,
                    ),
            ),
            credential =
                UiAccount.VVo.Credential(
                    chocolate = chocolate,
                ),
        )
    }
}

@Immutable
public interface VVOLoginState {
    public val loading: Boolean
    public val error: Throwable?

    public fun checkChocolate(cookie: String): Boolean

    public fun login(chocolate: String)
}
