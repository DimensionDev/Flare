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
import dev.dimension.flare.data.database.cache.model.stableDatabaseHash
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
        require(sortIds.isEmpty() || sortIds.size == data.size) {
            "sortIds must be empty or have the same size as data"
        }
        val context = MappingContext()
        val roots = data.map { it.rootTimelineForDatabase() }
        // Roots win when the same status also appears as another row's presentation reference.
        roots.forEach(context::statusForTimeline)
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
        val rootStatus = context.statusForTimeline(root)
        val presentationReferences =
            when {
                root is UiTimelineV2.Post && presentation != null -> {
                    collectPresentationReferences(
                        pagingKey = pagingKey,
                        rootStatusId = rootStatus.data.id,
                        presentation = presentation,
                        context = context,
                    )
                }

                else -> {
                    emptyList()
                }
            }
        val statusReferences =
            when (root) {
                is UiTimelineV2.Post -> {
                    collectPostReferences(
                        root = root,
                        presentation = presentation,
                        rootStatusId = rootStatus.data.id,
                        presentationReferences = presentationReferences,
                    )
                }

                is UiTimelineV2.UserList -> {
                    collectUserListReferences(root, rootStatus.data.id, context)
                }

                else -> {
                    emptyList()
                }
            }
        return DbPagingTimelineWithStatus(
            timeline =
                DbPagingTimeline(
                    pagingKey = pagingKey,
                    statusId = rootStatus.data.id,
                    sortId = sortId ?: SnowflakeIdGenerator.nextId(),
                    message = presentation?.message,
                    statusReferenceHash = statusReferences.statusReferenceStorageHash(),
                    presentationReferenceHash = presentationReferences.presentationReferenceStorageHash(),
                ),
            status =
                DbStatusWithReference(
                    status = rootStatus,
                    references = statusReferences,
                ),
            presentationReferences = presentationReferences,
        )
    }

    fun toUi(
        item: DbPagingTimelineWithStatus,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 {
        val root =
            toUi(
                status = DbStatusWithUser(data = item.statusData, translations = item.statusTranslations),
                references = item.references,
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
                itemKey = "${pagingKey}_${item.statusData.id}",
            )
        } else {
            root
        }
    }

    fun toUi(
        item: DbStatusWithReference,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 =
        toUi(
            status = item.status,
            references = item.references,
            pagingKey = pagingKey,
            translationDisplayOptions = translationDisplayOptions,
        )

    private fun toUi(
        status: DbStatusWithUser,
        references: List<DbStatusReferenceWithStatus>,
        pagingKey: String,
        translationDisplayOptions: TranslationDisplayOptions,
    ): UiTimelineV2 {
        val root =
            dbStatusWithUserToUiTimeline(
                data = status,
                pagingKey = pagingKey,
                translationDisplayOptions = translationDisplayOptions,
            )
        return when (root) {
            is UiTimelineV2.TimelinePostItem -> {
                root.post
            }

            is UiTimelineV2.UserList -> {
                val references =
                    references
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
        val inlineParents = ArrayList<UiTimelineV2.Post>()
        val quotes = ArrayList<UiTimelineV2.Post>()
        var repost: UiTimelineV2.Post? = null
        item.presentationReferences
            .sortedBy { it.reference.referenceOrder }
            .forEach { reference ->
                val post =
                    reference.status?.let {
                        dbStatusWithUserToUiTimeline(
                            data = it,
                            pagingKey = pagingKey,
                            translationDisplayOptions = translationDisplayOptions,
                        ) as? UiTimelineV2.Post
                    } ?: return@forEach
                when (reference.reference.presentationType) {
                    DbTimelineItemPresentationType.InlineParent -> inlineParents += post
                    DbTimelineItemPresentationType.Quote -> quotes += post
                    DbTimelineItemPresentationType.Repost -> if (repost == null) repost = post
                }
            }
        return UiTimelineV2.PostPresentation(
            message = item.timeline.message,
            inlineParents = inlineParents.toImmutableList(),
            quotes = quotes.toImmutableList(),
            repost = repost,
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
                        status = context.statusForPost(it),
                        referenceType = ReferenceType.Quote,
                        rootStatusId = rootStatusId,
                        referenceOrder = 0,
                    ),
                )
            }.orEmpty()

    private fun collectPostReferences(
        root: UiTimelineV2.Post,
        presentation: UiTimelineV2.PostPresentation?,
        rootStatusId: String,
        presentationReferences: List<DbTimelineItemPresentationReferenceWithStatus>,
    ): List<DbStatusReferenceWithStatus> {
        val presentationStatuses =
            presentationReferences
                .mapNotNull { reference ->
                    val referenceType =
                        when (reference.reference.presentationType) {
                            DbTimelineItemPresentationType.Quote -> ReferenceType.Quote
                            DbTimelineItemPresentationType.Repost -> ReferenceType.Retweet
                            DbTimelineItemPresentationType.InlineParent -> null
                        }
                    val status = reference.status
                    if (referenceType == null || status == null) {
                        null
                    } else {
                        (referenceType to reference.reference.referenceStatusId) to status
                    }
                }.toMap()
        val semanticReferences =
            (
                root.references +
                    presentation.orEmptySemanticReferences()
            ).distinctBy { it.type to it.statusKey }
        return semanticReferences
            .mapIndexed { index, reference ->
                val referenceStatusId = DbStatus.createId(root.accountType as DbAccountType, reference.statusKey)
                val status = presentationStatuses[reference.type to referenceStatusId]
                if (status != null) {
                    dbStatusReferenceWithStatus(
                        status = status,
                        referenceType = reference.type,
                        rootStatusId = rootStatusId,
                        referenceOrder = index,
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

    private fun UiTimelineV2.PostPresentation?.orEmptySemanticReferences(): List<UiTimelineV2.Post.Reference> =
        buildList {
            this@orEmptySemanticReferences?.quotes?.forEach {
                add(UiTimelineV2.Post.Reference(statusKey = it.statusKey, type = ReferenceType.Quote))
            }
            this@orEmptySemanticReferences?.repost?.let {
                add(UiTimelineV2.Post.Reference(statusKey = it.statusKey, type = ReferenceType.Retweet))
            }
        }

    private fun List<DbStatusReferenceWithStatus>.statusReferenceStorageHash(): Long {
        val sorted = sortedWith(compareBy({ it.reference.referenceOrder }, { it.reference._id }))
        val parts = ArrayList<String>(sorted.size * 2)
        sorted.forEach { item ->
            parts += item.reference._id
            parts += item.reference.referenceOrder.toString()
        }
        return stableDatabaseHash("status-reference-set", parts)
    }

    private fun List<DbTimelineItemPresentationReferenceWithStatus>.presentationReferenceStorageHash(): Long {
        val sorted = sortedWith(compareBy({ it.reference.referenceOrder }, { it.reference._id }))
        val parts = ArrayList<String>(sorted.size * 2)
        sorted.forEach { item ->
            parts += item.reference._id
            parts += item.reference.referenceOrder.toString()
        }
        return stableDatabaseHash("presentation-reference-set", parts)
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
        status: DbStatusWithUser,
        referenceType: ReferenceType,
        rootStatusId: String,
        referenceOrder: Int,
    ) = DbStatusReferenceWithStatus(
        reference =
            DbStatusReference(
                referenceType = referenceType,
                statusId = rootStatusId,
                referenceStatusId = status.data.id,
                referenceOrder = referenceOrder,
                _id =
                    DbStatusReference.createId(
                        referenceType = referenceType,
                        statusId = rootStatusId,
                        referenceStatusId = status.data.id,
                    ),
            ),
        status = status,
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
                _id =
                    DbStatusReference.createId(
                        referenceType = reference.type,
                        statusId = rootStatusId,
                        referenceStatusId = DbStatus.createId(accountType, reference.statusKey),
                    ),
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
    ): DbTimelineItemPresentationReferenceWithStatus {
        val status = context.statusForPost(post)
        return DbTimelineItemPresentationReferenceWithStatus(
            reference =
                DbTimelineItemPresentationReference(
                    pagingKey = pagingKey,
                    statusId = rootStatusId,
                    referenceStatusId = status.data.id,
                    presentationType = type,
                    referenceOrder = referenceOrder,
                    _id =
                        DbTimelineItemPresentationReference.createId(
                            pagingKey = pagingKey,
                            statusId = rootStatusId,
                            referenceStatusId = status.data.id,
                            presentationType = type,
                        ),
                ),
            status = status,
        )
    }

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
        private val statusById = HashMap<String, DbStatusWithUser>()

        fun statusForTimeline(data: UiTimelineV2): DbStatusWithUser {
            val id = DbStatus.createId(data.accountType as DbAccountType, data.statusKey)
            return statusById.getOrPut(id) { uiTimelineToDbStatusWithUser(data) }
        }

        fun statusForPost(data: UiTimelineV2.Post): DbStatusWithUser {
            val id = DbStatus.createId(data.accountType as DbAccountType, data.statusKey)
            return statusById.getOrPut(id) { uiTimelineToDbStatusWithUser(data.normalizedPost()) }
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
