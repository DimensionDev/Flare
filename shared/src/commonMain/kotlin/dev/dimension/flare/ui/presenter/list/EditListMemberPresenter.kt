package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch

class EditListMemberPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<EditListMemberState>() {
    @Composable
    override fun body(): EditListMemberState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        return object : EditListMemberState {
            override fun addMember(userKey: MicroBlogKey) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is ListDataSource)
                        it.addMember(listId, userKey)
                    }
                }
            }

            override fun removeMember(userKey: MicroBlogKey) {
                serviceState.onSuccess {
                    scope.launch {
                        require(it is ListDataSource)
                        it.removeMember(listId, userKey)
                    }
                }
            }
        }
    }
}

interface EditListMemberState {
    fun addMember(userKey: MicroBlogKey)

    fun removeMember(userKey: MicroBlogKey)
}
