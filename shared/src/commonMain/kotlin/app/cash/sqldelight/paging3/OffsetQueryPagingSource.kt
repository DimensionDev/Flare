/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.sqldelight.paging3

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransacterBase
import app.cash.sqldelight.TransactionCallbacks
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class OffsetQueryPagingSource<RowType : Any>(
    private val queryProvider: (limit: Int, offset: Int) -> Query<RowType>,
    private val countQuery: Query<Int>,
    private val transacter: TransacterBase,
    private val context: CoroutineContext,
    private val initialOffset: Int,
) : QueryPagingSource<Int, RowType>() {
    override val jumpingSupported get() = true

    override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, RowType> =
        withContext(context) {
            val key = params.key ?: initialOffset
            val limit =
                when (params) {
                    is PagingSource.LoadParams.Prepend<*> -> minOf(key, params.loadSize)
                    else -> params.loadSize
                }
            val getPagingSourceLoadResult: TransactionCallbacks.() -> PagingSource.LoadResult.Page<Int, RowType> = {
                val count = countQuery.executeAsOne()
                val offset =
                    when (params) {
                        is PagingSource.LoadParams.Prepend<*> -> maxOf(0, key - params.loadSize)
                        is PagingSource.LoadParams.Append<*> -> key
                        is PagingSource.LoadParams.Refresh<*> -> if (key >= count) maxOf(0, count - params.loadSize) else key
                        else -> error("Unknown PagingSourceLoadParams ${params::class}")
                    }
                val data =
                    queryProvider(limit, offset)
                        .also { currentQuery = it }
                        .executeAsList()
                val nextPosToLoad = offset + data.size
                PagingSource.LoadResult.Page(
                    data = data,
                    prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                    nextKey = nextPosToLoad.takeIf { data.isNotEmpty() && data.size >= limit && it < count },
                    itemsBefore = offset,
                    itemsAfter = maxOf(0, count - nextPosToLoad),
                )
            }
            val loadResult =
                when (transacter) {
                    is Transacter -> transacter.transactionWithResult(bodyWithReturn = getPagingSourceLoadResult)
                    is SuspendingTransacter -> transacter.transactionWithResult(bodyWithReturn = getPagingSourceLoadResult)
                }
            (if (invalid) PagingSource.LoadResult.Invalid<Int, RowType>() else loadResult)
        }

    override fun getRefreshKey(state: PagingState<Int, RowType>) =
        state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }
}
