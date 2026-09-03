package dev.dimension.flare.data.database.cache

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

internal object TimelineRevisionCallback : RoomDatabase.Callback() {
    override suspend fun onOpen(connection: SQLiteConnection) {
        TRIGGERS.forEach { connection.executeSQL(it) }
    }

    private val TRIGGERS =
        listOf(
            contentTrigger("timeline_status_insert_revision", "INSERT", "DbStatus", "NEW.id"),
            contentTrigger("timeline_status_delete_revision", "DELETE", "DbStatus", "OLD.id"),
            contentTrigger(
                name = "timeline_status_update_revision",
                event = "UPDATE OF contentFingerprint, renderHash",
                table = "DbStatus",
                contentId = "NEW.id",
                condition =
                    "WHEN OLD.contentFingerprint IS NOT NEW.contentFingerprint " +
                        "OR OLD.renderHash IS NOT NEW.renderHash",
            ),
            contentTrigger(
                "timeline_translation_insert_revision",
                "INSERT",
                "DbTranslation",
                "NEW.entityKey",
                "WHEN NEW.entityType = 'Status'",
            ),
            contentTrigger(
                "timeline_translation_delete_revision",
                "DELETE",
                "DbTranslation",
                "OLD.entityKey",
                "WHEN OLD.entityType = 'Status'",
            ),
            contentTrigger(
                name = "timeline_translation_update_revision",
                event = "UPDATE",
                table = "DbTranslation",
                contentId = "NEW.entityKey",
                condition =
                    "WHEN NEW.entityType = 'Status' AND (OLD.status IS NOT NEW.status " +
                        "OR OLD.displayMode IS NOT NEW.displayMode " +
                        "OR OLD.payload IS NOT NEW.payload " +
                        "OR OLD.statusReason IS NOT NEW.statusReason " +
                        "OR OLD.sourceHash IS NOT NEW.sourceHash " +
                        "OR OLD.targetLanguage IS NOT NEW.targetLanguage)",
            ),
            referenceTrigger(
                "timeline_semantic_reference_delete_revision",
                "DELETE",
                "status_reference",
                "statusId = OLD.statusId",
            ),
            referenceTrigger(
                "timeline_semantic_reference_update_revision",
                "UPDATE",
                "status_reference",
                "statusId = OLD.statusId OR statusId = NEW.statusId",
            ),
            referenceTrigger(
                "timeline_presentation_reference_delete_revision",
                "DELETE",
                "timeline_item_presentation_reference",
                "pagingKey = OLD.pagingKey AND statusId = OLD.statusId",
            ),
            referenceTrigger(
                "timeline_presentation_reference_update_revision",
                "UPDATE",
                "timeline_item_presentation_reference",
                "(pagingKey = OLD.pagingKey AND statusId = OLD.statusId) " +
                    "OR (pagingKey = NEW.pagingKey AND statusId = NEW.statusId)",
            ),
        )

    private fun contentTrigger(
        name: String,
        event: String,
        table: String,
        contentId: String,
        condition: String = "",
    ): String =
        """
        CREATE TRIGGER IF NOT EXISTS $name
        AFTER $event ON $table
        $condition
        BEGIN
            ${contentRevisionUpdate(contentId)}
        END
        """.trimIndent()

    private fun contentRevisionUpdate(contentId: String): String =
        """
        UPDATE DbPagingTimeline
        SET contentRevision = contentRevision + 1
        WHERE _id IN (
            SELECT timeline._id
            FROM DbPagingTimeline AS timeline
            WHERE timeline.statusId = $contentId
            UNION
            SELECT timeline._id
            FROM DbPagingTimeline AS timeline
            INNER JOIN status_reference AS semantic
                ON semantic.statusId = timeline.statusId
            WHERE semantic.referenceStatusId = $contentId
            UNION
            SELECT timeline._id
            FROM DbPagingTimeline AS timeline
            INNER JOIN timeline_item_presentation_reference AS presentation
                ON presentation.pagingKey = timeline.pagingKey
                AND presentation.statusId = timeline.statusId
            WHERE presentation.referenceStatusId = $contentId
        );
        """.trimIndent()

    private fun referenceTrigger(
        name: String,
        event: String,
        table: String,
        condition: String,
    ): String =
        """
        CREATE TRIGGER IF NOT EXISTS $name
        AFTER $event ON $table
        BEGIN
            UPDATE DbPagingTimeline
            SET contentRevision = contentRevision + 1
            WHERE $condition;
        END
        """.trimIndent()
}
