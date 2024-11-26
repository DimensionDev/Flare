package dev.dimension.flare.ui.screen.home

import dev.dimension.flare.AppContext
import dev.dimension.flare.common.isEmpty
import dev.dimension.flare.common.isError
import dev.dimension.flare.common.isLoading
import dev.dimension.flare.common.isSuccess
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.observe
import dev.dimension.flare.ui.component.timelineItem
import dev.dimension.flare.ui.presenter.home.HomeTimelinePresenter
import kotlinx.cinterop.ExperimentalForeignApi
import org.gtkkn.bindings.gio.ListStore
import org.gtkkn.bindings.gio.annotations.GioVersion2_44
import org.gtkkn.bindings.gobject.Object
import org.gtkkn.bindings.gtk.Label
import org.gtkkn.bindings.gtk.ListItem
import org.gtkkn.bindings.gtk.ListView
import org.gtkkn.bindings.gtk.NoSelection
import org.gtkkn.bindings.gtk.Overlay
import org.gtkkn.bindings.gtk.ScrolledWindow
import org.gtkkn.bindings.gtk.SignalListItemFactory
import org.gtkkn.bindings.gtk.Spinner
import org.gtkkn.bindings.gtk.Widget
import org.gtkkn.extensions.gobject.ObjectType
import org.gtkkn.extensions.gobject.asType

@OptIn(ExperimentalForeignApi::class, GioVersion2_44::class)
internal fun AppContext.homeScreen(): Widget {
    val presenter = HomeTimelinePresenter(dev.dimension.flare.model.AccountType.Guest)

    val listModel = ListStore(Indexer.gType)

    val factory = SignalListItemFactory()
    factory.connectBind {
        val listItem = it.asType<ListItem>()
        val (index) = listItem.getItem()?.asType<Indexer>() ?: return@connectBind
        presenter.models.value.listState.onSuccess {
            val item = get(index)
            if (item != null) {
                listItem.setChild(timelineItem(item))
            }
        }
    }
    val list =
        ScrolledWindow().apply {
            setChild(ListView(NoSelection(listModel), factory))
        }
    val loading =
        Spinner().apply {
            setSizeRequest(100, 100)
        }
    val empty = Label("Empty")
    val error = Label("Error")
    return Overlay().apply {
        setChild(loading)
        addOverlay(empty)
        addOverlay(error)
        addOverlay(list)
        observe(presenter.models) {
            val listState = it.listState
            loading.spinning = listState.isLoading
            error.visible = listState.isError
            empty.visible = listState.isEmpty
            list.visible = listState.isSuccess()
            if (listState.isSuccess()) {
//                listModel.removeAll()
                (0 until listState.itemCount).map { Indexer(it) }.forEach {
                    listModel.append(it)
                }
//                addOverlay(list)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private data class Indexer(
    val index: Int,
) : Object(newInstancePointer()) {
    companion object Type : ObjectType<Indexer>(Indexer::class, type)
}
