@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.android

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.lazy.LazyCollectionCoordinator
import dev.dimension.flare.ui.lazy.LazyCollectionModel
import dev.dimension.flare.ui.lazy.LazyCollectionWidget
import dev.dimension.flare.ui.lazy.LazyCrossAxisAlignment
import dev.dimension.flare.ui.lazy.LazyItemHost
import dev.dimension.flare.ui.lazy.LazyListItemInfo
import dev.dimension.flare.ui.lazy.LazyListLayoutInfo
import dev.dimension.flare.ui.lazy.LazyListOrientation
import dev.dimension.flare.ui.lazy.LazyListScrollRequest
import dev.dimension.flare.ui.lazy.LazyRealizedItemUpdate
import dev.dimension.flare.ui.lazy.findIndexByKey
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

/** RecyclerView renderer for Flare lazy collections. */
public object AndroidViewLazyLayoutRendererPlugin : FlareRendererPlugin<AndroidViewBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidViewBackend>) {
        registrar.register(LazyCollectionWidget::class) { backend ->
            AndroidViewLazyCollectionWidget(backend)
        }
    }
}

private class AndroidViewLazyCollectionWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<RecyclerView>(RecyclerView(backend.context)),
    LazyCollectionWidget {
    private val density = view.resources.displayMetrics.density
    private val layoutManager = LinearLayoutManager(backend.context)
    private val spacingDecoration = LazySpacingDecoration()
    private var pendingAnimatedScroll: LazyListScrollRequest? = null
    private val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = ::applyModel,
            onScroll = ::performScroll,
            onScrollCancelled = ::cancelScroll,
            uiDispatcher = Dispatchers.Main.immediate,
        )
    private val lazyAdapter = AndroidLazyAdapter(coordinator, ::currentModel)
    private val scrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) {
                reportLayoutInfo()
            }

            override fun onScrollStateChanged(
                recyclerView: RecyclerView,
                newState: Int,
            ) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    cancelPendingAnimatedScroll()
                }
                coordinator.reportScrollInProgress(newState != RecyclerView.SCROLL_STATE_IDLE)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    completeAnimatedScroll()
                }
                reportLayoutInfo()
            }
        }

    init {
        view.layoutManager = layoutManager
        view.adapter = lazyAdapter
        view.addItemDecoration(spacingDecoration)
        view.addOnScrollListener(scrollListener)
    }

    override fun setModel(model: LazyCollectionModel) {
        coordinator.setModel(model)
    }

    override fun dispose() {
        pendingAnimatedScroll?.cancel()
        pendingAnimatedScroll = null
        view.removeOnScrollListener(scrollListener)
        view.adapter = null
        lazyAdapter.dispose()
        coordinator.dispose()
    }

    private fun currentModel(): LazyCollectionModel = checkNotNull(coordinator.model) { "Android lazy collection has no model." }

    private fun applyModel(
        previous: LazyCollectionModel?,
        current: LazyCollectionModel,
    ): LazyRealizedItemUpdate {
        val anchor = previous?.let(::captureAnchor)
        layoutManager.orientation =
            when (current.orientation) {
                LazyListOrientation.Vertical -> RecyclerView.VERTICAL
                LazyListOrientation.Horizontal -> RecyclerView.HORIZONTAL
            }
        spacingDecoration.orientation = current.orientation
        spacingDecoration.spacing = (current.spacing * density).roundToInt()
        lazyAdapter.update()
        anchor?.let { restoreAnchor(it, current) }
        view.invalidateItemDecorations()
        view.post(::reportLayoutInfo)
        return LazyRealizedItemUpdate.RendererManaged
    }

    private fun captureAnchor(model: LazyCollectionModel): AndroidLazyAnchor? {
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position !in 0 until model.itemProvider.itemCount) return null
        val child = layoutManager.findViewByPosition(position) ?: return null
        val offset =
            when (model.orientation) {
                LazyListOrientation.Vertical -> layoutManager.getDecoratedTop(child) - view.paddingTop
                LazyListOrientation.Horizontal -> layoutManager.getDecoratedLeft(child) - view.paddingLeft
            }
        return AndroidLazyAnchor(
            key = model.itemProvider.key(position),
            index = position,
            itemCount = model.itemProvider.itemCount,
            offset = offset,
        )
    }

    private fun restoreAnchor(
        anchor: AndroidLazyAnchor,
        model: LazyCollectionModel,
    ) {
        val index =
            model.itemProvider.findIndexByKey(
                key = anchor.key,
                expectedIndex = anchor.index,
                previousItemCount = anchor.itemCount,
            )
        if (index >= 0) {
            layoutManager.scrollToPositionWithOffset(index, anchor.offset)
        }
    }

    private fun performScroll(request: LazyListScrollRequest) {
        if (request.animated) {
            cancelPendingAnimatedScroll(stopScroll = true)
            pendingAnimatedScroll = request
            val offset = -(request.scrollOffset * density).roundToInt()
            layoutManager.startSmoothScroll(
                OffsetLinearSmoothScroller(view.context, offset) {
                    completeAnimatedScroll(request)
                }.apply {
                    targetPosition = request.index
                },
            )
        } else {
            layoutManager.scrollToPositionWithOffset(
                request.index,
                -(request.scrollOffset * density).roundToInt(),
            )
            view.post {
                reportLayoutInfo()
                request.complete()
            }
        }
    }

    private fun completeAnimatedScroll() {
        completeAnimatedScroll(pendingAnimatedScroll ?: return)
    }

    private fun completeAnimatedScroll(request: LazyListScrollRequest) {
        if (pendingAnimatedScroll !== request) return
        pendingAnimatedScroll = null
        val itemCount = coordinator.model?.itemProvider?.itemCount ?: 0
        if (request.isActive && request.index in 0 until itemCount) {
            request.complete()
        } else {
            request.cancel()
        }
    }

    private fun cancelScroll(request: LazyListScrollRequest) {
        if (pendingAnimatedScroll !== request) return
        pendingAnimatedScroll = null
        view.stopScroll()
        coordinator.reportScrollInProgress(false)
    }

    private fun cancelPendingAnimatedScroll(stopScroll: Boolean = false) {
        val request = pendingAnimatedScroll ?: return
        pendingAnimatedScroll = null
        if (stopScroll) view.stopScroll()
        request.cancel()
    }

    private fun reportLayoutInfo() {
        val model = coordinator.model ?: return
        val provider = model.itemProvider
        val visibleItems =
            buildList {
                repeat(view.childCount) { childIndex ->
                    val child = view.getChildAt(childIndex)
                    val index = view.getChildAdapterPosition(child)
                    if (index !in 0 until provider.itemCount) return@repeat
                    val offset =
                        when (model.orientation) {
                            LazyListOrientation.Vertical -> child.top - view.paddingTop
                            LazyListOrientation.Horizontal -> child.left - view.paddingLeft
                        }
                    val size =
                        when (model.orientation) {
                            LazyListOrientation.Vertical -> child.measuredHeight
                            LazyListOrientation.Horizontal -> child.measuredWidth
                        }
                    add(
                        LazyListItemInfo(
                            key = provider.key(index),
                            index = index,
                            offset = offset / density,
                            size = size / density,
                        ),
                    )
                }
            }.sortedBy(LazyListItemInfo::index)
        val viewportSize =
            when (model.orientation) {
                LazyListOrientation.Vertical -> view.height - view.paddingTop - view.paddingBottom
                LazyListOrientation.Horizontal -> view.width - view.paddingLeft - view.paddingRight
            }
        coordinator.reportLayoutInfo(
            LazyListLayoutInfo(
                totalItemsCount = provider.itemCount,
                viewportStartOffset = 0f,
                viewportEndOffset = viewportSize / density,
                visibleItems = visibleItems,
            ),
        )
    }
}

private class AndroidLazyAdapter(
    private val coordinator: LazyCollectionCoordinator,
    private val model: () -> LazyCollectionModel,
) : RecyclerView.Adapter<AndroidLazyViewHolder>() {
    private val holders = mutableSetOf<AndroidLazyViewHolder>()

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = coordinator.model?.itemProvider?.itemCount ?: 0

    override fun getItemId(position: Int): Long {
        val key = model().itemProvider.key(position)
        return coordinator.itemIdentities.idFor(key)
    }

    // Every Android View item uses the same LazyItemLinearLayout shell. Keeping native view types
    // split by arbitrary business contentType only fragments RecyclerView's pool and retains one
    // bucket per type; the Flare item host already resets composition content when it is rebound.
    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AndroidLazyViewHolder = AndroidLazyViewHolder(LazyItemLinearLayout(parent.context))

    override fun onBindViewHolder(
        holder: AndroidLazyViewHolder,
        position: Int,
    ) {
        holders += holder
        holder.bind(coordinator, position, model())
    }

    override fun onViewRecycled(holder: AndroidLazyViewHolder) {
        holders -= holder
        holder.recycle()
    }

    fun update() {
        // Provider callbacks may observe Compose snapshot state and cannot safely be diffed on a
        // worker thread. Stable IDs let RecyclerView preserve its visible holders while avoiding
        // a synchronous walk over every key on each model generation.
        notifyDataSetChanged()
    }

    fun dispose() {
        holders.toList().forEach(AndroidLazyViewHolder::recycle)
        holders.clear()
    }
}

private class AndroidLazyViewHolder(
    private val root: LazyItemLinearLayout,
) : RecyclerView.ViewHolder(root) {
    private var itemHost: LazyItemHost? = null

    fun bind(
        coordinator: LazyCollectionCoordinator,
        index: Int,
        model: LazyCollectionModel,
    ) {
        root.bindModel(model)
        root.layoutParams =
            when (model.orientation) {
                LazyListOrientation.Vertical -> {
                    RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }

                LazyListOrientation.Horizontal -> {
                    RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            }
        val host = itemHost ?: coordinator.createItemHost(AndroidViewChildren(root)).also { itemHost = it }
        host.bind(index)
    }

    fun recycle() {
        itemHost?.dispose()
        itemHost = null
    }
}

private class OffsetLinearSmoothScroller(
    context: Context,
    private val offset: Int,
    private val onStopped: () -> Unit,
) : LinearSmoothScroller(context) {
    override fun getVerticalSnapPreference(): Int = SNAP_TO_START

    override fun getHorizontalSnapPreference(): Int = SNAP_TO_START

    override fun calculateDtToFit(
        viewStart: Int,
        viewEnd: Int,
        boxStart: Int,
        boxEnd: Int,
        snapPreference: Int,
    ): Int = boxStart + offset - viewStart

    override fun onStop() {
        super.onStop()
        onStopped()
    }
}

private class LazyItemLinearLayout(
    context: android.content.Context,
) : LinearLayout(context) {
    private var model: LazyCollectionModel? = null

    fun bindModel(value: LazyCollectionModel) {
        model = value
        orientation =
            when (value.orientation) {
                LazyListOrientation.Vertical -> VERTICAL
                LazyListOrientation.Horizontal -> HORIZONTAL
            }
        gravity =
            when (value.orientation) {
                LazyListOrientation.Vertical -> value.crossAxisAlignment.horizontalGravity()
                LazyListOrientation.Horizontal -> value.crossAxisAlignment.verticalGravity()
            }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        model?.let(::applyCrossAxisAlignment)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun applyCrossAxisAlignment(model: LazyCollectionModel) {
        repeat(childCount) { index ->
            val child = getChildAt(index)
            val current = child.layoutParams
            val params =
                current as? LayoutParams
                    ?: LayoutParams(
                        current?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                        current?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            when (model.orientation) {
                LazyListOrientation.Vertical -> {
                    if (model.crossAxisAlignment == LazyCrossAxisAlignment.Stretch) {
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    params.gravity = model.crossAxisAlignment.horizontalGravity()
                }

                LazyListOrientation.Horizontal -> {
                    if (model.crossAxisAlignment == LazyCrossAxisAlignment.Stretch) {
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    params.gravity = model.crossAxisAlignment.verticalGravity()
                }
            }
            if (child.layoutParams !== params) {
                child.layoutParams = params
            }
        }
    }
}

private fun LazyCrossAxisAlignment.horizontalGravity(): Int =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> Gravity.START
        LazyCrossAxisAlignment.Center -> Gravity.CENTER_HORIZONTAL
        LazyCrossAxisAlignment.End -> Gravity.END
    }

private fun LazyCrossAxisAlignment.verticalGravity(): Int =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> Gravity.TOP
        LazyCrossAxisAlignment.Center -> Gravity.CENTER_VERTICAL
        LazyCrossAxisAlignment.End -> Gravity.BOTTOM
    }

private class LazySpacingDecoration : RecyclerView.ItemDecoration() {
    var orientation: LazyListOrientation = LazyListOrientation.Vertical
    var spacing: Int = 0

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position < 0 || position >= state.itemCount - 1) return
        when (orientation) {
            LazyListOrientation.Vertical -> outRect.bottom = spacing
            LazyListOrientation.Horizontal -> outRect.right = spacing
        }
    }
}

private data class AndroidLazyAnchor(
    val key: Any,
    val index: Int,
    val itemCount: Int,
    val offset: Int,
)
