package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.SnowflakeIdGenerator
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationType
import dev.dimension.flare.data.database.cache.model.TranslationDisplayOptions
import dev.dimension.flare.data.database.cache.model.applyTranslation
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.withItemKey
import kotlinx.collections.immutable.toImmutableList

internal object TimelinePagingMapper {
    suspend fun toDb(
        data: UiTimelineV2,
        pagingKey: String,
        sortId: Long? = null,
    ): DbPagingTimelineWithStatus =
        toDb(
            data = data,
            root = data.rootTimelineForDatabase(),
            pagingKey = pagingKey,
            sortId = sortId,
            context = MappingContext(),
        )

    suspend fun toDb(
        data: List<UiTimelineV2>,
        pagingKey: String,
        sortIds: List<Long?> = emptyList(),
    ): List<DbPagingTimelineWithStatus> {
        require(sortIds.isEmpty() || sortIds.size == data.size)
        val context = MappingContext()
        val roots = data.map { it.rootTimelineForDatabase() }
        roots.forEach(context::status)
        return data.mapIndexed { index, item ->
            toDb(
                data = item,
                root = roots[index],
                pagingKey = pagingKey,
                sortId = sortIds.getOrNull(index),
                context = context,
            )
        }
    }

    private suspend fun toDb(
        data: UiTimelineV2,
        root: UiTimelineV2,
        pagingKey: String,
        sortId: Long?,
        context: MappingContext,
    ): DbPagingTimelineWithStatus {
        val timelineItem = data.asTimelinePostItem()
        val presentation = timelineItem?.presentation
        val rootStatus = context.status(root)
        val semanticReferences =
            when (root) {
                is UiTimelineV2.Post -> collectPostReferences(root, presentation, rootStatus.data.id, context)
                is UiTimelineV2.UserList -> collectUserListReferences(root, rootStatus.data.id, context)
                else -> emptyList()
            }
        val presentationReferences =
            if (root is UiTimelineV2.Post && presentation != null) {
                collectPresentationReferences(
                    pagingKey = pagingKey,
                    rootStatusId = rootStatus.data.id,
                    presentation = presentation,
                    context = context,
                )
            } else {
                emptyList()
            }
        return DbPagingTimelineWithStatus(
            timeline =
                DbPagingTimeline(
                    pagingKey = pagingKey,
                    statusId = rootStatus.data.id,
                    sortId = sortId ?: SnowflakeIdGenerator.nextId(),
                    message = presentation?.message,
                    semanticReferenceSignature =
                        semanticReferenceSignature(semanticReferences.map { it.reference }),
                    presentationReferenceSignature =
                        presentationReferenceSignature(presentationReferences.map { it.reference }),
                ),
            status =
                DbStatusWithReference(
                    status = rootStatus,
                    references = semanticReferences,
                ),
            presentationReferences = presentationReferences,
        )
    }

    private fun semanticReferenceSignature(references: List<DbStatusReference>): String =
        buildString {
            references
                .sortedWith(compareBy(DbStatusReference::referenceType, DbStatusReference::referenceStatusId))
                .forEach { reference ->
                    appendSignatureValue(reference.referenceType.name)
                    appendSignatureValue(reference.referenceStatusId)
                    append(reference.referenceOrder)
                    append(';')
                }
        }

    private fun presentationReferenceSignature(references: List<DbTimelineItemPresentationReference>): String =
        buildString {
            references
                .sortedWith(
                    compareBy(
                        DbTimelineItemPresentationReference::presentationType,
                        DbTimelineItemPresentationReference::referenceStatusId,
                    ),
                ).forEach { reference ->
                    appendSignatureValue(reference.presentationType.name)
                    appendSignatureValue(reference.referenceStatusId)
                    append(reference.referenceOrder)
                    append(';')
                }
        }

    private fun StringBuilder.appendSignatureValue(value: String) {
        append(value.length)
        append(':')
        append(value)
        append(':')
    }

    fun toUi(
        item: DbPagingTimelineWithStatus,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 {
        val root =
            toUi(
                item = item.status,
                pagingKey = pagingKey,
                translationDisplayOptions = translationDisplayOptions,
            )
        return if (root is UiTimelineV2.Post) {
            UiTimelineV2.TimelinePostItem(
                post = root,
                presentation =
                    buildPresentation(
                        item = item,
                        pagingKey = pagingKey,
                        translationDisplayOptions = translationDisplayOptions,
                    ),
                itemKey = "${pagingKey}_${item.status.status.data.id}",
            )
        } else {
            root
        }
    }

    fun toUi(
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
        return when (root) {
            is UiTimelineV2.TimelinePostItem -> {
                root.post
            }

            is UiTimelineV2.UserList -> {
                val references =
                    item.references
                        .sortedBy { it.reference.referenceOrder }
                        .mapNotNull { reference ->
                            reference.status?.let {
                                dbStatusWithUserToUiTimeline(
                                    data = it,
                                    pagingKey = pagingKey,
                                    translationDisplayOptions = translationDisplayOptions,
                                ) as? UiTimelineV2.Post
                            }
                        }
                root.copy(
                    post = root.post?.let { post -> references.find { it.statusKey == post.statusKey } ?: post },
                )
            }

            else -> {
                root
            }
        }
    }

    private fun buildPresentation(
        item: DbPagingTimelineWithStatus,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2.PostPresentation {
        val references =
            item.presentationReferences
                .sortedBy { it.reference.referenceOrder }
                .mapNotNull { reference ->
                    reference.status?.let {
                        reference.reference.presentationType to
                            dbStatusWithUserToUiTimeline(
                                data = it,
                                pagingKey = pagingKey,
                                translationDisplayOptions = translationDisplayOptions,
                            ) as? UiTimelineV2.Post
                    }
                }
        return UiTimelineV2.PostPresentation(
            message = item.timeline.message,
            inlineParents =
                references
                    .filter { it.first == DbTimelineItemPresentationType.InlineParent }
                    .mapNotNull { it.second }
                    .toImmutableList(),
            quotes =
                references
                    .filter { it.first == DbTimelineItemPresentationType.Quote }
                    .mapNotNull { it.second }
                    .toImmutableList(),
            repost =
                references
                    .firstOrNull { it.first == DbTimelineItemPresentationType.Repost }
                    ?.second,
        )
    }

    private fun UiTimelineV2.rootTimelineForDatabase(): UiTimelineV2 =
        when (this) {
            is UiTimelineV2.TimelinePostItem -> post.normalizedPost()
            is UiTimelineV2.Post -> normalizedPost()
            else -> this
        }

    private fun UiTimelineV2.Post.normalizedPost(): UiTimelineV2.Post =
        copy(
            references = references.distinctBy { it.type to it.statusKey }.toImmutableList(),
        )

    private fun collectUserListReferences(
        data: UiTimelineV2.UserList,
        rootStatusId: String,
        context: MappingContext,
    ): List<DbStatusReferenceWithStatus> =
        data.post
            ?.let {
                listOf(
                    dbStatusReferenceWithStatus(
                        post = it,
                        referenceType = ReferenceType.Quote,
                        rootStatusId = rootStatusId,
                        referenceOrder = 0,
                        context = context,
                    ),
                )
            }.orEmpty()

    private fun collectPostReferences(
        root: UiTimelineV2.Post,
        presentation: UiTimelineV2.PostPresentation?,
        rootStatusId: String,
        context: MappingContext,
    ): List<DbStatusReferenceWithStatus> {
        val presentationPosts =
            buildMap {
                presentation?.quotes?.forEach {
                    put(ReferenceType.Quote to it.statusKey, it)
                }
                presentation?.repost?.let {
                    put(ReferenceType.Retweet to it.statusKey, it)
                }
            }
        val semanticReferences =
            (
                root.references +
                    presentationPosts.map { (key, _) ->
                        UiTimelineV2.Post.Reference(
                            statusKey = key.second,
                            type = key.first,
                        )
                    }
            ).distinctBy { it.type to it.statusKey }
        return semanticReferences
            .mapIndexed { index, reference ->
                val post = presentationPosts[reference.type to reference.statusKey]
                if (post != null) {
                    dbStatusReferenceWithStatus(
                        post = post,
                        referenceType = reference.type,
                        rootStatusId = rootStatusId,
                        referenceOrder = index,
                        context = context,
                    )
                } else {
                    dbStatusReferenceWithStatus(
                        reference = reference,
                        accountType = root.accountType as DbAccountType,
                        rootStatusId = rootStatusId,
                        referenceOrder = index,
                    )
                }
            }.distinctBy {
                it.reference.referenceType to it.reference.referenceStatusId
            }
    }

    private fun collectPresentationReferences(
        pagingKey: String,
        rootStatusId: String,
        presentation: UiTimelineV2.PostPresentation,
        context: MappingContext,
    ): List<DbTimelineItemPresentationReferenceWithStatus> {
        var order = 0
        return buildList {
            presentation.inlineParents.forEach { post ->
                add(
                    presentationReferenceWithStatus(
                        pagingKey,
                        rootStatusId,
                        post,
                        DbTimelineItemPresentationType.InlineParent,
                        order++,
                        context,
                    ),
                )
            }
            presentation.quotes.forEach { post ->
                add(
                    presentationReferenceWithStatus(
                        pagingKey,
                        rootStatusId,
                        post,
                        DbTimelineItemPresentationType.Quote,
                        order++,
                        context,
                    ),
                )
            }
            presentation.repost?.let { post ->
                add(
                    presentationReferenceWithStatus(
                        pagingKey,
                        rootStatusId,
                        post,
                        DbTimelineItemPresentationType.Repost,
                        order++,
                        context,
                    ),
                )
            }
        }.distinctBy {
            it.reference.presentationType to it.reference.referenceStatusId
        }
    }

    private fun dbStatusReferenceWithStatus(
        post: UiTimelineV2.Post,
        referenceType: ReferenceType,
        rootStatusId: String,
        referenceOrder: Int,
        context: MappingContext,
    ) = DbStatusReferenceWithStatus(
        reference =
            DbStatusReference(
                referenceType = referenceType,
                statusId = rootStatusId,
                referenceStatusId = DbStatus.createId(post.accountType as DbAccountType, post.statusKey),
                referenceOrder = referenceOrder,
            ),
        status = context.status(post.normalizedPost()),
    )

    private fun dbStatusReferenceWithStatus(
        reference: UiTimelineV2.Post.Reference,
        accountType: DbAccountType,
        rootStatusId: String,
        referenceOrder: Int,
    ) = DbStatusReferenceWithStatus(
        reference =
            DbStatusReference(
                referenceType = reference.type,
                statusId = rootStatusId,
                referenceStatusId = DbStatus.createId(accountType, reference.statusKey),
                referenceOrder = referenceOrder,
            ),
        status = null,
    )

    private fun presentationReferenceWithStatus(
        pagingKey: String,
        rootStatusId: String,
        post: UiTimelineV2.Post,
        type: DbTimelineItemPresentationType,
        referenceOrder: Int,
        context: MappingContext,
    ) = DbTimelineItemPresentationReferenceWithStatus(
        reference =
            DbTimelineItemPresentationReference(
                pagingKey = pagingKey,
                statusId = rootStatusId,
                referenceStatusId = DbStatus.createId(post.accountType as DbAccountType, post.statusKey),
                presentationType = type,
                referenceOrder = referenceOrder,
            ),
        status = context.status(post.normalizedPost()),
    )

    private fun uiTimelineToDbStatusWithUser(data: UiTimelineV2): DbStatusWithUser =
        DbStatusWithUser(
            data =
                DbStatus(
                    statusKey = data.statusKey,
                    content = data,
                    renderHash = data.renderHash,
                    accountType = data.accountType as DbAccountType,
                    text = data.searchText,
                ),
        )

    private class MappingContext {
        private val statusById = mutableMapOf<String, DbStatusWithUser>()

        fun status(data: UiTimelineV2): DbStatusWithUser {
            val id = DbStatus.createId(data.accountType as DbAccountType, data.statusKey)
            return statusById.getOrPut(id) { uiTimelineToDbStatusWithUser(data) }
        }
    }

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
