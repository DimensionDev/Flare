package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow
import kotlin.native.HiddenFromObjC

public expect abstract class PresenterBase<Model : Any>() {
    public val models: StateFlow<Model>

    @Composable
    public abstract fun body(): Model
}

@HiddenFromObjC
@Composable
public operator fun <Model : Any> PresenterBase<Model>.invoke(): Model = body()
