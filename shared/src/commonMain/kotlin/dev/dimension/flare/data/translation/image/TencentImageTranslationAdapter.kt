package dev.dimension.flare.data.translation.image

import dev.dimension.flare.data.datastore.model.ImageTranslationConfig
import dev.dimension.flare.data.datastore.model.ImageTranslationProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock
import kotlin.time.Instant

internal class TencentImageTranslationAdapter(
    private val client: HttpClient,
) : ImageTranslationAdapter {
    override val provider = ImageTranslationProvider.Tencent
    private val mutex = Mutex()
    private var lastRequest = 0L

    override suspend fun translate(
        input: ImageTranslationInput,
        config: ImageTranslationConfig,
        target: String,
    ): ImageTranslationOutput =
        mutex.withLock {
            val encoded = input.prepared().readBytes(7L * 1024 * 1024).base64()
            if (encoded.length > 9_000_000) throw ImageTranslationException(ImageTranslationError.UnsupportedInput)
            delay((lastRequest + 1000 - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0))
            val body =
                buildJsonObject {
                    put("Target", imageTranslationLanguageCode(target))
                    put("Mode", config.value("mode").toIntOrNull()?.takeIf { it in 0..1 } ?: 0)
                    put("Data", encoded)
                }
            val timestamp = Clock.System.now().epochSeconds
            lastRequest = Clock.System.now().toEpochMilliseconds()
            val result =
                client
                    .imagePost("https://tmt.tencentcloudapi.com/", body) {
                        header("X-TC-Action", "ImageTranslateLLM")
                        header("X-TC-Version", "2018-03-21")
                        header("X-TC-Region", config.value("region"))
                        header("X-TC-Timestamp", timestamp)
                        header(
                            "Authorization",
                            tencentImageAuthorization(config.value("secretId"), config.value("secretKey"), timestamp, body.toString()),
                        )
                    }
            val response = result.body["Response"]?.jsonObject ?: throw result.failure()
            response["Error"]?.let { error ->
                val code = error.jsonObject.text("Code").orEmpty()
                throw ImageTranslationException(
                    when {
                        code.startsWith("AuthFailure") || code.startsWith("Unauthorized") -> ImageTranslationError.Authentication
                        code.startsWith("RequestLimit") || code.startsWith("LimitExceeded") -> ImageTranslationError.RateLimited
                        else -> ImageTranslationError.Rejected
                    },
                    diagnostics = result.diagnostics,
                )
            }
            response.text("Data")?.let(ImageTranslationOutput::Encoded)
                ?: throw result.failure()
        }
}

internal fun tencentImageAuthorization(
    secretId: String,
    secretKey: String,
    timestamp: Long,
    payload: String,
): String {
    val date =
        Instant
            .fromEpochSeconds(timestamp)
            .toLocalDateTime(TimeZone.UTC)
            .date
            .toString()
    val scope = "$date/tmt/tc3_request"
    val canonical =
        "POST\n/\n\ncontent-type:application/json\nhost:tmt.tencentcloudapi.com\n\ncontent-type;host\n" +
            payload.encodeUtf8().sha256().hex()
    val toSign = "TC3-HMAC-SHA256\n$timestamp\n$scope\n" + canonical.encodeUtf8().sha256().hex()

    fun sign(
        key: ByteString,
        text: String,
    ) = text.encodeUtf8().hmacSha256(key)
    val signature = sign(sign(sign(sign(("TC3" + secretKey).encodeUtf8(), date), "tmt"), "tc3_request"), toSign).hex()
    return "TC3-HMAC-SHA256 Credential=$secretId/$scope, SignedHeaders=content-type;host, Signature=$signature"
}
