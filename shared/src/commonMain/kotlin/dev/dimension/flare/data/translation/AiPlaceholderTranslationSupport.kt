package dev.dimension.flare.data.translation

import dev.dimension.flare.ui.render.TranslationBlock
import dev.dimension.flare.ui.render.TranslationDocument
import dev.dimension.flare.ui.render.TranslationToken
import dev.dimension.flare.ui.render.TranslationTokenKind

internal object AiPlaceholderTranslationSupport {
    private val markerPattern = Regex("""\{\{([TL])(\d+)}}""")
    private val blockPattern = Regex("""(?ms)^<<<B(\d+)>>>\n(.*?)\n<<<E\1>>>(?:\n|$)""")
    private val itemPattern = Regex("""(?ms)^<<<I (\S+) ([CS])(?: (.*?))?>>>(?:\n(.*?))?(?=^<<<I |\z)""")
    private val fieldPattern = Regex("""(?ms)^<<<F (content|contentWarning|title|description)>>>\n(.*?)(?=^<<<F |\z)""")

    fun buildPromptTemplate(document: TranslationDocument): String = formatDocumentTemplate(document)

    fun buildPromptTemplate(document: PreTranslationBatchDocument): String = formatBatchTemplate(document)

    fun reconstructDocument(
        sourceDocument: TranslationDocument,
        translatedTemplate: String,
        targetLanguage: String,
    ): TranslationDocument =
        applyTemplateDocument(
            sourceDocument = sourceDocument,
            translatedTemplate = translatedTemplate,
            targetLanguage = targetLanguage,
        )

    fun reconstructBatchDocument(
        sourceDocument: PreTranslationBatchDocument,
        translatedTemplate: String,
        targetLanguage: String,
    ): PreTranslationBatchDocument =
        applyTemplateBatchDocument(
            sourceDocument = sourceDocument,
            translatedTemplate = translatedTemplate,
            targetLanguage = targetLanguage,
        )

    private fun formatDocumentTemplate(document: TranslationDocument): String =
        document.blocks.joinToString("\n") { block ->
            buildString {
                append("<<<B")
                append(block.id)
                append(">>>\n")
                append(toPlaceholderBlock(block))
                append("\n<<<E")
                append(block.id)
                append(">>>")
            }
        }

    private fun formatBatchTemplate(document: PreTranslationBatchDocument): String =
        document.items.joinToString("\n") { item ->
            buildString {
                append("<<<I ")
                append(item.entityKey)
                append(" ")
                append(item.status.toTemplateCode())
                item.reason?.takeIf { it.isNotBlank() }?.let {
                    append(" ")
                    append(it)
                }
                append(">>>")
                if (item.status == PreTranslationBatchItemStatus.Completed) {
                    item.payload?.let { payload ->
                        appendField("content", payload.content)
                        appendField("contentWarning", payload.contentWarning)
                        appendField("title", payload.title)
                        appendField("description", payload.description)
                    }
                }
            }
        }

    private fun StringBuilder.appendField(
        label: String,
        document: TranslationDocument?,
    ) {
        val value = document ?: return
        append("\n<<<F ")
        append(label)
        append(">>>\n")
        append(formatDocumentTemplate(value))
    }

    private fun toPlaceholderBlock(block: TranslationBlock): String =
        buildString {
            block.tokens.forEach { token ->
                append(token.marker())
                if (token.kind == TranslationTokenKind.Translatable) {
                    append(token.text)
                }
            }
        }

    private fun applyTemplateDocument(
        sourceDocument: TranslationDocument,
        translatedTemplate: String,
        targetLanguage: String,
    ): TranslationDocument {
        val translatedBlocks = parseBlockTemplates(translatedTemplate)
        require(translatedBlocks.size == sourceDocument.blocks.size) {
            "Expected ${sourceDocument.blocks.size} blocks but found ${translatedBlocks.size}"
        }
        return sourceDocument.copy(
            targetLanguage = targetLanguage,
            blocks =
                sourceDocument.blocks.map { sourceBlock ->
                    val translatedBlock =
                        translatedBlocks[sourceBlock.id]
                            ?: error("Missing block ${sourceBlock.id}")
                    applyPlaceholderBlock(sourceBlock, translatedBlock)
                },
        )
    }

    private fun applyPlaceholderBlock(
        sourceBlock: TranslationBlock,
        translatedBlock: String,
    ): TranslationBlock {
        val sourceTokensById = sourceBlock.tokens.associateBy { it.id }
        val translatedTextById = mutableMapOf<Int, String>()
        val seenMarkers = mutableSetOf<String>()
        val matches = markerPattern.findAll(translatedBlock).toList()
        require(matches.isNotEmpty()) {
            "No translation markers found in block ${sourceBlock.id}"
        }

        matches.forEachIndexed { index, match ->
            require(match.range.first == 0 || index > 0) {
                "Unexpected text before the first marker in block ${sourceBlock.id}"
            }
            val kind = match.groupValues[1]
            val tokenId = match.groupValues[2].toInt()
            val sourceToken =
                sourceTokensById[tokenId]
                    ?: error("Unknown token $tokenId in block ${sourceBlock.id}")
            val expectedKind =
                when (sourceToken.kind) {
                    TranslationTokenKind.Translatable -> "T"
                    TranslationTokenKind.Locked -> "L"
                }
            require(kind == expectedKind) {
                "Token $tokenId in block ${sourceBlock.id} changed kind"
            }
            require(seenMarkers.add("$kind$tokenId")) {
                "Duplicate token $tokenId in block ${sourceBlock.id}"
            }
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: translatedBlock.length
            val segment = translatedBlock.substring(match.range.last + 1, nextStart)
            if (sourceToken.kind == TranslationTokenKind.Translatable) {
                translatedTextById[tokenId] = segment
            } else {
                require(segment.isEmpty()) {
                    "Locked token $tokenId in block ${sourceBlock.id} was modified"
                }
            }
        }

        require(seenMarkers.size == sourceBlock.tokens.size) {
            "Expected ${sourceBlock.tokens.size} tokens in block ${sourceBlock.id} but found ${seenMarkers.size}"
        }

        return sourceBlock.copy(
            tokens =
                sourceBlock.tokens.map { token ->
                    when (token.kind) {
                        TranslationTokenKind.Locked -> token
                        TranslationTokenKind.Translatable ->
                            token.copy(
                                text =
                                    translatedTextById[token.id]
                                        ?: error("Missing token ${token.id} in block ${sourceBlock.id}"),
                            )
                    }
                },
        )
    }

    private fun parseBlockTemplates(template: String): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        blockPattern.findAll(template).forEach { match ->
            val blockId = match.groupValues[1].toInt()
            require(result.put(blockId, match.groupValues[2]) == null) {
                "Duplicate block $blockId"
            }
        }
        require(result.isNotEmpty()) {
            "No translated blocks found"
        }
        return result
    }

    private fun applyTemplateBatchDocument(
        sourceDocument: PreTranslationBatchDocument,
        translatedTemplate: String,
        targetLanguage: String,
    ): PreTranslationBatchDocument {
        val translatedItems = parseBatchTemplate(translatedTemplate)
        require(translatedItems.size == sourceDocument.items.size) {
            "Expected ${sourceDocument.items.size} translated items but found ${translatedItems.size}"
        }
        return sourceDocument.copy(
            targetLanguage = targetLanguage,
            items =
                sourceDocument.items.map { sourceItem ->
                    val translatedItem =
                        translatedItems[sourceItem.entityKey]
                            ?: error("Missing translated item ${sourceItem.entityKey}")
                    when (translatedItem.status) {
                        PreTranslationBatchItemStatus.Completed ->
                            sourceItem.copy(
                                status = PreTranslationBatchItemStatus.Completed,
                                payload =
                                    applyTemplateBatchPayload(
                                        sourcePayload = sourceItem.payload,
                                        translatedFields = translatedItem.fields,
                                        targetLanguage = targetLanguage,
                                        entityKey = sourceItem.entityKey,
                                    ),
                                reason = translatedItem.reason,
                            )

                        PreTranslationBatchItemStatus.Skipped ->
                            sourceItem.copy(
                                status = PreTranslationBatchItemStatus.Skipped,
                                payload = null,
                                reason = translatedItem.reason,
                            )
                    }
                },
        )
    }

    private fun parseBatchTemplate(template: String): Map<String, ParsedBatchItem> {
        val result = LinkedHashMap<String, ParsedBatchItem>()
        itemPattern.findAll(template).forEach { match ->
            val entityKey = match.groupValues[1]
            val status =
                when (match.groupValues[2]) {
                    "C" -> PreTranslationBatchItemStatus.Completed
                    "S" -> PreTranslationBatchItemStatus.Skipped
                    else -> error("Unsupported status '${match.groupValues[2]}'")
                }
            val reason = match.groupValues[3].ifBlank { null }
            val fields =
                if (status == PreTranslationBatchItemStatus.Completed) {
                    parseFieldTemplates(match.groupValues[4])
                } else {
                    emptyMap()
                }
            require(result.put(entityKey, ParsedBatchItem(status, reason, fields)) == null) {
                "Duplicate translated item $entityKey"
            }
        }
        require(result.isNotEmpty()) {
            "No translated items found"
        }
        return result
    }

    private fun parseFieldTemplates(template: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        fieldPattern.findAll(template).forEach { match ->
            val field = match.groupValues[1]
            require(result.put(field, match.groupValues[2]) == null) {
                "Duplicate translated field $field"
            }
        }
        return result
    }

    private fun applyTemplateBatchPayload(
        sourcePayload: PreTranslationBatchPayload?,
        translatedFields: Map<String, String>,
        targetLanguage: String,
        entityKey: String,
    ): PreTranslationBatchPayload? =
        when {
            sourcePayload == null -> {
                require(translatedFields.isEmpty()) {
                    "Unexpected translated payload for $entityKey"
                }
                null
            }

            else ->
                PreTranslationBatchPayload(
                    content =
                        applyTemplateBatchField(
                            sourceDocument = sourcePayload.content,
                            translatedTemplate = translatedFields["content"],
                            targetLanguage = targetLanguage,
                            entityKey = entityKey,
                            field = "content",
                        ),
                    contentWarning =
                        applyTemplateBatchField(
                            sourceDocument = sourcePayload.contentWarning,
                            translatedTemplate = translatedFields["contentWarning"],
                            targetLanguage = targetLanguage,
                            entityKey = entityKey,
                            field = "contentWarning",
                        ),
                    title =
                        applyTemplateBatchField(
                            sourceDocument = sourcePayload.title,
                            translatedTemplate = translatedFields["title"],
                            targetLanguage = targetLanguage,
                            entityKey = entityKey,
                            field = "title",
                        ),
                    description =
                        applyTemplateBatchField(
                            sourceDocument = sourcePayload.description,
                            translatedTemplate = translatedFields["description"],
                            targetLanguage = targetLanguage,
                            entityKey = entityKey,
                            field = "description",
                        ),
                )
        }

    private fun applyTemplateBatchField(
        sourceDocument: TranslationDocument?,
        translatedTemplate: String?,
        targetLanguage: String,
        entityKey: String,
        field: String,
    ): TranslationDocument? =
        when {
            sourceDocument == null -> {
                require(translatedTemplate == null) {
                    "Unexpected translated field $field for $entityKey"
                }
                null
            }

            translatedTemplate == null -> error("Missing translated field $field for $entityKey")
            else ->
                applyTemplateDocument(
                    sourceDocument = sourceDocument,
                    translatedTemplate = translatedTemplate,
                    targetLanguage = targetLanguage,
                )
        }

    private fun TranslationToken.marker(): String =
        when (kind) {
            TranslationTokenKind.Translatable -> "{{T$id}}"
            TranslationTokenKind.Locked -> "{{L$id}}"
        }

    private fun PreTranslationBatchItemStatus.toTemplateCode(): String =
        when (this) {
            PreTranslationBatchItemStatus.Completed -> "C"
            PreTranslationBatchItemStatus.Skipped -> "S"
        }

    private data class ParsedBatchItem(
        val status: PreTranslationBatchItemStatus,
        val reason: String?,
        val fields: Map<String, String>,
    )
}
