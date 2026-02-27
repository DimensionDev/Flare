package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingConfig

internal val pagingConfig: PagingConfig =
    PagingConfig(
        pageSize = 20,
    )

// @OptIn(ExperimentalPagingApi::class)
// internal fun timelinePager(
//    pageSize: Int,
//    database: CacheDatabase,
//    scope: CoroutineScope,
//    filterFlow: Flow<List<String>>,
//    mediator: BaseTimelineRemoteMediator,
//    accountRepository: AccountRepository,
// ): Flow<PagingData<UiTimeline>> {
//    val pagerFlow =
//        Pager(
//            config = pagingConfig,
//            remoteMediator = mediator,
//            pagingSourceFactory = {
//                database.pagingTimelineDao().getPagingSource(
//                    pagingKey = mediator.pagingKey,
//                )
//            },
//        ).flow.cachedIn(scope)
//    return combine(
//        pagerFlow,
//        filterFlow,
//        accountRepository.allAccounts,
//    ) { pagingData, filters, accounts ->
//        pagingData
//            .map { data ->
//                withContext(Dispatchers.IO) {
//                    val dataSource =
//                        when (data.timeline.accountType) {
//                            AccountType.Guest -> null
//                            is AccountType.Specific -> {
//                                accounts.first {
//                                    it.accountKey == data.timeline.accountType.accountKey
//                                }
//                            }
//                        }?.dataSource
//                    data.render(dataSource)
//                }
//            }.filter {
//                !it.contains(filters)
//            }
//    }.cachedIn(scope)
// }

// internal fun UiTimeline.contains(keywords: List<String>): Boolean {
//    val text =
//        if (content is UiTimeline.ItemContent.Status) {
//            listOfNotNull(
//                content.content.raw,
//                content.contentWarning?.raw,
//            )
//        } else {
//            emptyList()
//        }
//    return keywords.any { keyword ->
//        text.any { it.contains(keyword, ignoreCase = true) }
//    }
// }

// internal class MemoryPagingSource<T : Any>(
//    private val key: String,
//    private val context: CoroutineContext,
// ) : BasePagingSource<Int, T>() {
//    companion object {
//        private val caches = mutableMapOf<String, MutableStateFlow<ImmutableList<Any>>>()
//
//        fun <T : Any> update(
//            key: String,
//            value: ImmutableList<T>,
//        ) {
//            caches[key]?.value = value
//        }
//
//        fun <T : Any> append(
//            key: String,
//            value: ImmutableList<T>,
//        ) {
//            @Suppress("UNCHECKED_CAST")
//            caches[key]?.value = ((caches[key]?.value as? ImmutableList<T> ?: persistentListOf()) + value).toImmutableList()
//        }
//
//        fun <T : Any> updateWith(
//            key: String,
//            update: (ImmutableList<T>) -> ImmutableList<T>,
//        ) {
//            @Suppress("UNCHECKED_CAST")
//            val value = caches[key]?.value as? ImmutableList<T> ?: persistentListOf()
//            caches[key]?.value = update(value)
//        }
//
//        @Suppress("UNCHECKED_CAST")
//        fun <T : Any> get(key: String): ImmutableList<T>? = caches[key]?.value as? ImmutableList<T>
//
//        fun clear(key: String) {
//            caches.remove(key)
//        }
//
//        @Suppress("UNCHECKED_CAST")
//        fun <T : Any> getFlow(key: String): Flow<ImmutableList<T>> =
//            caches
//                .getOrPut(key) {
//                    MutableStateFlow(persistentListOf<T>())
//                } as Flow<ImmutableList<T>>
//    }
//
//    // TODO: workaround for skip first invalidation to avoid loading infinite loop
//    private var skiped = false
//
//    private val job =
//        caches
//            .getOrPut(key) {
//                MutableStateFlow(persistentListOf<T>())
//            }.let {
//                CoroutineScope(context).launch {
//                    it.collectLatest {
//                        if (!skiped) {
//                            skiped = true
//                            return@collectLatest
//                        }
//                        invalidate()
//                    }
//                }
//            }
//
//    init {
//        registerInvalidatedCallback {
//            job.cancel()
//        }
//    }
//
//    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
//        state.anchorPosition?.let {
//            maxOf(0, it - (state.config.initialLoadSize / 2))
//        }
//
//    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, T> {
//        val page = params.key ?: 0
//
//        @Suppress("UNCHECKED_CAST")
//        val list = caches[key]?.value as? ImmutableList<T> ?: return LoadResult.Error(Exception("No data"))
//        val data = list.subList(page, (page + params.loadSize).coerceIn(0, list.size))
//        val prevKey = (page - params.loadSize).takeIf { it in list.indices }
//        val nextKey = (page + params.loadSize).takeIf { it in list.indices }
//        return LoadResult.Page(
//            data = data,
//            prevKey = prevKey,
//            nextKey = nextKey,
//        )
//    }
// }
//
// @OptIn(ExperimentalPagingApi::class)
// internal fun <T : Any> memoryPager(
//    pageSize: Int,
//    pagingKey: String,
//    scope: CoroutineScope,
//    mediator: BaseRemoteMediator<Int, T>,
// ): Flow<PagingData<T>> =
//    Pager(
//        config = pagingConfig,
//        remoteMediator = mediator,
//        pagingSourceFactory = {
//            MemoryPagingSource(
//                key = pagingKey,
//                context = Dispatchers.IO,
//            )
//        },
//    ).flow.cachedIn(scope)
