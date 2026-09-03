package dev.dimension.flare.data.database.cache

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

/** Installs mutation triggers that keep timeline dependency revisions current. */
internal object TimelineDependencyRevisionCallback : RoomDatabase.Callback() {
    override suspend fun onOpen(connection: SQLiteConnection) {
        TIMELINE_DEPENDENCY_TRIGGER_SQL.forEach { connection.executeSQL(it) }
    }
}

private val TIMELINE_DEPENDENCY_TRIGGER_SQL =
    listOf(
        statusTrigger(
            name = "timeline_dependency_status_update",
            event = "AFTER UPDATE OF contentHash, renderHash, text",
            row = "NEW",
            condition =
                "WHEN OLD.contentHash IS NOT NEW.contentHash " +
                    "OR OLD.renderHash IS NOT NEW.renderHash " +
                    "OR OLD.text IS NOT NEW.text",
        ),
        statusTrigger(
            name = "timeline_dependency_status_delete",
            event = "AFTER DELETE",
            row = "OLD",
        ),
        translationTrigger(
            name = "timeline_dependency_translation_insert",
            event = "AFTER INSERT",
            row = "NEW",
        ),
        translationTrigger(
            name = "timeline_dependency_translation_update_old",
            event = "AFTER UPDATE OF entityType, entityKey, revision",
            row = "OLD",
            condition =
                "WHEN OLD.entityType = 'Status' AND (" +
                    "OLD.entityType IS NOT NEW.entityType OR " +
                    "OLD.entityKey IS NOT NEW.entityKey OR " +
                    "OLD.revision IS NOT NEW.revision)",
        ),
        translationTrigger(
            name = "timeline_dependency_translation_update_new",
            event = "AFTER UPDATE OF entityType, entityKey, revision",
            row = "NEW",
            condition =
                "WHEN NEW.entityType = 'Status' AND (" +
                    "OLD.entityType IS NOT NEW.entityType OR " +
                    "OLD.entityKey IS NOT NEW.entityKey OR " +
                    "OLD.revision IS NOT NEW.revision)",
        ),
        translationTrigger(
            name = "timeline_dependency_translation_delete",
            event = "AFTER DELETE",
            row = "OLD",
        ),
    )

private fun statusTrigger(
    name: String,
    event: String,
    row: String,
    condition: String = "",
): String =
    """
    CREATE TRIGGER IF NOT EXISTS $name
    $event ON DbStatus
    $condition
    BEGIN
        ${dependencyRevisionUpdate("$row.id")}
    END
    """.trimIndent()

private fun translationTrigger(
    name: String,
    event: String,
    row: String,
    condition: String = "WHEN $row.entityType = 'Status'",
): String =
    """
    CREATE TRIGGER IF NOT EXISTS $name
    $event ON DbTranslation
    $condition
    BEGIN
        ${dependencyRevisionUpdate("$row.entityKey")}
    END
    """.trimIndent()

private fun dependencyRevisionUpdate(statusId: String): String =
    """
    UPDATE DbPagingTimeline
    SET dependencyRevision = dependencyRevision + 1
    WHERE _id IN (
        SELECT timeline._id
        FROM DbPagingTimeline AS timeline
        WHERE timeline.statusId = $statusId
        UNION
        SELECT timeline._id
        FROM status_reference AS reference
        INNER JOIN DbPagingTimeline AS timeline ON timeline.statusId = reference.statusId
        WHERE reference.referenceStatusId = $statusId
        UNION
        SELECT timeline._id
        FROM timeline_item_presentation_reference AS reference
        INNER JOIN DbPagingTimeline AS timeline
            ON timeline.pagingKey = reference.pagingKey AND timeline.statusId = reference.statusId
        WHERE reference.referenceStatusId = $statusId
    );
    """.trimIndent()
