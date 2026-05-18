package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.common.SerializableImmutableList
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
import dev.dimension.flare.ui.model.withItemKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.uuid.Uuid

public object TimelinePagingMapper {
    public suspend fun toDb(
        data: UiTimelineV2,
        pagingKey: String,
        sortId: Long? = null,
    ): DbPagingTimelineWithStatus {
        val rootStatus = uiTimelineToDbStatusWithUser(data, sanitizePostReferences = true)
        return DbPagingTimelineWithStatus(
            timeline =
                DbPagingTimeline(
                    pagingKey = pagingKey,
                    statusId = rootStatus.data.id,
                    sortId = sortId ?: SnowflakeIdGenerator.nextId(),
                ),
            status =
                DbStatusWithReference(
                    status = rootStatus,
                    references =
                        when (data) {
                            is UiTimelineV2.Feed -> {
                                emptyList()
                            }

                            is UiTimelineV2.Message -> {
                                emptyList()
                            }

                            is UiTimelineV2.Post -> {
                                collectPostReferences(data, rootStatus.data.id)
                            }

                            is UiTimelineV2.User -> {
                                emptyList()
                            }

                            is UiTimelineV2.UserList -> {
                                data.post
                                    ?.let {
                                        listOf(
                                            uiTimelineToDbStatusReferenceWithStatus(
                                                data = it,
                                                referenceType = ReferenceType.Quote,
                                                rootStatusId = rootStatus.data.id,
                                            ),
                                        ) + collectPostReferences(it, rootStatus.data.id)
                                    }.orEmpty()
                            }
                        },
                ),
        )
    }

    public fun toUi(
        item: DbPagingTimelineWithStatus,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 =
        toUi(
            item = item.status,
            pagingKey = pagingKey,
            translationDisplayOptions = translationDisplayOptions,
        )

    public fun toUi(
        item: DbStatusWithReference,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 {
        val root =
            dbStatusWithUserToUiTimeline(
                data = item.status,
                pagingKey = pagingKey,
                translationDisplayOptions = translationDisplayOptions,
            )
        val references =
            item.references.mapNotNull { reference ->
                reference.status?.let {
                    reference.reference.referenceType to
                        dbStatusWithUserToUiTimeline(
                            data = it,
                            pagingKey = pagingKey,
                            translationDisplayOptions = translationDisplayOptions,
                        )
                }
            }
        return when (root) {
            is UiTimelineV2.Feed -> {
                root
            }

            is UiTimelineV2.Message -> {
                root
            }

            is UiTimelineV2.Post -> {
                val referenceResolver = ReferenceResolver(references)
                val resolvedRoot =
                    referenceResolver.resolve(root)
                val repost =
                    (references.find { it.first == ReferenceType.Retweet }?.second as? UiTimelineV2.Post)
                        ?: resolvedRoot.internalRepost
                val resolvedRepost =
                    repost?.let(referenceResolver::resolve)
                if (resolvedRepost != null) {
                    resolvedRepost.copy(
                        internalRepost = resolvedRepost,
                        statusKey = resolvedRoot.statusKey,
                        message = resolvedRoot.message,
                        itemKey = resolvedRoot.itemKey,
                    )
                } else {
                    resolvedRoot
                }
            }

            is UiTimelineV2.User -> {
                root
            }

            is UiTimelineV2.UserList -> {
                root.copy(
                    post =
                        root.post?.let { post ->
                            references.map { it.second }.find { it.statusKey == post.statusKey } as? UiTimelineV2.Post ?: post
                        },
                )
            }
        }
    }

    private class ReferenceResolver(
        references: List<Pair<ReferenceType, UiTimelineV2>>,
    ) {
        private val referencePosts =
            references
                .mapNotNull { (type, timeline) ->
                    (timeline as? UiTimelineV2.Post)?.let { post ->
                        type to post.statusKey to post
                    }
                }.toMap()
        private val resolvedPosts = mutableMapOf<MicroBlogKey, UiTimelineV2.Post>()
        private val resolvingKeys = mutableSetOf<MicroBlogKey>()

        fun resolve(post: UiTimelineV2.Post): UiTimelineV2.Post {
            resolvedPosts[post.statusKey]?.let {
                return it
            }

            val stack = mutableListOf(ResolveFrame(post))
            while (stack.isNotEmpty()) {
                val frame = stack.last()
                val current = frame.post
                val currentKey = current.statusKey
                resolvedPosts[currentKey]?.let {
                    resolvingKeys -= currentKey
                    stack.removeLast()
                    continue
                }

                if (!frame.expanded) {
                    if (!resolvingKeys.add(currentKey)) {
                        stack.removeLast()
                        continue
                    }
                    frame.expanded = true
                    current
                        .directReferencePosts()
                        .asReversed()
                        .forEach { referencedPost ->
                            val referencedKey = referencedPost.statusKey
                            if (referencedKey !in resolvedPosts && referencedKey !in resolvingKeys) {
                                stack += ResolveFrame(referencedPost)
                            }
                        }
                    continue
                }

                val resolved =
                    current.copy(
                        parents = current.resolveReferencePosts(ReferenceType.Reply),
                        quote = current.resolveReferencePosts(ReferenceType.Quote),
                        internalRepost = current.resolveReferencePosts(ReferenceType.Retweet).firstOrNull(),
                    )
                resolvedPosts[currentKey] = resolved
                resolvingKeys -= currentKey
                stack.removeLast()
            }

            return resolvedPosts[post.statusKey] ?: post
        }

        private fun UiTimelineV2.Post.resolveReferencePosts(type: ReferenceType): SerializableImmutableList<UiTimelineV2.Post> =
            directReferencePosts(type)
                .map { referencedPost ->
                    resolvedPosts[referencedPost.statusKey] ?: referencedPost
                }.toImmutableList()

        private fun UiTimelineV2.Post.directReferencePosts(): List<UiTimelineV2.Post> =
            directReferencePosts(ReferenceType.Reply) +
                directReferencePosts(ReferenceType.Quote) +
                directReferencePosts(ReferenceType.Retweet)

        private fun UiTimelineV2.Post.directReferencePosts(type: ReferenceType): List<UiTimelineV2.Post> {
            val current =
                when (type) {
                    ReferenceType.Reply -> parents
                    ReferenceType.Quote -> quote
                    ReferenceType.Retweet -> internalRepost?.let(::listOf).orEmpty()
                    ReferenceType.Notification -> emptyList()
                }
            return if (current.isNotEmpty()) {
                current.map { currentPost ->
                    referencePosts[type to currentPost.statusKey] ?: currentPost
                }
            } else {
                references
                    .asSequence()
                    .filter { it.type == type }
                    .mapNotNull { reference ->
                        referencePosts[type to reference.statusKey]
                    }.toList()
            }
        }

        private data class ResolveFrame(
            val post: UiTimelineV2.Post,
            var expanded: Boolean = false,
        )
    }

    private fun uiTimelineToDbStatusReferenceWithStatus(
        data: UiTimelineV2,
        referenceType: ReferenceType,
        rootStatusId: String,
    ) = DbStatusReferenceWithStatus(
        reference =
            DbStatusReference(
                referenceType = referenceType,
                statusId = rootStatusId,
                referenceStatusId = DbStatus.createId(data.accountType as DbAccountType, data.statusKey),
                _id = Uuid.random().toString(),
            ),
        status = uiTimelineToDbStatusWithUser(data, sanitizePostReferences = true),
    )

    private fun collectPostReferences(
        data: UiTimelineV2.Post,
        rootStatusId: String,
    ): List<DbStatusReferenceWithStatus> {
        val visited = mutableSetOf<MicroBlogKey>()

        fun visit(post: UiTimelineV2.Post): List<DbStatusReferenceWithStatus> =
            post.directReferencePosts().flatMap { (referenceType, referencedPost) ->
                listOf(
                    uiTimelineToDbStatusReferenceWithStatus(
                        data = referencedPost,
                        referenceType = referenceType,
                        rootStatusId = rootStatusId,
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
                it.reference.referenceType to it.reference.referenceStatusId
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
            is UiTimelineV2.Post -> {
                copy(
                    references = directReferences(),
                    quote = persistentListOf(),
                    parents = persistentListOf(),
                    internalRepost = null,
                )
            }

            else -> {
                this
            }
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
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 {
        val rootItemKey = "${pagingKey}_${data.data.id}"
        val root =
            data.data.content.applyTranslation(
                options = translationDisplayOptions,
                translations = data.translations,
            )
        return root.withItemKey(rootItemKey)
    }
}
