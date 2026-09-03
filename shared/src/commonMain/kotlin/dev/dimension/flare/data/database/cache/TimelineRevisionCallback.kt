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
            statusTrigger("timeline_status_delete_revision", "DELETE", "OLD.id"),
            statusTrigger(
                name = "timeline_status_update_revision",
                event = "UPDATE OF contentFingerprint, renderHash",
                statusId = "NEW.id",
                condition =
                    "WHEN OLD.contentFingerprint IS NOT NEW.contentFingerprint " +
                        "OR OLD.renderHash IS NOT NEW.renderHash",
            ),
            translationTrigger("timeline_translation_insert_revision", "INSERT", "NEW"),
            translationTrigger("timeline_translation_delete_revision", "DELETE", "OLD"),
            translationTrigger(
                name = "timeline_translation_update_revision",
                event = "UPDATE",
                row = "NEW",
                condition =
                    "AND (OLD.status IS NOT NEW.status " +
                        "OR OLD.displayMode IS NOT NEW.displayMode " +
                        "OR OLD.payload IS NOT NEW.payload " +
                        "OR OLD.statusReason IS NOT NEW.statusReason " +
                        "OR OLD.sourceHash IS NOT NEW.sourceHash " +
                        "OR OLD.targetLanguage IS NOT NEW.targetLanguage)",
            ),
            semanticReferenceTrigger("timeline_semantic_reference_delete_revision", "DELETE", "OLD.statusId"),
            semanticReferenceUpdateTrigger(),
            presentationReferenceTrigger(
                "timeline_presentation_reference_delete_revision",
                "DELETE",
                "OLD.pagingKey",
                "OLD.statusId",
            ),
            presentationReferenceUpdateTrigger(),
        )

    private fun statusTrigger(
        name: String,
        event: String,
        statusId: String,
        condition: String = "",
    ): String =
        """
        CREATE TRIGGER IF NOT EXISTS $name
        AFTER $event ON DbStatus
        $condition
        BEGIN
            UPDATE DbPagingTimeline
            SET contentRevision = contentRevision + 1
            WHERE _id IN (
                SELECT timeline._id
                FROM DbPagingTimeline AS timeline
                WHERE timeline.statusId = $statusId
                UNION
                SELECT timeline._id
                FROM DbPagingTimeline AS timeline
                INNER JOIN status_reference AS semantic
                    ON semantic.statusId = timeline.statusId
                WHERE semantic.referenceStatusId = $statusId
                UNION
                SELECT timeline._id
                FROM DbPagingTimeline AS timeline
                INNER JOIN timeline_item_presentation_reference AS presentation
                    ON presentation.pagingKey = timeline.pagingKey
                    AND presentation.statusId = timeline.statusId
                WHERE presentation.referenceStatusId = $statusId
            );
        END
        """.trimIndent()

    private fun translationTrigger(
        name: String,
        event: String,
        row: String,
        condition: String = "",
    ): String =
        """
        CREATE TRIGGER IF NOT EXISTS $name
        AFTER $event ON DbTranslation
        WHEN $row.entityType = 'Status' $condition
        BEGIN
            UPDATE DbPagingTimeline
            SET contentRevision = contentRevision + 1
            WHERE _id IN (
                SELECT timeline._id
                FROM DbPagingTimeline AS timeline
                WHERE timeline.statusId = $row.entityKey
                UNION
                SELECT timeline._id
                FROM DbPagingTimeline AS timeline
                INNER JOIN status_reference AS semantic
                    ON semantic.statusId = timeline.statusId
                WHERE semantic.referenceStatusId = $row.entityKey
                UNION
                SELECT timeline._id
                FROM DbPagingTimeline AS timeline
                INNER JOIN timeline_item_presentation_reference AS presentation
                    ON presentation.pagingKey = timeline.pagingKey
                    AND presentation.statusId = timeline.statusId
                WHERE presentation.referenceStatusId = $row.entityKey
            );
        END
        """.trimIndent()

    private fun semanticReferenceTrigger(
        name: String,
        event: String,
        statusId: String,
    ): String =
        """
        CREATE TRIGGER IF NOT EXISTS $name
        AFTER $event ON status_reference
        BEGIN
            UPDATE DbPagingTimeline
            SET contentRevision = contentRevision + 1
            WHERE statusId = $statusId;
        END
        """.trimIndent()

    private fun semanticReferenceUpdateTrigger(): String =
        """
        CREATE TRIGGER IF NOT EXISTS timeline_semantic_reference_update_revision
        AFTER UPDATE ON status_reference
        BEGIN
            UPDATE DbPagingTimeline
            SET contentRevision = contentRevision + 1
            WHERE statusId = OLD.statusId OR statusId = NEW.statusId;
        END
        """.trimIndent()

    private fun presentationReferenceTrigger(
        name: String,
        event: String,
        pagingKey: String,
        statusId: String,
    ): String =
        """
        CREATE TRIGGER IF NOT EXISTS $name
        AFTER $event ON timeline_item_presentation_reference
        BEGIN
            UPDATE DbPagingTimeline
            SET contentRevision = contentRevision + 1
            WHERE pagingKey = $pagingKey AND statusId = $statusId;
        END
        """.trimIndent()

    private fun presentationReferenceUpdateTrigger(): String =
        """
        CREATE TRIGGER IF NOT EXISTS timeline_presentation_reference_update_revision
        AFTER UPDATE ON timeline_item_presentation_reference
        BEGIN
            UPDATE DbPagingTimeline
            SET contentRevision = contentRevision + 1
            WHERE (pagingKey = OLD.pagingKey AND statusId = OLD.statusId)
                OR (pagingKey = NEW.pagingKey AND statusId = NEW.statusId);
        END
        """.trimIndent()
}
