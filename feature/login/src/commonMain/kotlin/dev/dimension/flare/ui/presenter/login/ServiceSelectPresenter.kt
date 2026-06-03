package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.presenter.PresenterBase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ServiceSelectPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<ServiceSelectState>(),
    KoinComponent {
    private val loginPlatformRegistry: LoginPlatformRegistry by inject()

    @Composable
    override fun body(): ServiceSelectState {
        val nodeInfoState = remember { NodeInfoPresenter() }.body()

        return object : ServiceSelectState, NodeInfoState by nodeInfoState {
            override val loading: Boolean = false

            override fun platformIcon(platformType: PlatformType): UiIcon = loginPlatformRegistry.require(platformType).metadata.icon

            override fun agreementUrl(
                platformType: PlatformType,
                host: String,
            ): String? = loginPlatformRegistry.require(platformType).agreementUrl(host)

            override fun loginMethods(platformType: PlatformType): List<LoginMethodSpec> = loginPlatformRegistry.methods(platformType)

            override fun createLoginHandler(
                platformType: PlatformType,
                host: String,
                methodType: LoginMethodType,
                redirectUri: String?,
            ): LoginMethodHandler =
                loginPlatformRegistry.require(platformType).createHandler(
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

    public fun platformIcon(platformType: PlatformType): UiIcon

    public fun agreementUrl(
        platformType: PlatformType,
        host: String,
    ): String?

    public fun loginMethods(platformType: PlatformType): List<LoginMethodSpec>

    public fun createLoginHandler(
        platformType: PlatformType,
        host: String,
        methodType: LoginMethodType,
        redirectUri: String? = null,
    ): LoginMethodHandler
}
