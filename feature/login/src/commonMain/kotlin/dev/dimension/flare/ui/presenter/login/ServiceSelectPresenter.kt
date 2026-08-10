package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.presenter.PresenterBase

public class ServiceSelectPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<ServiceSelectState>() {
    private val loginPlatformRegistry: LoginPlatformRegistry by koinInject()

    @Composable
    override fun body(): ServiceSelectState {
        val nodeInfoState = remember { NodeInfoPresenter() }.body()

        return object : ServiceSelectState, NodeInfoState by nodeInfoState {
            override val loading: Boolean = false

            override fun agreementUrl(
                platformId: String,
                host: String,
            ): String? = loginPlatformRegistry.require(platformId).agreementUrl(host)

            override fun createLoginHandler(
                platformId: String,
                host: String,
                methodType: LoginMethodType,
                redirectUri: String?,
            ): LoginMethodHandler =
                loginPlatformRegistry.require(platformId).createHandler(
                    LoginContext(
                        host = host,
                        methodType = methodType,
                        redirectUri = redirectUri,
                        onSuccess = {
                            toHome.invoke()
                        },
                    ),
                )
        }
    }
}

@Immutable
public interface ServiceSelectState : NodeInfoState {
    public val loading: Boolean

    public fun agreementUrl(
        platformId: String,
        host: String,
    ): String?

    public fun createLoginHandler(
        platformId: String,
        host: String,
        methodType: LoginMethodType,
        redirectUri: String? = null,
    ): LoginMethodHandler
}
