package dev.dimension.flare.data.database.cache

import androidx.paging.PagingSource
import androidx.room3.Room
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.benchmark.benchmarkPlatform
import dev.dimension.flare.benchmark.collectLiveHeapBytes
import dev.dimension.flare.common.Locale
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbTranslation
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.TranslationEntityType
import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.data.database.cache.model.TranslationStatus
import dev.dimension.flare.data.database.cache.model.sourceHash
import dev.dimension.flare.data.database.cache.model.translationPayload
import dev.dimension.flare.data.database.createDatabaseDriver
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingSource
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageCache
import dev.dimension.flare.data.datasource.microblog.paging.TimelineDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.TimelinePagingMapper
import dev.dimension.flare.data.datasource.microblog.paging.TimelineUiMapperCache
import dev.dimension.flare.memoryDatabaseBuilder
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.measureTime

/**
 * Cross-platform baseline for the database path seen in the iOS Instruments trace.
 *
 * Run explicitly with:
 * ./gradlew :shared:<target>Test -PrunDatabaseBenchmark=true --tests '*TimelineDatabaseBenchmarkTest*'
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineDatabaseBenchmarkTest : RobolectricTest() {
    /**
     * Production-shaped benchmark for the complete timeline lifecycle.
     *
     * Unlike [benchmarkTimelineDatabase], this includes UI -> database mapping, diffed writes,
     * Room invalidation delivery, stable-prefix refresh, database -> UI mapping, and render hash
     * evaluation. The same [TimelineDbPageCache] survives paging-source replacement, matching the
     * non-Android timeline presenter.
     */
    @Test
    fun benchmarkTimelineLifecycle() =
        runTest(timeout = 5.minutes) {
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(PlatformDispatchers.IO)
                    .build()

            try {
                warmUpDatabase(database)
                val pageCache = TimelineDbPageCache()
                val loader =
                    TimelineDbPageLoader(
                        database = database,
                        pagingKey = LIFECYCLE_PAGING_KEY,
                        pageCache = pageCache,
                    )
                val displayOptions =
                    TranslationDisplayOptions(
                        translationEnabled = true,
                        autoDisplayEnabled = true,
                        providerCacheKey = LIFECYCLE_TRANSLATION_PROVIDER,
                    )
                val uiMapperCache = TimelineUiMapperCache(maxSize = LIFECYCLE_ROWS + PAGE_SIZE)

                var checksum = 0L
                var retainedUi: List<UiTimelineV2> = emptyList()

                val initialElapsed =
                    measureTime {
                        val initialUi = createRealisticTimelineItems(startIndex = 0, count = PAGE_SIZE, revision = 0)
                        val mapped = mapTimelineItems(initialUi, startIndex = 0)
                        database.connect { saveToDatabase(database, mapped) }
                        val page = loader.load(offset = 0, limit = PAGE_SIZE)
                        retainedUi = page.map { uiMapperCache.toUi(it, LIFECYCLE_PAGING_KEY, displayOptions) }
                        checksum = checksum * 31 + retainedUi.renderChecksum()
                    }

                val appendSamples = LongArray(LIFECYCLE_ROWS / PAGE_SIZE - 1)
                appendSamples.indices.forEach { pageIndex ->
                    val startIndex = (pageIndex + 1) * PAGE_SIZE
                    val elapsed =
                        measureTime {
                            val pageUi = createRealisticTimelineItems(startIndex, PAGE_SIZE, revision = 0)
                            val mapped = mapTimelineItems(pageUi, startIndex)
                            database.connect { saveToDatabase(database, mapped) }
                            val page = loader.load(offset = startIndex, limit = PAGE_SIZE)
                            val rendered = page.map { uiMapperCache.toUi(it, LIFECYCLE_PAGING_KEY, displayOptions) }
                            checksum = checksum * 31 + rendered.renderChecksum()
                        }
                    appendSamples[pageIndex] = elapsed.inWholeNanoseconds
                }

                seedLifecycleTranslations(database)
                retainedUi =
                    loader
                        .load(offset = 0, limit = PAGE_SIZE)
                        .map { uiMapperCache.toUi(it, LIFECYCLE_PAGING_KEY, displayOptions) }
                assertEquals(LIFECYCLE_ROWS, retainedUi.size)
                val heapWithStablePrefix = collectLiveHeapBytes()

                var pagingSource = OffsetFromStartPagingSource(loader)
                retainedUi =
                    loadLifecycleRefresh(pagingSource).map {
                        uiMapperCache.toUi(it, LIFECYCLE_PAGING_KEY, displayOptions)
                    }

                val unchangedUi = createRealisticTimelineItems(startIndex = 0, count = PAGE_SIZE, revision = 0)
                val unchangedSamples =
                    measureSamples(warmups = 1, iterations = LIFECYCLE_REFRESH_ITERATIONS) {
                        val mapped = mapTimelineItems(unchangedUi, startIndex = 0)
                        database.connect { saveToDatabase(database, mapped) }
                        mapped.fold(0L) { value, item -> value * 31 + item.statusData.contentHash }
                    }

                val changedSamples = LongArray(LIFECYCLE_REFRESH_ITERATIONS)
                repeat(LIFECYCLE_REFRESH_ITERATIONS) { iteration ->
                    val invalidated = CompletableDeferred<Unit>()
                    pagingSource.registerInvalidatedCallback { invalidated.complete(Unit) }
                    val elapsed =
                        measureTime {
                            val changedUi =
                                createRealisticTimelineItems(
                                    startIndex = 0,
                                    count = PAGE_SIZE,
                                    revision = iteration + 1,
                                )
                            val mapped = mapTimelineItems(changedUi, startIndex = 0)
                            database.connect { saveToDatabase(database, mapped) }
                            awaitLifecycleInvalidation(invalidated)

                            pagingSource = OffsetFromStartPagingSource(loader)
                            retainedUi =
                                loadLifecycleRefresh(pagingSource).map {
                                    uiMapperCache.toUi(it, LIFECYCLE_PAGING_KEY, displayOptions)
                                }
                            assertEquals(LIFECYCLE_ROWS, retainedUi.size)
                            checksum = checksum * 31 + retainedUi.renderChecksum()
                        }
                    changedSamples[iteration] = elapsed.inWholeNanoseconds
                }

                val translationInvalidated = CompletableDeferred<Unit>()
                pagingSource.registerInvalidatedCallback { translationInvalidated.complete(Unit) }
                val translationElapsed =
                    measureTime {
                        val latestRoot = createRealisticTimelineItems(0, 1, revision = LIFECYCLE_REFRESH_ITERATIONS).single().post
                        database.translationDao().insert(latestRoot.completedTranslation(updatedAt = FIXED_EPOCH_MILLIS + 50_000))
                        awaitLifecycleInvalidation(translationInvalidated)
                        pagingSource = OffsetFromStartPagingSource(loader)
                        retainedUi =
                            loadLifecycleRefresh(pagingSource).map {
                                uiMapperCache.toUi(it, LIFECYCLE_PAGING_KEY, displayOptions)
                            }
                        assertEquals(LIFECYCLE_ROWS, retainedUi.size)
                        checksum = checksum * 31 + retainedUi.renderChecksum()
                    }

                val insertInvalidated = CompletableDeferred<Unit>()
                pagingSource.registerInvalidatedCallback { insertInvalidated.complete(Unit) }
                val topInsertElapsed =
                    measureTime {
                        val insertedUi = createRealisticTimelineItems(startIndex = -1, count = 1, revision = 1)
                        database.connect { saveToDatabase(database, mapTimelineItems(insertedUi, startIndex = -1)) }
                        awaitLifecycleInvalidation(insertInvalidated)
                        pagingSource = OffsetFromStartPagingSource(loader)
                        retainedUi =
                            loadLifecycleRefresh(pagingSource).map {
                                uiMapperCache.toUi(it, LIFECYCLE_PAGING_KEY, displayOptions)
                            }
                        assertEquals(LIFECYCLE_ROWS + 1, retainedUi.size)
                        checksum = checksum * 31 + retainedUi.renderChecksum()
                    }
                val heapAfterLifecycle = collectLiveHeapBytes()

                println("TIMELINE_LIFECYCLE_BENCHMARK version=$LIFECYCLE_BENCHMARK_VERSION platform=$benchmarkPlatform")
                println(
                    "config retained_rows=$LIFECYCLE_ROWS page_size=$PAGE_SIZE users=$LIFECYCLE_USER_COUNT " +
                        "semantic_references_per_root=$REFERENCES_PER_ROOT " +
                        "presentation_references_per_root=$REFERENCES_PER_ROOT payload_chars=$LIFECYCLE_PAYLOAD_CHARS " +
                        "translated_root_stride=$LIFECYCLE_TRANSLATION_STRIDE refresh_iterations=$LIFECYCLE_REFRESH_ITERATIONS",
                )
                printMetric("initial_page_full_chain", BenchmarkSamples(longArrayOf(initialElapsed.inWholeNanoseconds), checksum))
                printMetric("append_page_full_chain", BenchmarkSamples(appendSamples, checksum))
                printMetric("unchanged_network_refresh", unchangedSamples)
                printMetric("changed_refresh_full_chain", BenchmarkSamples(changedSamples, checksum), "retained_rows=$LIFECYCLE_ROWS")
                printMetric(
                    "translation_refresh_full_chain",
                    BenchmarkSamples(longArrayOf(translationElapsed.inWholeNanoseconds), checksum),
                    "retained_rows=$LIFECYCLE_ROWS",
                )
                printMetric(
                    "top_insert_full_chain",
                    BenchmarkSamples(longArrayOf(topInsertElapsed.inWholeNanoseconds), checksum),
                    "retained_rows=${LIFECYCLE_ROWS + 1}",
                )
                printHeap("lifecycle_stable_prefix", heapWithStablePrefix)
                printHeap("lifecycle_complete", heapAfterLifecycle)
                println("lifecycle_checksum=${checksum + retainedUi.size}")

                pagingSource.invalidate()
                // Let the paging source's invalidation observer finish cancellation before Room closes.
                withContext(PlatformDispatchers.IO) { delay(50) }
            } finally {
                database.close()
            }
        }

    @Test
    fun benchmarkTimelineDatabase() =
        runTest(timeout = 5.minutes) {
            val database =
                Room
                    .memoryDatabaseBuilder<CacheDatabase>()
                    .setDriver(createDatabaseDriver())
                    .setQueryCoroutineContext(Dispatchers.Unconfined)
                    .build()

            try {
                warmUpDatabase(database)
                val heapBeforeSeed = collectLiveHeapBytes()
                val seed = seedDatabase(database)
                seedTranslations(database, seed.statusIds)
                val heapAfterSeed = collectLiveHeapBytes()

                val pageMetrics = benchmarkPageReads(database)
                val pageWindowMetrics = benchmarkPageWindowReads(database)
                val identityMetrics = benchmarkIdentityReads(database)
                val heapAfterReads = collectLiveHeapBytes()
                val stablePrefix = benchmarkStablePrefixRefresh(database)
                val heapWithStablePrefix = collectLiveHeapBytes()

                println("TIMELINE_DB_BENCHMARK version=$BENCHMARK_VERSION platform=$benchmarkPlatform")
                println(
                    "config root_rows=$ROOT_ROWS incoming_status_instances=${seed.statusInstanceCount} " +
                        "unique_status_rows=${seed.statusIds.size} semantic_references_per_root=$REFERENCES_PER_ROOT " +
                        "presentation_references_per_root=$REFERENCES_PER_ROOT payload_chars=$PAYLOAD_CHARS " +
                        "page_size=$PAGE_SIZE warmups=$WARMUP_ITERATIONS iterations=$MEASURE_ITERATIONS",
                )
                printMetric("initial_write", seed.initialWrite)
                printMetric("unchanged_rewrite", seed.unchangedRewrite)
                printMetric("equivalent_remap_rewrite", seed.equivalentRemapRewrite)
                pageMetrics.forEach { (offset, samples) ->
                    printMetric("page_read", samples, "offset=$offset limit=$PAGE_SIZE")
                }
                pageWindowMetrics.forEach { (limit, samples) ->
                    printMetric("page_window_read", samples, "offset=0 limit=$limit")
                }
                identityMetrics.forEach { (limit, samples) ->
                    printMetric("identity_read", samples, "offset=0 limit=$limit")
                }
                printMetric(
                    "stable_prefix_refresh",
                    stablePrefix.samples,
                    "requested_limit=$PAGE_SIZE retained_rows=${stablePrefix.retainedRows.size}",
                )
                printHeap("before_seed", heapBeforeSeed)
                printHeap("after_seed", heapAfterSeed)
                printHeap("after_reads", heapAfterReads)
                printHeap("with_stable_prefix", heapWithStablePrefix)
                println(
                    "checksum=${
                        seed.checksum +
                            pageMetrics.values.sumOf { it.checksum } +
                            pageWindowMetrics.values.sumOf { it.checksum } +
                            identityMetrics.values.sumOf { it.checksum } +
                            stablePrefix.samples.checksum +
                            stablePrefix.retainedRows.size
                    }",
                )
            } finally {
                database.close()
            }
        }

    private suspend fun warmUpDatabase(database: CacheDatabase) {
        val items = createTimelineItems(WARMUP_PAGING_KEY, startIndex = ROOT_ROWS, count = PAGE_SIZE)
        database.connect {
            saveToDatabase(database, items)
        }
        database.loadTimelinePage(WARMUP_PAGING_KEY, offset = 0, limit = PAGE_SIZE)
        database.loadTimelinePageIdentities(WARMUP_PAGING_KEY, offset = 0, limit = PAGE_SIZE)
    }

    private suspend fun seedDatabase(database: CacheDatabase): SeedResult {
        val items = createTimelineItems(BENCHMARK_PAGING_KEY, startIndex = 0, count = ROOT_ROWS)
        val statusIds = collectStatusIds(items)
        val statusInstanceCount = countStatusInstances(items)
        assertEquals(ROOT_ROWS * (REFERENCES_PER_ROOT + 1), statusIds.size)

        val initialWrite =
            measureSingle {
                database.connect {
                    saveToDatabase(database, items)
                }
            }
        assertEquals(
            ROOT_ROWS,
            database.loadTimelinePageIdentities(BENCHMARK_PAGING_KEY, offset = 0, limit = ROOT_ROWS + 1).size,
        )

        val unchangedRewrite =
            measureSamples(warmups = 1, iterations = REWRITE_ITERATIONS) {
                database.connect {
                    saveToDatabase(database, items)
                }
                statusIds.size.toLong()
            }

        val equivalentRemapRewrite =
            measureSamples(warmups = 1, iterations = REWRITE_ITERATIONS) {
                val remappedItems =
                    createTimelineItems(
                        pagingKey = BENCHMARK_PAGING_KEY,
                        startIndex = 0,
                        count = ROOT_ROWS,
                    )
                database.connect {
                    saveToDatabase(database, remappedItems)
                }
                countStatusInstances(remappedItems).toLong()
            }

        return SeedResult(
            statusIds = statusIds,
            statusInstanceCount = statusInstanceCount,
            initialWrite = initialWrite,
            unchangedRewrite = unchangedRewrite,
            equivalentRemapRewrite = equivalentRemapRewrite,
            checksum = statusIds.fold(0L) { checksum, id -> checksum * 31 + id.length },
        )
    }

    private suspend fun seedTranslations(
        database: CacheDatabase,
        statusIds: List<String>,
    ) {
        val translations =
            statusIds.mapIndexed { index, statusId ->
                DbTranslation(
                    entityType = TranslationEntityType.Status,
                    entityKey = statusId,
                    targetLanguage = "zh-CN",
                    sourceHash = "benchmark-$index",
                    status = TranslationStatus.Completed,
                    updatedAt = FIXED_EPOCH_MILLIS + index,
                )
            }
        database.connect {
            database.translationDao().insertAll(translations)
        }
    }

    private suspend fun benchmarkPageReads(database: CacheDatabase): Map<Int, BenchmarkSamples> =
        PAGE_OFFSETS.associateWith { offset ->
            val initial = database.loadTimelinePage(BENCHMARK_PAGING_KEY, offset, PAGE_SIZE)
            assertEquals(PAGE_SIZE, initial.size)
            assertEquals(PAGE_SIZE * REFERENCES_PER_ROOT, initial.sumOf { it.references.size })
            assertEquals(PAGE_SIZE * REFERENCES_PER_ROOT, initial.sumOf { it.presentationReferences.size })

            measureSamples {
                val page = database.loadTimelinePage(BENCHMARK_PAGING_KEY, offset, PAGE_SIZE)
                page.fold(page.size.toLong()) { checksum, item ->
                    checksum * 31 +
                        item.timeline.sortId +
                        item.statusTranslations.size +
                        item.references.size +
                        item.presentationReferences.size
                }
            }
        }

    private suspend fun benchmarkIdentityReads(database: CacheDatabase): Map<Int, BenchmarkSamples> =
        IDENTITY_LIMITS.associateWith { limit ->
            val initial = database.loadTimelinePageIdentities(BENCHMARK_PAGING_KEY, offset = 0, limit = limit)
            assertEquals(limit, initial.size)

            measureSamples {
                database
                    .loadTimelinePageIdentities(BENCHMARK_PAGING_KEY, offset = 0, limit = limit)
                    .fold(0L) { checksum, identity ->
                        checksum * 31 +
                            identity.sortId +
                            identity.rootContentHash +
                            identity.dependencyRevision +
                            identity.dependencyCount
                    }
            }
        }

    private suspend fun benchmarkPageWindowReads(database: CacheDatabase): Map<Int, BenchmarkSamples> =
        PAGE_WINDOW_LIMITS.associateWith { limit ->
            val initial = database.loadTimelinePage(BENCHMARK_PAGING_KEY, offset = 0, limit = limit)
            assertEquals(limit, initial.size)

            measureSamples(warmups = 1, iterations = WINDOW_MEASURE_ITERATIONS) {
                database
                    .loadTimelinePage(BENCHMARK_PAGING_KEY, offset = 0, limit = limit)
                    .fold(0L) { checksum, item ->
                        checksum * 31 +
                            item.timeline.sortId +
                            item.statusTranslations.size +
                            item.references.size +
                            item.presentationReferences.size
                    }
            }
        }

    private suspend fun benchmarkStablePrefixRefresh(database: CacheDatabase): StablePrefixBenchmarkResult {
        val loader =
            TimelineDbPageLoader(
                database = database,
                pagingKey = BENCHMARK_PAGING_KEY,
                pageCache = TimelineDbPageCache(),
            )
        val initial = loader.load(offset = 0, limit = ROOT_ROWS)
        assertEquals(ROOT_ROWS, initial.size)

        lateinit var retainedRows: List<DbPagingTimelineWithStatus>
        val samples =
            measureSamples(warmups = 1, iterations = WINDOW_MEASURE_ITERATIONS) {
                retainedRows = loader.load(offset = 0, limit = PAGE_SIZE)
                retainedRows
                    .fold(0L) { checksum, item ->
                        checksum * 31 + item.timeline.sortId + item.status.references.size
                    }
            }
        return StablePrefixBenchmarkResult(samples = samples, retainedRows = retainedRows)
    }

    private suspend fun createTimelineItems(
        pagingKey: String,
        startIndex: Int,
        count: Int,
    ): List<DbPagingTimelineWithStatus> {
        val result = ArrayList<DbPagingTimelineWithStatus>(count)
        repeat(count) { relativeIndex ->
            val index = startIndex + relativeIndex
            val parent = createPost(index, "parent")
            val quote = createPost(index, "quote")
            val repost = createPost(index, "repost")
            val root =
                createPost(
                    index = index,
                    role = "root",
                    references =
                        persistentListOf(
                            UiTimelineV2.Post.Reference(parent.statusKey, ReferenceType.Reply),
                            UiTimelineV2.Post.Reference(quote.statusKey, ReferenceType.Quote),
                            UiTimelineV2.Post.Reference(repost.statusKey, ReferenceType.Retweet),
                        ),
                )
            result +=
                TimelinePagingMapper.toDb(
                    data =
                        UiTimelineV2.TimelinePostItem(
                            post = root,
                            presentation =
                                UiTimelineV2.PostPresentation(
                                    inlineParents = persistentListOf(parent),
                                    quotes = persistentListOf(quote),
                                    repost = repost,
                                ),
                        ),
                    pagingKey = pagingKey,
                    sortId = index.toLong(),
                )
        }
        return result
    }

    private suspend fun mapTimelineItems(
        items: List<UiTimelineV2.TimelinePostItem>,
        startIndex: Int,
    ): List<DbPagingTimelineWithStatus> =
        TimelinePagingMapper.toDb(
            data = items,
            pagingKey = LIFECYCLE_PAGING_KEY,
            sortIds = List(items.size) { index -> (startIndex + index).toLong() },
        )

    private fun createRealisticTimelineItems(
        startIndex: Int,
        count: Int,
        revision: Int,
    ): List<UiTimelineV2.TimelinePostItem> =
        List(count) { relativeIndex ->
            val index = startIndex + relativeIndex
            val parent = createRealisticPost(index, "parent", revision, userOffset = 1)
            val quote = createRealisticPost(index, "quote", revision, userOffset = 2)
            val repost = createRealisticPost(index, "repost", revision, userOffset = 3)
            val root =
                createRealisticPost(
                    index = index,
                    role = "root",
                    revision = revision,
                    userOffset = 0,
                    references =
                        persistentListOf(
                            UiTimelineV2.Post.Reference(parent.statusKey, ReferenceType.Reply),
                            UiTimelineV2.Post.Reference(quote.statusKey, ReferenceType.Quote),
                            UiTimelineV2.Post.Reference(repost.statusKey, ReferenceType.Retweet),
                        ),
                )
            UiTimelineV2.TimelinePostItem(
                post = root,
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents = persistentListOf(parent),
                        quotes = persistentListOf(quote),
                        repost = repost,
                    ),
            )
        }

    private fun createRealisticPost(
        index: Int,
        role: String,
        revision: Int,
        userOffset: Int,
        references: kotlinx.collections.immutable.ImmutableList<UiTimelineV2.Post.Reference> = persistentListOf(),
    ): UiTimelineV2.Post {
        val userIndex = ((index * 7 + userOffset).mod(LIFECYCLE_USER_COUNT))
        val user = createBenchmarkUser(userIndex)
        return UiTimelineV2.Post(
            platformId = "Mastodon",
            images = persistentListOf(),
            sensitive = index.mod(17) == 0,
            contentWarning =
                if (index.mod(17) == 0) {
                    UiTranslatableText("benchmark warning $index".toUiPlainText())
                } else {
                    null
                },
            user = user,
            content =
                UiTranslatableText(
                    "$role-$index revision=$revision $LIFECYCLE_PAYLOAD_TEXT".toUiPlainText(),
                ),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id = "$role-$index", host = BENCHMARK_HOST),
            card = null,
            createdAt = Instant.fromEpochMilliseconds(FIXED_EPOCH_MILLIS + index).toUi(),
            sourceLanguages = persistentListOf("en"),
            references = references,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
        )
    }

    private fun createBenchmarkUser(index: Int): UiProfile {
        val key = MicroBlogKey(id = "benchmark-user-$index", host = BENCHMARK_HOST)
        return UiProfile(
            key = key,
            handle = UiHandle(raw = key.id, host = key.host),
            avatar = "https://${key.host}/avatars/${key.id}.png",
            nameInternal = "Benchmark User $index".toUiPlainText(),
            platformId = "Mastodon",
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = "Shared benchmark profile $index".toUiPlainText(),
            matrices =
                UiProfile.Matrices(
                    fansCount = index.toLong() * 101,
                    followsCount = index.toLong() * 7,
                    statusesCount = index.toLong() * 503,
                    platformFansCount = (index * 101).toString(),
                ),
            mark = persistentListOf(),
            bottomContent = null,
        )
    }

    private suspend fun seedLifecycleTranslations(database: CacheDatabase) {
        val translations =
            (0 until LIFECYCLE_ROWS step LIFECYCLE_TRANSLATION_STRIDE).map { index ->
                createRealisticTimelineItems(index, 1, revision = 0).single().post.completedTranslation(
                    updatedAt = FIXED_EPOCH_MILLIS + 10_000 + index,
                )
            }
        database.connect { database.translationDao().insertAll(translations) }
    }

    private fun UiTimelineV2.Post.completedTranslation(updatedAt: Long): DbTranslation {
        val originalPayload = checkNotNull(translationPayload())
        return DbTranslation(
            entityType = TranslationEntityType.Status,
            entityKey = DbStatus.createId(accountType as dev.dimension.flare.model.DbAccountType, statusKey),
            targetLanguage = Locale.language,
            sourceHash = originalPayload.sourceHash(LIFECYCLE_TRANSLATION_PROVIDER),
            status = TranslationStatus.Completed,
            payload = TranslationPayload(content = "translated ${content.original.raw.take(80)}".toUiPlainText()),
            updatedAt = updatedAt,
        )
    }

    private suspend fun loadLifecycleRefresh(
        source: OffsetFromStartPagingSource<DbPagingTimelineWithStatus>,
    ): List<DbPagingTimelineWithStatus> {
        val result =
            source.load(
                PagingSource.LoadParams.Refresh<OffsetFromStartPagingKey>(
                    key = null,
                    loadSize = PAGE_SIZE,
                    placeholdersEnabled = false,
                ),
            )
        return assertIs<PagingSource.LoadResult.Page<OffsetFromStartPagingKey, DbPagingTimelineWithStatus>>(result).data
    }

    private suspend fun awaitLifecycleInvalidation(invalidated: CompletableDeferred<Unit>) {
        // runTest uses virtual time; invalidation delivery runs on the production IO dispatcher.
        withContext(PlatformDispatchers.IO) {
            withTimeout(LIFECYCLE_INVALIDATION_TIMEOUT_MILLIS) { invalidated.await() }
        }
    }

    private fun List<UiTimelineV2>.renderChecksum(): Long = fold(size.toLong()) { checksum, item -> checksum * 31 + item.renderHash }

    private fun createPost(
        index: Int,
        role: String,
        references: kotlinx.collections.immutable.ImmutableList<UiTimelineV2.Post.Reference> = persistentListOf(),
    ): UiTimelineV2.Post {
        val statusKey = MicroBlogKey(id = "$role-$index", host = BENCHMARK_HOST)
        return UiTimelineV2.Post(
            platformId = "Benchmark",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText("$role-$index $PAYLOAD_TEXT".toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Instant.fromEpochMilliseconds(FIXED_EPOCH_MILLIS + index).toUi(),
            references = references,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Guest,
        )
    }

    private fun collectStatusIds(items: List<DbPagingTimelineWithStatus>): List<String> =
        buildList {
            items.forEach { item ->
                add(item.status.status.data.id)
                item.status.references.mapNotNullTo(this) { it.status?.data?.id }
                item.presentationReferences.mapNotNullTo(this) { it.status?.data?.id }
            }
        }.distinct()

    private fun countStatusInstances(items: List<DbPagingTimelineWithStatus>): Int =
        items.sumOf { item ->
            1 +
                item.status.references.count { it.status != null } +
                item.presentationReferences.count { it.status != null }
        }

    private suspend fun measureSingle(block: suspend () -> Unit): BenchmarkSamples {
        val elapsed = measureTime { block() }
        return BenchmarkSamples(longArrayOf(elapsed.inWholeNanoseconds), checksum = 1L)
    }

    private suspend fun measureSamples(
        warmups: Int = WARMUP_ITERATIONS,
        iterations: Int = MEASURE_ITERATIONS,
        block: suspend () -> Long,
    ): BenchmarkSamples {
        var checksum = 0L
        repeat(warmups) {
            checksum = checksum * 31 + block()
        }
        val samples = LongArray(iterations)
        repeat(iterations) { index ->
            var value = 0L
            val elapsed = measureTime { value = block() }
            samples[index] = elapsed.inWholeNanoseconds
            checksum = checksum * 31 + value
        }
        return BenchmarkSamples(samples, checksum)
    }

    private fun printMetric(
        name: String,
        samples: BenchmarkSamples,
        dimensions: String = "",
    ) {
        val prefix = if (dimensions.isEmpty()) "" else "$dimensions "
        println(
            "metric name=$name ${prefix}samples=${samples.size} " +
                "median_ms=${samples.medianMillis.rounded()} " +
                "p95_ms=${samples.p95Millis.rounded()} " +
                "min_ms=${samples.minMillis.rounded()}",
        )
    }

    private fun printHeap(
        phase: String,
        bytes: Long?,
    ) {
        println("memory name=live_heap_after_gc phase=$phase bytes=${bytes ?: "unavailable"}")
    }

    private fun Double.rounded(): Double = (this * 1_000.0).roundToLong() / 1_000.0

    private data class SeedResult(
        val statusIds: List<String>,
        val statusInstanceCount: Int,
        val initialWrite: BenchmarkSamples,
        val unchangedRewrite: BenchmarkSamples,
        val equivalentRemapRewrite: BenchmarkSamples,
        val checksum: Long,
    )

    private data class BenchmarkSamples(
        private val nanos: LongArray,
        val checksum: Long,
    ) {
        private val sorted: LongArray = nanos.sortedArray()

        val size: Int = nanos.size
        val medianMillis: Double = sorted[sorted.size / 2].toMillis()
        val p95Millis: Double = sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)].toMillis()
        val minMillis: Double = sorted.first().toMillis()

        private fun Long.toMillis(): Double = toDouble() / 1_000_000.0
    }

    private data class StablePrefixBenchmarkResult(
        val samples: BenchmarkSamples,
        val retainedRows: List<DbPagingTimelineWithStatus>,
    )

    private companion object {
        const val BENCHMARK_VERSION = 2
        const val LIFECYCLE_BENCHMARK_VERSION = 2
        const val BENCHMARK_PAGING_KEY = "timeline-database-benchmark"
        const val WARMUP_PAGING_KEY = "timeline-database-benchmark-warmup"
        const val LIFECYCLE_PAGING_KEY = "timeline-lifecycle-benchmark"
        const val LIFECYCLE_TRANSLATION_PROVIDER = "benchmark-provider-v1"
        const val BENCHMARK_HOST = "benchmark.invalid"
        const val ROOT_ROWS = 500
        const val LIFECYCLE_ROWS = 240
        const val LIFECYCLE_USER_COUNT = 48
        const val LIFECYCLE_PAYLOAD_CHARS = 768
        const val LIFECYCLE_TRANSLATION_STRIDE = 4
        const val LIFECYCLE_REFRESH_ITERATIONS = 3
        const val LIFECYCLE_INVALIDATION_TIMEOUT_MILLIS = 10_000L
        const val PAGE_SIZE = 20
        const val REFERENCES_PER_ROOT = 3
        const val PAYLOAD_CHARS = 2_048
        const val FIXED_EPOCH_MILLIS = 1_700_000_000_000L
        const val WARMUP_ITERATIONS = 2
        const val MEASURE_ITERATIONS = 7
        const val REWRITE_ITERATIONS = 3
        const val WINDOW_MEASURE_ITERATIONS = 3

        val PAGE_OFFSETS = listOf(0, 240, 480)
        val PAGE_WINDOW_LIMITS = listOf(20, 100, 500)
        val IDENTITY_LIMITS = listOf(20, 100, 500)
        val PAYLOAD_TEXT = "0123456789abcdef".repeat(PAYLOAD_CHARS / 16)
        val LIFECYCLE_PAYLOAD_TEXT = "timeline payload 0123456789abcdef ".repeat(LIFECYCLE_PAYLOAD_CHARS / 34)
    }
}
