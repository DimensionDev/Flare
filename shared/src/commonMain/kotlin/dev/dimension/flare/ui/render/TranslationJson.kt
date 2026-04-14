package dev.dimension.flare.ui.render

import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class TranslationDocument(
    val version: Int? = 1,
    val targetLanguage: String? = null,
    val blocks: List<TranslationBlock>,
)

@Serializable
internal data class TranslationBlock(
    val id: Int,
    val tokens: List<TranslationToken>,
)

@Serializable
internal data class TranslationToken(
    val id: Int,
    val kind: TranslationTokenKind,
    val text: String,
)

@Serializable
internal enum class TranslationTokenKind {
    Translatable,
    Locked,
}

internal class TranslationFormatException(
    message: String,
) : IllegalArgumentException(message)

private val translationJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

internal fun UiRichText.toTranslationDocument(targetLanguage: String? = null): TranslationDocument {
    val projection = toProjectionBlocks()
    return TranslationDocument(
        version = 1,
        targetLanguage = targetLanguage?.takeIf { it.isNotBlank() },
        blocks =
            projection.map { block ->
                TranslationBlock(
                    id = block.id,
                    tokens =
                        block.pieces.mapNotNull { piece ->
                            when (piece) {
                                is TranslationProjectionPiece.StaticImage -> {
                                    null
                                }

                                is TranslationProjectionPiece.Token -> {
                                    TranslationToken(
                                        id = piece.id,
                                        kind = piece.kind,
                                        text = piece.text,
                                    )
                                }
                            }
                        },
                )
            },
    )
}

internal fun UiRichText.toTranslationJson(targetLanguage: String? = null): String =
    translationJson.encodeToString(toTranslationDocument(targetLanguage))

internal fun UiRichText.applyTranslationJson(json: String): UiRichText =
    applyTranslationDocument(
        runCatching {
            translationJson.decodeFromString(TranslationDocument.serializer(), json)
        }.getOrElse { throwable ->
            throw TranslationFormatException(throwable.message ?: "Failed to parse translation json")
        },
    )

internal fun UiRichText.applyTranslationDocument(document: TranslationDocument): UiRichText {
    val projection = toProjectionBlocks()
    val projectedBlocksByContent = projection.associateBy { it.content }
    val blocksById = document.blocks.associateBy { it.id }
    if (blocksById.size != projection.size) {
        throw TranslationFormatException(
            "Expected ${projection.size} blocks but found ${blocksById.size}",
        )
    }

    val translatedContents =
        renderRuns.map { content ->
            when (content) {
                is RenderContent.BlockImage -> {
                    content
                }

                is RenderContent.Text -> {
                    val projectedBlock =
                        projectedBlocksByContent[content]
                            ?: return@map content
                    val translatedBlock =
                        blocksById[projectedBlock.id]
                            ?: throw TranslationFormatException("Missing block ${projectedBlock.id}")
                    applyTranslatedBlock(projectedBlock, translatedBlock)
                }
            }
        }

    return uiRichTextOf(
        renderRuns = translatedContents,
    )
}

private fun applyTranslatedBlock(
    projectedBlock: TranslationProjectionBlock,
    translatedBlock: TranslationBlock,
): RenderContent.Text {
    val translatedTokens = translatedBlock.tokens.associateBy { it.id }
    val expectedTokens = projectedBlock.pieces.filterIsInstance<TranslationProjectionPiece.Token>()
    if (translatedTokens.size != expectedTokens.size) {
        throw TranslationFormatException(
            "Expected ${expectedTokens.size} tokens in block ${projectedBlock.id} but found ${translatedTokens.size}",
        )
    }

    val translatedRuns =
        buildList {
            projectedBlock.pieces.forEach { piece ->
                when (piece) {
                    is TranslationProjectionPiece.StaticImage -> {
                        add(piece.run)
                    }

                    is TranslationProjectionPiece.Token -> {
                        val translatedToken =
                            translatedTokens[piece.id]
                                ?: throw TranslationFormatException(
                                    "Missing token ${piece.id} in block ${projectedBlock.id}",
                                )
                        if (translatedToken.kind != piece.kind) {
                            throw TranslationFormatException(
                                "Token ${piece.id} in block ${projectedBlock.id} changed kind",
                            )
                        }
                        if (piece.kind == TranslationTokenKind.Locked && translatedToken.text != piece.text) {
                            throw TranslationFormatException(
                                "Locked token ${piece.id} in block ${projectedBlock.id} was modified",
                            )
                        }
                        add(
                            RenderRun.Text(
                                text = translatedToken.text,
                                style = piece.style,
                            ),
                        )
                    }
                }
            }
        }.mergeAdjacentTextRuns()

    return RenderContent.Text(
        runs = translatedRuns.toImmutableList(),
        block = projectedBlock.content.block,
    )
}

private data class TranslationProjectionBlock(
    val id: Int,
    val content: RenderContent.Text,
    val pieces: List<TranslationProjectionPiece>,
)

private sealed interface TranslationProjectionPiece {
    data class Token(
        val id: Int,
        val kind: TranslationTokenKind,
        val style: RenderTextStyle,
        val text: String,
    ) : TranslationProjectionPiece

    data class StaticImage(
        val run: RenderRun.Image,
    ) : TranslationProjectionPiece
}

private val protectedTranslationPattern =
    Regex("""https?://\S+|@[A-Za-z0-9._-]+(?:@[A-Za-z0-9.-]+)?|#[\p{L}\p{N}_]+""")

private fun UiRichText.toProjectionBlocks(): List<TranslationProjectionBlock> =
    buildList {
        var nextBlockId = 0
        renderRuns.forEach { content ->
            when (content) {
                is RenderContent.BlockImage -> {
                    Unit
                }

                is RenderContent.Text -> {
                    val pieces = content.toProjectionPieces()
                    if (pieces.any { it is TranslationProjectionPiece.Token && it.kind == TranslationTokenKind.Translatable }) {
                        add(
                            TranslationProjectionBlock(
                                id = nextBlockId,
                                content = content,
                                pieces = pieces,
                            ),
                        )
                        nextBlockId += 1
                    }
                }
            }
        }
    }

private fun RenderContent.Text.toProjectionPieces(): List<TranslationProjectionPiece> {
    val pieces = mutableListOf<TranslationProjectionPiece>()
    var nextTokenId = 0
    runs.forEach { run ->
        when (run) {
            is RenderRun.Image -> {
                pieces.add(TranslationProjectionPiece.StaticImage(run))
            }

            is RenderRun.Text -> {
                tokenizeTranslationText(run.text, run.style).forEach { (kind, text) ->
                    if (text.isEmpty()) {
                        return@forEach
                    }
                    val last = pieces.lastOrNull()
                    if (last is TranslationProjectionPiece.Token && last.kind == kind && last.style == run.style) {
                        pieces[pieces.lastIndex] = last.copy(text = last.text + text)
                    } else {
                        pieces.add(
                            TranslationProjectionPiece.Token(
                                id = nextTokenId,
                                kind = kind,
                                style = run.style,
                                text = text,
                            ),
                        )
                        nextTokenId += 1
                    }
                }
            }
        }
    }
    return pieces
}

private fun tokenizeTranslationText(
    text: String,
    style: RenderTextStyle,
): List<Pair<TranslationTokenKind, String>> {
    if (text.isEmpty()) {
        return emptyList()
    }
    if (style.code || style.monospace) {
        return listOf(TranslationTokenKind.Locked to text)
    }
    val tokens = mutableListOf<Pair<TranslationTokenKind, String>>()
    var cursor = 0
    protectedTranslationPattern.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            val segment = text.substring(cursor, match.range.first)
            tokens.add(segment.toTranslationToken())
        }
        tokens.add(TranslationTokenKind.Locked to match.value)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        tokens.add(text.substring(cursor).toTranslationToken())
    }
    return tokens.filter { it.second.isNotEmpty() }
}

private fun String.toTranslationToken(): Pair<TranslationTokenKind, String> =
    if (isBlank()) {
        TranslationTokenKind.Locked to this
    } else {
        TranslationTokenKind.Translatable to this
    }

private fun List<RenderRun>.mergeAdjacentTextRuns(): List<RenderRun> {
    val merged = mutableListOf<RenderRun>()
    forEach { run ->
        val last = merged.lastOrNull()
        if (last is RenderRun.Text && run is RenderRun.Text && last.style == run.style) {
            merged[merged.lastIndex] = last.copy(text = last.text + run.text)
        } else {
            merged.add(run)
        }
    }
    return merged
}
