package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.presenter.PresenterBase

class NodeInfoPresenter : PresenterBase<NodeInfoState>() {
    @Composable
    override fun body(): NodeInfoState {


        return object : NodeInfoState {
        }
    }
}

interface NodeInfoState {

}