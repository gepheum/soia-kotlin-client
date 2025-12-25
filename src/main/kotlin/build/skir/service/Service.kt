package build.skir.service

import build.skir.JsonFlavor
import build.skir.UnrecognizedValuesPolicy
import build.skir.internal.JsonObjectBuilder
import build.skir.internal.formatReadableJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.http.HttpHeaders

/**
 * Implementation of a skir service.
 */
class Service private constructor(private val impl: Impl<*>) {
    /**
     * Parses the content of a user request and invokes the appropriate method.
     *
     * If the request is a GET request, pass in the decoded query string as the
     * request's body. The query string is the part of the URL after '?', and it
     * can be decoded with DecodeURIComponent.
     *
     * Pass in [UnrecognizedValuesPolicy.KEEP] if the request cannot come from a
     * malicious user.
     */
    suspend fun handleRequest(
        requestBody: String,
        requestHeaders: HttpHeaders,
        unrecognizedValues: UnrecognizedValuesPolicy,
    ): RawResponse {
        return impl.handleRequest(requestBody, requestHeaders, unrecognizedValues)
    }

    /** Raw response returned by the server. */
    data class RawResponse(
        @get:JvmName("data")
        val data: String,
        @get:JvmName("type")
        val type: ResponseType,
    ) {
        enum class ResponseType {
            OK_JSON,
            OK_HTML,
            BAD_REQUEST,
            SERVER_ERROR,
        }

        @get:JvmName("statusCode")
        val statusCode: Int
            get() =
                when (type) {
                    ResponseType.OK_JSON, ResponseType.OK_HTML -> 200
                    ResponseType.BAD_REQUEST -> 400
                    ResponseType.SERVER_ERROR -> 500
                }

        @get:JvmName("contentType")
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
        private val methodImpls: MutableMap<Long, MethodImpl<*, *, RequestMeta>> = mutableMapOf()

        /**
         * Registers the implementation of a method.
         *
         * @return `this` builder
         */
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
        val methodImpls: Map<Long, MethodImpl<*, *, RequestMeta>>,
    ) {
        private fun getMethodNumberByName(methodName: String): Long? {
            val nameMatches = methodImpls.values.filter { it.method.name == methodName }
            return when {
                nameMatches.isEmpty() -> null
                nameMatches.size > 1 -> null
                else -> nameMatches[0].method.number
            }
        }

        suspend fun handleRequest(
            requestBody: String,
            requestHeaders: HttpHeaders,
            unrecognizedValues: UnrecognizedValuesPolicy,
        ): RawResponse {
            if (requestBody.isEmpty() || requestBody == "list") {
                val methodsData =
                    JsonArray(
                        methodImpls.values.map { methodImpl ->
                            JsonObjectBuilder()
                                .put("method", JsonPrimitive(methodImpl.method.name))
                                .put("number", JsonPrimitive(methodImpl.method.number))
                                .put("request", methodImpl.method.requestSerializer.typeDescriptor.asJson())
                                .put("response", methodImpl.method.responseSerializer.typeDescriptor.asJson())
                                .putUnlessEmpty("doc", JsonPrimitive(methodImpl.method.doc))
                                .build()
                        },
                    )
                val json = JsonObject(mapOf("methods" to methodsData))
                val jsonCode =
                    formatReadableJson(json)
                return RawResponse(jsonCode, RawResponse.ResponseType.OK_JSON)
            } else if (requestBody == "debug" || requestBody == "restudio") {
                return RawResponse(RESTUDIO_HTML, RawResponse.ResponseType.OK_HTML)
            }

            // Method invocation
            val methodName: String
            val methodNumber: Long
            val format: String
            val requestDataJson: JsonElement?
            val requestDataCode: String?

            val firstChar = requestBody[0]
            if (firstChar.isWhitespace() || firstChar == '{') {
                // A JSON object
                val reqBodyJson: JsonObject =
                    try {
                        kotlinx.serialization.json.Json.parseToJsonElement(requestBody) as? JsonObject
                            ?: return RawResponse(
                                "bad request: expected JSON object",
                                RawResponse.ResponseType.BAD_REQUEST,
                            )
                    } catch (e: Exception) {
                        return RawResponse("bad request: invalid JSON", RawResponse.ResponseType.BAD_REQUEST)
                    }

                val methodField =
                    reqBodyJson["method"]
                        ?: return RawResponse(
                            "bad request: missing 'method' field in JSON",
                            RawResponse.ResponseType.BAD_REQUEST,
                        )

                when (methodField) {
                    is JsonPrimitive -> {
                        if (methodField.isString) {
                            methodName = methodField.content
                            // Try to get the method number by name
                            val foundNumber = getMethodNumberByName(methodName)
                            if (foundNumber == null) {
                                val nameMatches = methodImpls.values.filter { it.method.name == methodName }
                                return if (nameMatches.isEmpty()) {
                                    RawResponse(
                                        "bad request: method not found: $methodName",
                                        RawResponse.ResponseType.BAD_REQUEST,
                                    )
                                } else {
                                    RawResponse(
                                        "bad request: method name '$methodName' is ambiguous; use method number instead",
                                        RawResponse.ResponseType.BAD_REQUEST,
                                    )
                                }
                            }
                            methodNumber = foundNumber
                        } else {
                            methodName = "?"
                            methodNumber = methodField.content.toLongOrNull()
                                ?: return RawResponse(
                                    "bad request: 'method' field must be a string or an integer",
                                    RawResponse.ResponseType.BAD_REQUEST,
                                )
                        }
                    }
                    else -> {
                        return RawResponse(
                            "bad request: 'method' field must be a string or an integer",
                            RawResponse.ResponseType.BAD_REQUEST,
                        )
                    }
                }

                format = "readable"

                val requestField =
                    reqBodyJson["request"]
                        ?: return RawResponse(
                            "bad request: missing 'request' field in JSON",
                            RawResponse.ResponseType.BAD_REQUEST,
                        )

                requestDataJson = requestField
                requestDataCode = null
            } else {
                // A colon-separated string
                val regex = Regex("^([^:]*):([^:]*):([^:]*):(\\S[\\s\\S]*)$")
                val matchResult =
                    regex.find(requestBody)
                        ?: return RawResponse(
                            "bad request: invalid request format",
                            RawResponse.ResponseType.BAD_REQUEST,
                        )

                val (methodNamePart, methodNumberStr, formatPart, requestDataPart) = matchResult.destructured

                methodName = methodNamePart
                format = formatPart
                requestDataJson = null
                requestDataCode = requestDataPart

                if (methodNumberStr.isNotEmpty()) {
                    val methodNumberRegex = Regex("-?[0-9]+")
                    if (!methodNumberRegex.matches(methodNumberStr)) {
                        return RawResponse(
                            "bad request: can't parse method number",
                            RawResponse.ResponseType.BAD_REQUEST,
                        )
                    }
                    methodNumber = methodNumberStr.toLong()
                } else {
                    // Try to get the method number by name
                    val foundNumber = getMethodNumberByName(methodName)
                    if (foundNumber == null) {
                        val nameMatches = methodImpls.values.filter { it.method.name == methodName }
                        return if (nameMatches.isEmpty()) {
                            RawResponse(
                                "bad request: method not found: $methodName",
                                RawResponse.ResponseType.BAD_REQUEST,
                            )
                        } else {
                            RawResponse(
                                "bad request: method name '$methodName' is ambiguous; use method number instead",
                                RawResponse.ResponseType.BAD_REQUEST,
                            )
                        }
                    }
                    methodNumber = foundNumber
                }
            }

            val methodImpl =
                methodImpls[methodNumber]
                    ?: return RawResponse(
                        "bad request: method not found: $methodName; number: $methodNumber",
                        RawResponse.ResponseType.BAD_REQUEST,
                    )

            val req: Any? =
                try {
                    if (requestDataCode != null) {
                        methodImpl.method.requestSerializer.fromJsonCode(requestDataCode, unrecognizedValues)
                    } else {
                        methodImpl.method.requestSerializer.fromJson(requestDataJson!!, unrecognizedValues)
                    }
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
                    val jsonFlavor = if (format == "readable") JsonFlavor.READABLE else JsonFlavor.DENSE
                    @Suppress("UNCHECKED_CAST")
                    (methodImpl.method as Method<Any?, Any?>).responseSerializer.toJsonCode(res, jsonFlavor)
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
