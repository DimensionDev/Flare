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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiProfile
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

    /**
     * Collapses the transient Room hydration graph into the representation retained by Paging.
     * Status content is materialized once per status and equal profiles are shared across pages.
     */
    fun toPageItems(
        items: List<DbPagingTimelineWithStatus>,
        identities: List<dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity>,
        pagingKey: String,
        canonicalProfiles: MutableMap<MicroBlogKey, UiProfile>,
    ): List<TimelinePageItem> {
        if (items.isEmpty()) {
            return emptyList()
        }
        require(items.size == identities.size)

        val contentByStatusId = LinkedHashMap<String, UiTimelineV2>(items.size * 4)
        val translationsByStatusId = LinkedHashMap<String, List<dev.dimension.flare.data.database.cache.model.DbTranslation>>()

        fun registerStatus(status: DbStatusWithUser?) {
            if (status == null) {
                return
            }
            val data = status.data
            if (data.id !in contentByStatusId) {
                contentByStatusId[data.id] =
                    data.content
                        .canonicalizeProfiles(canonicalProfiles)
                        .withItemKey("${pagingKey}_${data.id}")
            }
            if (status.translations.isNotEmpty()) {
                translationsByStatusId[data.id] = status.translations
            }
        }

        items.forEach { item ->
            registerStatus(DbStatusWithUser(item.statusData, item.statusTranslations))
            item.references.forEach { registerStatus(it.status) }
            item.presentationReferences.forEach { registerStatus(it.status) }
        }

        val sharedTranslations =
            if (translationsByStatusId.isEmpty()) {
                emptyMap()
            } else {
                translationsByStatusId
            }
        return ArrayList<TimelinePageItem>(items.size).also { result ->
            items.forEachIndexed { index, item ->
                val root = contentByStatusId.getValue(item.statusData.id)
                val resolvedRoot = root.resolveSemanticReferences(item.references, contentByStatusId)
                val itemKey = "${pagingKey}_${item.statusData.id}"
                val baseItem =
                    if (resolvedRoot is UiTimelineV2.Post) {
                        UiTimelineV2.TimelinePostItem(
                            post = resolvedRoot,
                            presentation =
                                buildProjectionPresentation(
                                    item = item,
                                    contentByStatusId = contentByStatusId,
                                    canonicalProfiles = canonicalProfiles,
                                ),
                            itemKey = itemKey,
                        )
                    } else {
                        resolvedRoot
                    }
                result +=
                    TimelinePageItem(
                        identity = identities[index],
                        baseItem = baseItem,
                        translationsByStatusId = sharedTranslations,
                    )
            }
        }
    }

    fun rebuildCanonicalProfiles(
        items: Iterable<TimelinePageItem>,
        canonicalProfiles: MutableMap<MicroBlogKey, UiProfile>,
    ) {
        canonicalProfiles.clear()
        items.forEach { item ->
            item.baseItem.collectProfiles { profile ->
                if (profile.key !in canonicalProfiles) {
                    canonicalProfiles[profile.key] = profile
                }
            }
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

    private fun UiTimelineV2.resolveSemanticReferences(
        references: List<DbStatusReferenceWithStatus>,
        contentByStatusId: Map<String, UiTimelineV2>,
    ): UiTimelineV2 {
        val root = if (this is UiTimelineV2.TimelinePostItem) post else this
        if (root !is UiTimelineV2.UserList || root.post == null) {
            return root
        }
        val expectedStatusKey = root.post.statusKey
        val replacement =
            references.firstNotNullOfOrNull { reference ->
                contentByStatusId[reference.reference.referenceStatusId]
                    ?.let { if (it is UiTimelineV2.TimelinePostItem) it.post else it }
                    ?.let { it as? UiTimelineV2.Post }
                    ?.takeIf { it.statusKey == expectedStatusKey }
            }
        return if (replacement == null || replacement === root.post) root else root.copy(post = replacement)
    }

    private fun buildProjectionPresentation(
        item: DbPagingTimelineWithStatus,
        contentByStatusId: Map<String, UiTimelineV2>,
        canonicalProfiles: MutableMap<MicroBlogKey, UiProfile>,
    ): UiTimelineV2.PostPresentation {
        val inlineParents = ArrayList<UiTimelineV2.Post>()
        val quotes = ArrayList<UiTimelineV2.Post>()
        var repost: UiTimelineV2.Post? = null
        val references = item.presentationReferences.inReferenceOrder { it.reference.referenceOrder }
        references.forEach { referenceWithStatus ->
            val reference = referenceWithStatus.reference
            val status =
                contentByStatusId[reference.referenceStatusId]
                    ?.let { if (it is UiTimelineV2.TimelinePostItem) it.post else it }
                    as? UiTimelineV2.Post ?: return@forEach
            when (reference.presentationType) {
                DbTimelineItemPresentationType.InlineParent -> inlineParents += status
                DbTimelineItemPresentationType.Quote -> quotes += status
                DbTimelineItemPresentationType.Repost -> if (repost == null) repost = status
            }
        }
        return UiTimelineV2.PostPresentation(
            message = item.timeline.message?.canonicalizeProfiles(canonicalProfiles) as? UiTimelineV2.Message,
            inlineParents = inlineParents.toImmutableList(),
            quotes = quotes.toImmutableList(),
            repost = repost,
        )
    }

    private fun <T> List<T>.inReferenceOrder(order: (T) -> Int): List<T> {
        for (index in 1 until size) {
            if (order(this[index - 1]) > order(this[index])) {
                return sortedBy(order)
            }
        }
        return this
    }

    private fun UiTimelineV2.canonicalizeProfiles(canonicalProfiles: MutableMap<MicroBlogKey, UiProfile>): UiTimelineV2 =
        when (this) {
            is UiTimelineV2.Feed -> {
                this
            }

            is UiTimelineV2.Message -> {
                val canonicalUser = user?.canonical(canonicalProfiles)
                if (canonicalUser === user) this else copy(user = canonicalUser)
            }

            is UiTimelineV2.Post -> {
                val canonicalUser = user?.canonical(canonicalProfiles)
                if (canonicalUser === user) this else copy(user = canonicalUser)
            }

            is UiTimelineV2.TimelinePostItem -> {
                val canonicalPost = post.canonicalizeProfiles(canonicalProfiles) as UiTimelineV2.Post
                val canonicalMessage = presentation.message?.canonicalizeProfiles(canonicalProfiles) as? UiTimelineV2.Message
                val canonicalParents = presentation.inlineParents.mapProfilesIfChanged(canonicalProfiles)
                val canonicalQuotes = presentation.quotes.mapProfilesIfChanged(canonicalProfiles)
                val canonicalRepost = presentation.repost?.canonicalizeProfiles(canonicalProfiles) as? UiTimelineV2.Post
                if (
                    canonicalPost === post &&
                    canonicalMessage === presentation.message &&
                    canonicalParents === presentation.inlineParents &&
                    canonicalQuotes === presentation.quotes &&
                    canonicalRepost === presentation.repost
                ) {
                    this
                } else {
                    copy(
                        post = canonicalPost,
                        presentation =
                            presentation.copy(
                                message = canonicalMessage,
                                inlineParents = canonicalParents,
                                quotes = canonicalQuotes,
                                repost = canonicalRepost,
                            ),
                    )
                }
            }

            is UiTimelineV2.User -> {
                val canonicalValue = value.canonical(canonicalProfiles)
                val canonicalMessage = message?.canonicalizeProfiles(canonicalProfiles) as? UiTimelineV2.Message
                if (canonicalValue === value && canonicalMessage === message) {
                    this
                } else {
                    copy(value = canonicalValue, message = canonicalMessage)
                }
            }

            is UiTimelineV2.UserList -> {
                var changed = false
                val canonicalUsers =
                    users
                        .map { user ->
                            user.canonical(canonicalProfiles).also { changed = changed || it !== user }
                        }.let { if (changed) it.toImmutableList() else users }
                val canonicalMessage = message?.canonicalizeProfiles(canonicalProfiles) as? UiTimelineV2.Message
                val canonicalPost = post?.canonicalizeProfiles(canonicalProfiles) as? UiTimelineV2.Post
                if (canonicalUsers === users && canonicalMessage === message && canonicalPost === post) {
                    this
                } else {
                    copy(users = canonicalUsers, message = canonicalMessage, post = canonicalPost)
                }
            }
        }

    private fun UiProfile.canonical(canonicalProfiles: MutableMap<MicroBlogKey, UiProfile>): UiProfile {
        val existing = canonicalProfiles[key]
        return when {
            existing == null -> {
                canonicalProfiles[key] = this
                this
            }

            existing == this -> {
                existing
            }

            else -> {
                this
            }
        }
    }

    private fun kotlinx.collections.immutable.ImmutableList<UiTimelineV2.Post>.mapProfilesIfChanged(
        canonicalProfiles: MutableMap<MicroBlogKey, UiProfile>,
    ): kotlinx.collections.immutable.ImmutableList<UiTimelineV2.Post> {
        var changed = false
        val mapped =
            map { post ->
                (post.canonicalizeProfiles(canonicalProfiles) as UiTimelineV2.Post)
                    .also { changed = changed || it !== post }
            }
        return if (changed) mapped.toImmutableList() else this
    }

    private fun UiTimelineV2.collectProfiles(collect: (UiProfile) -> Unit) {
        when (this) {
            is UiTimelineV2.Feed -> {}

            is UiTimelineV2.Message -> {
                user?.let(collect)
            }

            is UiTimelineV2.Post -> {
                user?.let(collect)
            }

            is UiTimelineV2.TimelinePostItem -> {
                post.collectProfiles(collect)
                presentation.message?.collectProfiles(collect)
                presentation.inlineParents.forEach { it.collectProfiles(collect) }
                presentation.quotes.forEach { it.collectProfiles(collect) }
                presentation.repost?.collectProfiles(collect)
            }

            is UiTimelineV2.User -> {
                collect(value)
                message?.collectProfiles(collect)
            }

            is UiTimelineV2.UserList -> {
                users.forEach(collect)
                message?.collectProfiles(collect)
                post?.collectProfiles(collect)
            }
        }
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
