package land.soia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** Sends RPCs to a soia service. */
class ServiceClient(
    serviceUrl: String,
    private val defaultRequestHeaders: Map<String, List<String>> = emptyMap(),
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
) {
    private val serviceUri = URI(serviceUrl)

    init {
        if (serviceUri.query != null) {
            throw IllegalArgumentException("Service URL must not contain a query string")
        }
    }

    enum class HttpMethod {
        GET,
        POST,
    }

    /** Invokes the given method on the remote server through an RPC. */
    suspend fun <Request, Response> invokeRemote(
        method: Method<Request, Response>,
        request: Request,
        httpMethod: HttpMethod = HttpMethod.POST,
        requestHeaders: Map<String, List<String>> = defaultRequestHeaders,
        timeout: Duration = MAX_DURATION,
    ): Response =
        withContext(Dispatchers.IO) {
            val requestJson = method.requestSerializer.toJsonCode(request)
            val requestBody = listOf(method.name, method.number.toString(), "", requestJson).joinToString(":")

            val httpRequestBuilder = HttpRequest.newBuilder()

            // Add headers from metadata
            for ((name, values) in requestHeaders) {
                for (value in values) {
                    httpRequestBuilder.header(name, value)
                }
            }

            // Set timeout if provided
            if (timeout != MAX_DURATION) {
                httpRequestBuilder.timeout(timeout)
            }

            when (httpMethod) {
                HttpMethod.POST -> {
                    httpRequestBuilder
                        .uri(serviceUri)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                }
                HttpMethod.GET -> {
                    val encodedBody = URLEncoder.encode(requestBody.replace("%", "%25"), StandardCharsets.UTF_8)
                    val urlWithQuery = "$serviceUri?$encodedBody"
                    httpRequestBuilder
                        .uri(URI(urlWithQuery))
                        .GET()
                }
            }

            val httpRequest = httpRequestBuilder.build()
            val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

            if (httpResponse.statusCode() in 200..299) {
                val jsonCode = httpResponse.body()
                method.responseSerializer.fromJsonCode(jsonCode, keepUnrecognizedFields = true)
            } else {
                var message = ""
                val contentType = httpResponse.headers().firstValue("content-type").orElse("")
                if (contentType.contains("text/plain", ignoreCase = true)) {
                    message = ": ${httpResponse.body()}"
                }
                throw IOException("HTTP status ${httpResponse.statusCode()}$message")
            }
        }

    companion object {
        private val MAX_DURATION = Duration.ofNanos(Long.MAX_VALUE)
    }
}
