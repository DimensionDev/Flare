package dev.dimension.flare.data.network.xqt.elonmusk114514

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorClient
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlin.experimental.xor
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.serialization.Serializable

internal object ElonMusk1145141919810 {
    @Serializable
    private data class JsonPair(
        val animationKey: String,
        val verification: String,
    )

    private var jsonPair: List<JsonPair>? = null

    suspend fun encodeSha256(data: String): ByteArray {
        return CryptographyProvider
            .Default
            .get(SHA256)
            .hasher()
            .hash(data.encodeToByteArray())
    }

    fun encodeBase64(data: ByteArray): String = data.encodeBase64()

    fun decodeBase64(data: String): ByteArray = data.decodeBase64Bytes()

    suspend fun senpaiSukissu(
        method: String,
        path: String,
    ): String {
        if (jsonPair == null) {
            val url = "https://raw.githubusercontent.com/fa0311/x-client-transaction-id-pair-dict/refs/heads/main/pair.json"
            val response = ktorClient().get(url).bodyAsText()
            jsonPair = response.decodeJson()
        }

        val randomPair = jsonPair!!.random()
        val animationKey = randomPair.animationKey
        val key = randomPair.verification
        val defaultKeyword = "obfiowerehiring"
        val additionRandomNumber = 3
        val epochOffset = 1682924400L * 1000
        val timeNow = ((Clock.System.now().toEpochMilliseconds() - epochOffset) / 1000).toInt()

        val timeNowBytes =
            byteArrayOf(
                (timeNow and 0xff).toByte(),
                ((timeNow shr 8) and 0xff).toByte(),
                ((timeNow shr 16) and 0xff).toByte(),
                ((timeNow shr 24) and 0xff).toByte(),
            )

        val data = "$method!$path!$timeNow$defaultKeyword$animationKey"
        val hashBytes = encodeSha256(data)
        val keyBytes = decodeBase64(key)

        val randomNum = Random.nextInt(0, 256).toByte()

        val bytesArr =
            keyBytes +
                timeNowBytes +
                hashBytes.take(16).toByteArray() +
                byteArrayOf(additionRandomNumber.toByte())

        val out = ByteArray(bytesArr.size + 1)
        out[0] = randomNum
        for (i in bytesArr.indices) {
            out[i + 1] = bytesArr[i].xor(randomNum)
        }

        return encodeBase64(out)
    }
}
