package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.presenter.PresenterBase

public class ReloginPresenter(
    private val target: ReloginTarget,
    private val onSuccess: () -> Unit,
) : PresenterBase<ReloginState>() {
    private val loginPlatformRegistry: LoginPlatformRegistry by koinInject()

    @Composable
    override fun body(): ReloginState =
        object : ReloginState {
            override val target: ReloginTarget = this@ReloginPresenter.target
            override val methods: List<LoginMethodSpec> = loginPlatformRegistry.methods(target.platformType)

            override fun platformIcon(): UiIcon = loginPlatformRegistry.require(target.platformType).metadata.icon

            override fun agreementUrl(): String? =
                loginPlatformRegistry
                    .require(target.platformType)
                    .agreementUrl(target.accountKey.host)

            override fun createLoginHandler(methodType: LoginMethodType): LoginMethodHandler =
                loginPlatformRegistry.require(target.platformType).createHandler(
                    LoginContext(
                        host = target.accountKey.host,
                        methodType = methodType,
                        onSuccess = {
                            onSuccess()
                        },
                        reloginTarget = target,
                    ),
                )
        }
}

@Immutable
public interface ReloginState {
    public val target: ReloginTarget
    public val methods: List<LoginMethodSpec>

    public fun platformIcon(): UiIcon

    public fun agreementUrl(): String?

    public fun createLoginHandler(methodType: LoginMethodType): LoginMethodHandler
}
