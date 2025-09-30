package land.soia

import land.soia.internal.MustNameArguments
import land.soia.reflection.asJson
import java.net.http.HttpHeaders

/**
 * Implementation of a soia service.
 */
class Service private constructor(private val impl: Impl<*>) {
    /**
     * Parses the content of a user request and invokes the appropriate method.
     *
     * If the request is a GET request, pass in the decoded query string as the
     * request's body. The query string is the part of the URL after '?', and it
     * can be decoded with DecodeURIComponent.
     *
     * Pass in "keep-unrecognized-fields" if the request cannot come from a
     * malicious user.
     */
    suspend fun handleRequest(
        requestBody: String,
        requestHeaders: HttpHeaders,
        @Suppress("UNUSED_PARAMETER")
        mustNameArguments: MustNameArguments = MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): RawResponse {
        return impl.handleRequest(requestBody, requestHeaders, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    /** Raw response returned by the server. */
    data class RawResponse(
        val data: String,
        val type: ResponseType,
    ) {
        enum class ResponseType {
            OK_JSON,
            OK_HTML,
            BAD_REQUEST,
            SERVER_ERROR,
        }

        val statusCode: Int
            get() =
                when (type) {
                    ResponseType.OK_JSON, ResponseType.OK_HTML -> 200
                    ResponseType.BAD_REQUEST -> 400
                    ResponseType.SERVER_ERROR -> 500
                }

        val contentType: String
            get() =
                when (type) {
                    ResponseType.OK_JSON -> "application/json"
                    ResponseType.OK_HTML -> "text/html; charset=utf-8"
                    ResponseType.BAD_REQUEST, ResponseType.SERVER_ERROR -> "text/plain; charset=utf-8"
                }
    }

    private data class MethodImpl<Request, Response, RequestMeta>(
        val method: Method<Request, Response>,
        val impl: suspend (req: Request, requestMeta: RequestMeta) -> Response,
    )

    class Builder<RequestMeta> internal constructor(
        private val getRequestMeta: (HttpHeaders) -> RequestMeta,
    ) {
        private val methodImpls: MutableMap<Int, MethodImpl<*, *, RequestMeta>> = mutableMapOf()

        fun <Request, Response> addMethod(
            method: Method<Request, Response>,
            impl: suspend (req: Request, requestMeta: RequestMeta) -> Response,
        ): Builder<RequestMeta> {
            val number = method.number
            if (methodImpls.containsKey(number)) {
                throw IllegalArgumentException("Method with the same number already registered ($number)")
            }
            methodImpls[number] = MethodImpl(method, impl)
            return this
        }

        fun build() = Service(Impl(getRequestMeta, methodImpls.toMap()))
    }

    private class Impl<RequestMeta>(
        val getRequestMeta: (HttpHeaders) -> RequestMeta,
        val methodImpls: Map<Int, MethodImpl<*, *, RequestMeta>>,
    ) {
        suspend fun handleRequest(
            requestBody: String,
            requestHeaders: HttpHeaders,
            keepUnrecognizedFields: Boolean,
        ): RawResponse {
            if (requestBody.isEmpty() || requestBody == "list") {
                val methodsData =
                    methodImpls.values.map { methodImpl ->
                        mapOf(
                            "method" to methodImpl.method.name,
                            "number" to methodImpl.method.number,
                            "request" to methodImpl.method.requestSerializer.typeDescriptor.asJson(),
                            "response" to methodImpl.method.responseSerializer.typeDescriptor.asJson(),
                        )
                    }
                val json = mapOf("methods" to methodsData)
                val jsonCode =
                    PRETTY_JSON.encodeToString(
                        kotlinx.serialization.json.JsonElement.serializer(),
                        kotlinx.serialization.json.Json.parseToJsonElement(json.toString()),
                    )
                return RawResponse(jsonCode, RawResponse.ResponseType.OK_JSON)
            } else if (requestBody == "restudio") {
                return RawResponse(RESTUDIO_HTML, RawResponse.ResponseType.OK_HTML)
            }

            val regex = Regex("^([^:]*):([^:]*):([^:]*):(\\S[\\s\\S]*)$")
            val matchResult =
                regex.find(requestBody)
                    ?: return RawResponse(
                        "bad request: invalid request format",
                        RawResponse.ResponseType.BAD_REQUEST,
                    )

            val (methodName, methodNumberStr, format, requestData) = matchResult.destructured

            val methodNumberRegex = Regex("-?[0-9]+")
            if (!methodNumberRegex.matches(methodNumberStr)) {
                return RawResponse(
                    "bad request: can't parse method number",
                    RawResponse.ResponseType.BAD_REQUEST,
                )
            }
            val methodNumber = methodNumberStr.toInt()

            val methodImpl =
                methodImpls[methodNumber]
                    ?: return RawResponse(
                        "bad request: method not found: $methodName; number: $methodNumber",
                        RawResponse.ResponseType.BAD_REQUEST,
                    )

            val req: Any? =
                try {
                    methodImpl.method.requestSerializer.fromJsonCode(requestData, keepUnrecognizedFields = keepUnrecognizedFields)
                } catch (e: Exception) {
                    return RawResponse(
                        "bad request: can't parse JSON: ${e.message}",
                        RawResponse.ResponseType.BAD_REQUEST,
                    )
                }

            val res: Any =
                try {
                    @Suppress("UNCHECKED_CAST")
                    (methodImpl.impl as suspend (Any?, RequestMeta) -> Any)(req, getRequestMeta(requestHeaders))
                } catch (e: Exception) {
                    return RawResponse(
                        "server error: ${e.message}",
                        RawResponse.ResponseType.SERVER_ERROR,
                    )
                }

            val resJson: String =
                try {
                    val readableFlavor = format == "readable"
                    @Suppress("UNCHECKED_CAST")
                    (methodImpl.method as Method<Any?, Any?>).responseSerializer.toJsonCode(res, readableFlavor = readableFlavor)
                } catch (e: Exception) {
                    return RawResponse(
                        "server error: can't serialize response to JSON: ${e.message}",
                        RawResponse.ResponseType.SERVER_ERROR,
                    )
                }

            return RawResponse(resJson, RawResponse.ResponseType.OK_JSON)
        }
    }

    companion object {
        fun builder() = Builder<HttpHeaders>({ it })

        fun <RequestMeta> builder(getRequestMeta: (HttpHeaders) -> RequestMeta) =
            (
                Builder<RequestMeta>(getRequestMeta)
            )

        private val PRETTY_JSON = kotlinx.serialization.json.Json { prettyPrint = true }

        // Copied from
        //   https://github.com/gepheum/restudio/blob/main/index.jsdeliver.html
        private const val RESTUDIO_HTML = """<!DOCTYPE html>

<html>
  <head>
    <meta charset="utf-8" />
    <title>RESTudio</title>
    <script src="https://cdn.jsdelivr.net/npm/restudio/dist/restudio-standalone.js"></script>
  </head>
  <body style="margin: 0; padding: 0;">
    <restudio-app></restudio-app>
  </body>
</html>
"""
    }
}
