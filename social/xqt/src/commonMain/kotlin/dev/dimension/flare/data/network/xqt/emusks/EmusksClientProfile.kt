package dev.dimension.flare.data.network.xqt.emusks

internal data class EmusksTlsFingerprint(
    val ja3: String,
    val ja4r: String,
    val userAgent: String,
)

internal data class EmusksClientProfile(
    val bearer: String,
    val fingerprint: EmusksTlsFingerprint,
    val defaultHeaders: Map<String, String>,
)

internal object EmusksClients {
    private val chromeFingerprint =
        EmusksTlsFingerprint(
            userAgent =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
            ja3 =
                "771,4865-4866-4867-49195-49199-49196-49200-52393-52392-49171-49172-156-157-47-53," +
                    "35-5-27-16-0-10-13-23-45-65037-17613-18-65281-51-43-11,4588-29-23-24,0",
            ja4r =
                "t13d1516h2_002f,0035,009c,009d,1301,1302,1303,c013,c014,c02b,c02c,c02f,c030," +
                    "cca8,cca9_0005,000a,000b,000d,0012,0017,001b,0023,002b,002d,0033,44cd,fe0d," +
                    "ff01_0403,0804,0401,0503,0805,0501,0806,0601",
        )

    val Web: EmusksClientProfile =
        EmusksClientProfile(
            bearer =
                "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D" +
                    "1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA",
            fingerprint = chromeFingerprint,
            defaultHeaders =
                mapOf(
                    "accept-language" to "en-US,en;q=0.9",
                    "priority" to "u=1, i",
                    "sec-ch-ua" to "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\"",
                    "sec-ch-ua-mobile" to "?0",
                    "sec-ch-ua-platform" to "\"macOS\"",
                    "sec-fetch-dest" to "empty",
                    "sec-fetch-mode" to "cors",
                    "sec-fetch-site" to "same-origin",
                    "sec-gpc" to "1",
                ),
        )
}
