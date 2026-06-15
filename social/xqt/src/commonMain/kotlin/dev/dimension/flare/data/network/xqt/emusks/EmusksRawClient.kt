package dev.dimension.flare.data.network.xqt.emusks

import dev.dimension.flare.common.JSON_WITH_ENCODE_DEFAULT
import dev.dimension.flare.data.network.ktorClient
import de.jensklingenberg.ktorfit.Response
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal fun interface EmusksTransactionIdProvider {
    suspend fun generate(
        method: String,
        path: String,
    ): String
}

internal enum class EmusksGraphqlEndpoint(
    val base: String,
    val referrer: String,
    val secFetchSite: String,
) {
    Main(
        base = "https://api.x.com/graphql",
        referrer = "https://x.com/",
        secFetchSite = "same-site",
    ),
    MainTwitter(
        base = "https://api.twitter.com/graphql",
        referrer = "https://twitter.com/",
        secFetchSite = "same-site",
    ),
    Web(
        base = "https://x.com/i/api/graphql",
        referrer = "https://x.com/",
        secFetchSite = "same-origin",
    ),
    WebTwitter(
        base = "https://twitter.com/i/api/graphql",
        referrer = "https://twitter.com/",
        secFetchSite = "same-origin",
    ),
}

internal class EmusksApiException(
    message: String,
) : Exception(message)

internal data class EmusksLoginResult(
    val cookies: EmusksCookie,
    val userId: String?,
)

private data class EmusksJsonResponse(
    val response: HttpResponse,
    val text: String,
    val json: JsonObject,
)

private data class EmusksTextResponse(
    val response: HttpResponse,
    val text: String,
)

internal class EmusksRawClient(
    private val cookieProvider: suspend () -> String?,
    private val clientProfile: EmusksClientProfile = EmusksClients.Web,
    private val graphqlEndpoint: EmusksGraphqlEndpoint = EmusksGraphqlEndpoint.Web,
    private val transactionIdProvider: EmusksTransactionIdProvider? = null,
    private val actingAsUserId: String? = null,
    private val loginExpiredException: (() -> Exception)? = null,
    private val client: HttpClient = ktorClient(),
) {
    suspend fun login(authToken: String): EmusksLoginResult {
        if (authToken.length !in 20..50) {
            throw EmusksApiException("invalid auth token length")
        }
        val response =
            client.get("https://x.com/") {
                accept(ContentType.Text.Html)
                headers {
                    append(HttpHeaders.Cookie, EmusksCookie.fromAuthToken(authToken).toHeader())
                    emusksBrowserHeaders(secFetchSite = "none")
                    append(HttpHeaders.Referrer, "https://x.com/")
                    append(HttpHeaders.UserAgent, clientProfile.fingerprint.userAgent)
                }
            }
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        val cookies =
            EmusksCookie
                .fromAuthToken(authToken)
                .plus(EmusksCookie.fromSetCookieHeaders(setCookies))
        if (cookies.csrfToken == null) {
            throw EmusksApiException("failed to log in")
        }
        val html = response.bodyAsText()
        return EmusksLoginResult(
            cookies = cookies,
            userId = extractInitialUserId(html),
        )
    }

    suspend fun graphqlJson(
        queryName: String,
        variables: JsonObject? = null,
        features: JsonObject? = null,
        fieldToggles: JsonObject? = null,
        body: JsonObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject =
        graphqlRaw(
            queryName = queryName,
            variables = variables,
            features = features,
            fieldToggles = fieldToggles,
            body = body,
            headers = headers,
        ).json

    private suspend fun graphqlRaw(
        queryName: String,
        variables: JsonObject? = null,
        features: JsonObject? = null,
        fieldToggles: JsonObject? = null,
        body: JsonObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): EmusksJsonResponse {
        val operation =
            EmusksGraphqlOperations[queryName]
                ?: throw EmusksApiException("graphql query $queryName not found")
        val isPost = operation.method == EmusksHttpMethod.POST
        val urlBuilder = URLBuilder("${graphqlEndpoint.base}/${operation.queryId}/$queryName")
        var requestBody: JsonObject? = null

        if (isPost) {
            requestBody =
                buildJsonObject {
                    body?.forEach { (key, value) -> put(key, value) }
                    put("variables", mergeVariables(variables, body?.get("variables") as? JsonObject))
                    put("queryId", operation.queryId)
                    if (features != null && features.isNotEmpty()) {
                        put("features", features)
                    }
                    if (fieldToggles != null && fieldToggles.isNotEmpty()) {
                        put("fieldToggles", fieldToggles)
                    }
                }
        } else {
            variables?.takeIf { it.isNotEmpty() }?.let {
                urlBuilder.parameters.append(
                    "variables",
                    JSON_WITH_ENCODE_DEFAULT.encodeToString(JsonObject.serializer(), it),
                )
            }
            features?.takeIf { it.isNotEmpty() }?.let {
                urlBuilder.parameters.append(
                    "features",
                    JSON_WITH_ENCODE_DEFAULT.encodeToString(JsonObject.serializer(), it),
                )
            }
            fieldToggles?.takeIf { it.isNotEmpty() }?.let {
                urlBuilder.parameters.append(
                    "fieldToggles",
                    JSON_WITH_ENCODE_DEFAULT.encodeToString(JsonObject.serializer(), it),
                )
            }
        }

        return requestJsonResponse(
            method = operation.method,
            url = urlBuilder.buildString(),
            referrer = graphqlEndpoint.referrer,
            secFetchSite = graphqlEndpoint.secFetchSite,
            body = requestBody,
            extraHeaders = headers,
        )
    }

    suspend fun <T> graphql(
        serializer: KSerializer<T>,
        queryName: String,
        variables: JsonObject? = null,
        features: JsonObject? = null,
        fieldToggles: JsonObject? = null,
        body: JsonObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): T =
        JSON_WITH_ENCODE_DEFAULT.decodeFromString(
            serializer,
            JSON_WITH_ENCODE_DEFAULT.encodeToString(
                JsonObject.serializer(),
                graphqlJson(
                    queryName = queryName,
                    variables = variables,
                    features = features,
                    fieldToggles = fieldToggles,
                    body = body,
                    headers = headers,
                ),
            ),
        )

    suspend fun apolloGraphqlJson(
        operationId: String,
        operationName: String,
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
    ): JsonObject =
        json(
            JsonObject.serializer(),
            method = EmusksHttpMethod.POST,
            url = "https://api.x.com/graphql/$operationId/$operationName",
            body =
                buildJsonObject {
                    put("operationName", operationName)
                    put("variables", variables)
                    put("query", query)
                    put("queryId", operationId)
                },
            headers =
                mapOf(
                    "Origin" to "https://chat.x.com",
                    "x-apollo-operation-id" to operationId,
                    "x-apollo-operation-name" to operationName,
                    "apollo-require-preflight" to "true",
                ),
            referrer = "https://chat.x.com/",
            secFetchSite = "same-site",
        )

    suspend fun <T : Any> graphqlResponse(
        serializer: KSerializer<T>,
        queryName: String,
        variables: JsonObject? = null,
        features: JsonObject? = null,
        fieldToggles: JsonObject? = null,
        body: JsonObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response<T> =
        graphqlRaw(
            queryName = queryName,
            variables = variables,
            features = features,
            fieldToggles = fieldToggles,
            body = body,
            headers = headers,
        ).toResponse(serializer)

    suspend fun <T> json(
        serializer: KSerializer<T>,
        method: EmusksHttpMethod,
        url: String,
        params: Map<String, String?> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        referrer: String = "https://x.com/",
        secFetchSite: String = "same-origin",
    ): T =
        JSON_WITH_ENCODE_DEFAULT.decodeFromString(
            serializer,
            jsonRaw(
                method = method,
                url = url,
                params = params,
                body = body,
                headers = headers,
                referrer = referrer,
                secFetchSite = secFetchSite,
            ).text,
        )

    suspend fun <T : Any> jsonResponse(
        serializer: KSerializer<T>,
        method: EmusksHttpMethod,
        url: String,
        params: Map<String, String?> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        referrer: String = "https://x.com/",
        secFetchSite: String = "same-origin",
    ): Response<T> =
        jsonRaw(
            method = method,
            url = url,
            params = params,
            body = body,
            headers = headers,
            referrer = referrer,
            secFetchSite = secFetchSite,
        ).toResponse(serializer)

    suspend fun unitResponse(
        method: EmusksHttpMethod,
        url: String,
        params: Map<String, String?> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        referrer: String = "https://x.com/",
        secFetchSite: String = "same-origin",
    ): Response<Unit> =
        textRaw(
            method = method,
            url = url,
            params = params,
            body = body,
            headers = headers,
            referrer = referrer,
            secFetchSite = secFetchSite,
        ).toUnitResponse()

    suspend fun text(
        method: EmusksHttpMethod,
        url: String,
        params: Map<String, String?> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        referrer: String = "https://x.com/",
        secFetchSite: String = "same-origin",
    ): String =
        textRaw(
            method = method,
            url = url,
            params = params,
            body = body,
            headers = headers,
            referrer = referrer,
            secFetchSite = secFetchSite,
        ).text

    suspend fun v1Json(
        queryName: String,
        params: Map<String, String> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject {
        val operation =
            EmusksV11Operations[queryName]
                ?: throw EmusksApiException("v1.1 endpoint $queryName not found")
        return restJson(operation, params, body, headers)
    }

    suspend fun <T : Any> v1Response(
        serializer: KSerializer<T>,
        queryName: String,
        params: Map<String, String> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response<T> {
        val operation =
            EmusksV11Operations[queryName]
                ?: throw EmusksApiException("v1.1 endpoint $queryName not found")
        return restRaw(operation, params, body, headers).toResponse(serializer)
    }

    suspend fun v2Json(
        queryName: String,
        params: Map<String, String> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject {
        val operation =
            EmusksV2Operations[queryName]
                ?: throw EmusksApiException("v2 endpoint $queryName not found")
        return restJson(operation, params, body, headers)
    }

    suspend fun <T : Any> v2Response(
        serializer: KSerializer<T>,
        queryName: String,
        params: Map<String, String> = emptyMap(),
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): Response<T> {
        val operation =
            EmusksV2Operations[queryName]
                ?: throw EmusksApiException("v2 endpoint $queryName not found")
        return restRaw(operation, params, body, headers).toResponse(serializer)
    }

    private suspend fun restJson(
        operation: EmusksRestOperation,
        params: Map<String, String>,
        body: Any?,
        headers: Map<String, String>,
    ): JsonObject = restRaw(operation, params, body, headers).json

    private suspend fun restRaw(
        operation: EmusksRestOperation,
        params: Map<String, String>,
        body: Any?,
        headers: Map<String, String>,
    ): EmusksJsonResponse {
        val urlBuilder = URLBuilder(operation.url)
        params.forEach { (key, value) -> urlBuilder.parameters.append(key, value) }
        return requestJsonResponse(
            method = operation.method,
            url = urlBuilder.buildString(),
            referrer = "https://x.com/",
            secFetchSite = "same-origin",
            body = body,
            extraHeaders = headers,
        )
    }

    private suspend fun requestJson(
        method: EmusksHttpMethod,
        url: String,
        referrer: String,
        secFetchSite: String,
        body: Any?,
        extraHeaders: Map<String, String>,
    ): JsonObject = requestJsonResponse(method, url, referrer, secFetchSite, body, extraHeaders).json

    private suspend fun jsonRaw(
        method: EmusksHttpMethod,
        url: String,
        params: Map<String, String?>,
        body: Any?,
        headers: Map<String, String>,
        referrer: String,
        secFetchSite: String,
    ): EmusksJsonResponse {
        val urlBuilder = URLBuilder(url)
        params.forEach { (key, value) ->
            if (value != null) {
                urlBuilder.parameters.append(key, value)
            }
        }
        return requestJsonResponse(
            method = method,
            url = urlBuilder.buildString(),
            referrer = referrer,
            secFetchSite = secFetchSite,
            body = body,
            extraHeaders = headers,
        )
    }

    private suspend fun requestJsonResponse(
        method: EmusksHttpMethod,
        url: String,
        referrer: String,
        secFetchSite: String,
        body: Any?,
        extraHeaders: Map<String, String>,
    ): EmusksJsonResponse {
        val textResponse = requestTextResponse(method, url, referrer, secFetchSite, body, extraHeaders)
        val responseText = textResponse.text
        if (textResponse.response.status == HttpStatusCode.Forbidden) {
            loginExpiredException?.let { throw it() }
        }
        if (responseText.isBlank()) {
            throw EmusksApiException("empty response")
        }
        val json = JSON_WITH_ENCODE_DEFAULT.decodeFromString(JsonElement.serializer(), responseText)
        val jsonObject = json.jsonObject
        val errors = jsonObject["errors"]?.jsonArray.orEmpty()
        val errorCode =
            errors
                .firstOrNull()
                ?.jsonObject
                ?.get("code")
                ?.jsonPrimitive
                ?.longOrNull
        if (errorCode == 215L) {
            loginExpiredException?.let { throw it() }
        }
        val hasData = (jsonObject["data"] as? JsonObject)?.isNotEmpty() == true
        if (errors.isNotEmpty() && !hasData) {
            val message =
                errors.joinToString(", ") { error ->
                    runCatching {
                        error.jsonObject["message"]?.jsonPrimitive?.content
                    }.getOrNull() ?: error.toString()
                }
            throw EmusksApiException(message)
        }
        return EmusksJsonResponse(textResponse.response, responseText, jsonObject)
    }

    private suspend fun textRaw(
        method: EmusksHttpMethod,
        url: String,
        params: Map<String, String?>,
        body: Any?,
        headers: Map<String, String>,
        referrer: String,
        secFetchSite: String,
    ): EmusksTextResponse {
        val urlBuilder = URLBuilder(url)
        params.forEach { (key, value) ->
            if (value != null) {
                urlBuilder.parameters.append(key, value)
            }
        }
        return requestTextResponse(
            method = method,
            url = urlBuilder.buildString(),
            referrer = referrer,
            secFetchSite = secFetchSite,
            body = body,
            extraHeaders = headers,
        )
    }

    private suspend fun requestTextResponse(
        method: EmusksHttpMethod,
        url: String,
        referrer: String,
        secFetchSite: String,
        body: Any?,
        extraHeaders: Map<String, String>,
    ): EmusksTextResponse {
        val response =
            client.request(url) {
                this.method = method.toKtor()
                val cookie = currentCookie()
                val path = Url(url).encodedPath
                val transactionId = transactionIdProvider?.generate(method.name, path)
                headers {
                    append(HttpHeaders.Accept, "*/*")
                    append(HttpHeaders.Authorization, "Bearer ${clientProfile.bearer}")
                    append(HttpHeaders.Cookie, cookie.toHeader())
                    append(HttpHeaders.Referrer, referrer)
                    append(HttpHeaders.UserAgent, clientProfile.fingerprint.userAgent)
                    append("x-twitter-client-language", "en")
                    append("x-twitter-active-user", "yes")
                    append("x-twitter-auth-type", "OAuth2Session")
                    cookie.csrfToken?.let { append("x-csrf-token", it) }
                    cookie.guestToken?.let { append("x-guest-token", it) }
                    actingAsUserId?.let { append("x-act-as-user-id", it) }
                    emusksBrowserHeaders(secFetchSite = secFetchSite)
                    transactionId?.takeIf { it.isNotBlank() }?.let { append("x-client-transaction-id", it) }
                    extraHeaders.forEach { (key, value) -> append(key, value) }
                }
                if (body != null) {
                    when (body) {
                        is JsonObject -> {
                            contentType(ContentType.Application.Json)
                            setBody(JSON_WITH_ENCODE_DEFAULT.encodeToString(JsonObject.serializer(), body))
                        }
                        is FormDataContent -> setBody(body)
                        else -> setBody(body)
                    }
                }
            }
        val responseText = response.bodyAsText()
        if (response.status == HttpStatusCode.Forbidden) {
            loginExpiredException?.let { throw it() }
        }
        return EmusksTextResponse(response, responseText)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> EmusksJsonResponse.toResponse(serializer: KSerializer<T>): Response<T> =
        if (response.status.isSuccess()) {
            Response.success(
                JSON_WITH_ENCODE_DEFAULT.decodeFromString(serializer, text),
                response,
            ) as Response<T>
        } else {
            Response.error<T>(json, response) as Response<T>
        }

    @Suppress("UNCHECKED_CAST")
    private fun EmusksTextResponse.toUnitResponse(): Response<Unit> =
        if (response.status.isSuccess()) {
            Response.success(Unit, response) as Response<Unit>
        } else {
            Response.error<Unit>(text, response) as Response<Unit>
        }

    private suspend fun currentCookie(): EmusksCookie {
        val cookieHeader =
            cookieProvider()
                ?: throw EmusksApiException("missing cookie")
        val cookie = EmusksCookie.parse(cookieHeader)
        if (cookie.authToken == null) {
            throw EmusksApiException("missing auth_token")
        }
        return cookie
    }

    private fun mergeVariables(
        variables: JsonObject?,
        bodyVariables: JsonObject?,
    ): JsonObject =
        buildJsonObject {
            variables?.forEach { (key, value) -> put(key, value) }
            bodyVariables?.forEach { (key, value) -> put(key, value) }
        }

    private fun EmusksHttpMethod.toKtor(): HttpMethod =
        when (this) {
            EmusksHttpMethod.GET -> HttpMethod.Get
            EmusksHttpMethod.POST -> HttpMethod.Post
            EmusksHttpMethod.PUT -> HttpMethod.Put
            EmusksHttpMethod.DELETE -> HttpMethod.Delete
        }

    private fun io.ktor.http.HeadersBuilder.emusksBrowserHeaders(secFetchSite: String) {
        clientProfile.defaultHeaders.forEach { (key, value) ->
            append(key, if (key == "sec-fetch-site") secFetchSite else value)
        }
    }

    companion object {
        fun formBody(fields: Map<String, String?>): FormDataContent =
            FormDataContent(
                Parameters.build {
                    fields.forEach { (key, value) ->
                        if (value != null) {
                            append(key, value)
                        }
                    }
                },
            )

        fun extractInitialUserId(html: String): String? {
            val initialStateMatch =
                Regex("""window\.__INITIAL_STATE__\s*=\s*(\{[\s\S]*?});""")
                    .find(html)
            if (initialStateMatch != null) {
                val initialState =
                    runCatching {
                        JSON_WITH_ENCODE_DEFAULT.decodeFromString(
                            JsonObject.serializer(),
                            initialStateMatch.groupValues[1],
                        )
                    }.getOrNull()
                val entities =
                    initialState
                        ?.get("entities")
                        ?.jsonObject
                        ?.get("users")
                        ?.jsonObject
                        ?.get("entities")
                        ?.jsonObject
                val firstUser = entities?.values?.firstOrNull()?.jsonObject
                val id =
                    firstUser?.get("id_str")?.jsonPrimitive?.content
                        ?: firstUser?.get("id")?.jsonPrimitive?.content
                if (id != null) {
                    return id
                }
            }
            return Regex("\"user_id\":\"([0-9]+)\"")
                .find(html)
                ?.groupValues
                ?.get(1)
        }
    }
}
