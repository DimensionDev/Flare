package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.applyTranslation
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.uuid.Uuid

internal object TimelinePagingMapper {
    suspend fun toDb(
        data: UiTimelineV2,
        pagingKey: String,
    ): DbPagingTimelineWithStatus =
        DbPagingTimelineWithStatus(
            timeline =
                DbPagingTimeline(
                    pagingKey = pagingKey,
                    statusKey = data.statusKey,
                    sortId = SnowflakeIdGenerator.nextId(),
                ),
            status =
                DbStatusWithReference(
                    status = uiTimelineToDbStatusWithUser(data, sanitizePostReferences = true),
                    references =
                        when (data) {
                            is UiTimelineV2.Feed -> emptyList()
                            is UiTimelineV2.Message -> emptyList()
                            is UiTimelineV2.Post -> collectPostReferences(data, data.statusKey)

                            is UiTimelineV2.User -> emptyList()
                            is UiTimelineV2.UserList ->
                                data.post
                                    ?.let {
                                        listOf(
                                            uiTimelineToDbStatusReferenceWithStatus(
                                                data = it,
                                                referenceType = ReferenceType.Quote,
                                                rootStatusKey = data.statusKey,
                                            ),
                                        ) + collectPostReferences(it, data.statusKey)
                                    }.orEmpty()
                        },
                ),
        )

    fun toUi(
        item: DbPagingTimelineWithStatus,
        pagingKey: String,
        useDbKeyInItemKey: Boolean,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 =
        toUi(
            item = item.status,
            pagingKey = pagingKey,
            useDbKeyInItemKey = useDbKeyInItemKey,
            translationDisplayOptions = translationDisplayOptions,
        )

    fun toUi(
        item: DbStatusWithReference,
        pagingKey: String,
        useDbKeyInItemKey: Boolean,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 {
        val root =
            dbStatusWithUserToUiTimeline(
                data = item.status,
                pagingKey = pagingKey,
                useDbKeyInItemKey = useDbKeyInItemKey,
                translationDisplayOptions = translationDisplayOptions,
            )
        val references =
            item.references.mapNotNull { reference ->
                reference.status?.let {
                    reference.reference.referenceType to
                        dbStatusWithUserToUiTimeline(
                            data = it,
                            pagingKey = pagingKey,
                            useDbKeyInItemKey = useDbKeyInItemKey,
                            translationDisplayOptions = translationDisplayOptions,
                        )
                }
            }
        return when (root) {
            is UiTimelineV2.Feed -> root
            is UiTimelineV2.Message -> root
            is UiTimelineV2.Post -> {
                val resolvedRoot =
                    root.resolveReferences(
                        references = references,
                    )
                val repost =
                    (references.find { it.first == ReferenceType.Retweet }?.second as? UiTimelineV2.Post)
                        ?: resolvedRoot.internalRepost
                val resolvedRepost =
                    repost?.resolveReferences(
                        references = references,
                    )
                if (resolvedRepost != null) {
                    resolvedRepost.copy(
                        internalRepost = resolvedRepost,
                        statusKey = resolvedRoot.statusKey,
                        message = resolvedRoot.message,
                    )
                } else {
                    resolvedRoot
                }
            }
            is UiTimelineV2.User -> root
            is UiTimelineV2.UserList ->
                root.copy(
                    post =
                        root.post?.let { post ->
                            references.map { it.second }.find { it.statusKey == post.statusKey } as? UiTimelineV2.Post ?: post
                        },
                )
        }
    }

    private fun UiTimelineV2.Post.resolveReferences(references: List<Pair<ReferenceType, UiTimelineV2>>): UiTimelineV2.Post =
        copy(
            parents = resolveReferencePosts(ReferenceType.Reply, references, parents),
            quote = resolveReferencePosts(ReferenceType.Quote, references, quote),
            internalRepost =
                resolveReferencePosts(
                    type = ReferenceType.Retweet,
                    references = references,
                    current = internalRepost?.let(::listOf).orEmpty(),
                ).firstOrNull(),
        )

    private fun UiTimelineV2.Post.resolveReferencePosts(
        type: ReferenceType,
        references: List<Pair<ReferenceType, UiTimelineV2>>,
        current: List<UiTimelineV2.Post>,
    ) = if (current.isNotEmpty()) {
        current
            .map { currentPost ->
                references
                    .find { it.first == type && it.second.statusKey == currentPost.statusKey }
                    ?.second as? UiTimelineV2.Post ?: currentPost
            }.toImmutableList()
    } else {
        this.references
            .asSequence()
            .filter { it.type == type }
            .mapNotNull { reference ->
                references
                    .find { it.first == type && it.second.statusKey == reference.statusKey }
                    ?.second as? UiTimelineV2.Post
            }.toList()
            .toImmutableList()
    }

    private fun uiTimelineToDbStatusReferenceWithStatus(
        data: UiTimelineV2,
        referenceType: ReferenceType,
        rootStatusKey: MicroBlogKey,
    ) = DbStatusReferenceWithStatus(
        reference =
            DbStatusReference(
                referenceType = referenceType,
                statusKey = rootStatusKey,
                referenceStatusKey = data.statusKey,
                _id = Uuid.random().toString(),
            ),
        status = uiTimelineToDbStatusWithUser(data, sanitizePostReferences = true),
    )

    private fun collectPostReferences(
        data: UiTimelineV2.Post,
        rootStatusKey: MicroBlogKey,
    ): List<DbStatusReferenceWithStatus> {
        val visited = mutableSetOf<MicroBlogKey>()

        fun visit(post: UiTimelineV2.Post): List<DbStatusReferenceWithStatus> =
            post.directReferencePosts().flatMap { (referenceType, referencedPost) ->
                listOf(
                    uiTimelineToDbStatusReferenceWithStatus(
                        data = referencedPost,
                        referenceType = referenceType,
                        rootStatusKey = rootStatusKey,
                    ),
                ) +
                    if (visited.add(referencedPost.statusKey)) {
                        visit(referencedPost)
                    } else {
                        emptyList()
                    }
            }

        return visit(data)
            .distinctBy {
                it.reference.referenceType to it.reference.referenceStatusKey
            }
    }

    private fun UiTimelineV2.Post.directReferencePosts(): List<Pair<ReferenceType, UiTimelineV2.Post>> =
        quote.map { ReferenceType.Quote to it } +
            parents.map { ReferenceType.Reply to it } +
            listOfNotNull(internalRepost?.let { ReferenceType.Retweet to it })

    private fun uiTimelineToDbStatusWithUser(
        data: UiTimelineV2,
        sanitizePostReferences: Boolean,
    ): DbStatusWithUser =
        DbStatusWithUser(
            data =
                DbStatus(
                    statusKey = data.statusKey,
                    content = if (sanitizePostReferences) data.sanitizeForDatabase() else data,
                    accountType = data.accountType as DbAccountType,
                    text = data.searchText,
                ),
        )

    private fun UiTimelineV2.sanitizeForDatabase(): UiTimelineV2 =
        when (this) {
            is UiTimelineV2.Post ->
                copy(
                    references = directReferences(),
                    quote = persistentListOf(),
                    parents = persistentListOf(),
                    internalRepost = null,
                )
            else -> this
        }

    private fun UiTimelineV2.Post.directReferences() =
        (
            references +
                quote.map {
                    UiTimelineV2.Post.Reference(
                        statusKey = it.statusKey,
                        type = ReferenceType.Quote,
                    )
                } +
                parents.map {
                    UiTimelineV2.Post.Reference(
                        statusKey = it.statusKey,
                        type = ReferenceType.Reply,
                    )
                } +
                listOfNotNull(
                    internalRepost?.let {
                        UiTimelineV2.Post.Reference(
                            statusKey = it.statusKey,
                            type = ReferenceType.Retweet,
                        )
                    },
                )
        ).distinctBy { it.type to it.statusKey }
            .toImmutableList()

    private fun dbStatusWithUserToUiTimeline(
        data: DbStatusWithUser,
        pagingKey: String,
        useDbKeyInItemKey: Boolean,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 {
        val root =
            data.data.content.applyTranslation(
                options = translationDisplayOptions,
                translations = data.translations,
            )
        return when (root) {
            is UiTimelineV2.Feed -> root
            is UiTimelineV2.Message ->
                root.copy(
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
            is UiTimelineV2.Post ->
                root.copy(
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
            is UiTimelineV2.User ->
                root.copy(
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
            is UiTimelineV2.UserList ->
                root.copy(
                    extraKey = if (useDbKeyInItemKey) pagingKey else null,
                )
        }
    }
}
