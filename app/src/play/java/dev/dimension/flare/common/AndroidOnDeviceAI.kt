package dev.dimension.flare.common

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AndroidOnDeviceAI(
    private val context: Context,
) : OnDeviceAI {
    override suspend fun isAvailable(): Boolean =
        runCatching {
            val summarizer = createSummarizer("en")
            try {
                val status = summarizer.checkFeatureStatus().await()
                status == FeatureStatus.AVAILABLE ||
                    status == FeatureStatus.DOWNLOADABLE ||
                    status == FeatureStatus.DOWNLOADING
            } finally {
                summarizer.close()
            }
        }.getOrDefault(false)

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        runCatching {
            val model = Generation.getClient()
            try {
                if (!ensureModelReady(model)) {
                    return@runCatching null
                }
                model
                    .generateContent(prompt)
                    .candidates
                    .firstOrNull()
                    ?.text
                    ?.trim()
                    .takeUnless { it.isNullOrBlank() }
            } finally {
                model.close()
            }
        }.getOrNull()

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val summary =
            runCatching {
                val summarizer = createSummarizer(targetLanguage)
                try {
                    val status = summarizer.checkFeatureStatus().await()
                    if (status == FeatureStatus.UNAVAILABLE) {
                        return@runCatching null
                    }
                    if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                        summarizer
                            .downloadFeature(
                                object : com.google.mlkit.genai.common.DownloadCallback {
                                    override fun onDownloadCompleted() = Unit

                                    override fun onDownloadFailed(e: com.google.mlkit.genai.common.GenAiException) = Unit

                                    override fun onDownloadProgress(bytesDownloaded: Long) = Unit

                                    override fun onDownloadStarted(bytesToDownload: Long) = Unit
                                },
                            ).get()
                    }
                    summarizer.prepareInferenceEngine().get()
                    summarizer
                        .runInference(SummarizationRequest.builder(source).build())
                        .get()
                        .summary
                        .trim()
                } finally {
                    summarizer.close()
                }
            }.getOrNull()
        if (!summary.isNullOrBlank()) {
            return summary
        }
        return translate(
            source = source,
            targetLanguage = targetLanguage,
            prompt = prompt,
        )
    }

    private suspend fun ensureModelReady(model: com.google.mlkit.genai.prompt.GenerativeModel): Boolean {
        val status = model.checkStatus()
        if (status == FeatureStatus.AVAILABLE) {
            return true
        }
        if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
            model.download().collect()
            return model.checkStatus() == FeatureStatus.AVAILABLE
        }
        return false
    }

    private fun createSummarizer(targetLanguage: String): com.google.mlkit.genai.summarization.Summarizer {
        val language =
            when (targetLanguage.substringBefore('-').substringBefore('_').lowercase()) {
                "ja" -> SummarizerOptions.Language.JAPANESE
                "ko" -> SummarizerOptions.Language.KOREAN
                else -> SummarizerOptions.Language.ENGLISH
            }
        val options =
            SummarizerOptions
                .builder(context)
                .setLanguage(language)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLongInputAutoTruncationEnabled(true)
                .build()
        return Summarization.getClient(options)
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                runCatching { get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            Executor(Runnable::run),
        )
        continuation.invokeOnCancellation {
            cancel(true)
        }
    }