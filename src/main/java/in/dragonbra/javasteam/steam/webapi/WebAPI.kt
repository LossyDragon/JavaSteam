package `in`.dragonbra.javasteam.steam.webapi

import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.util.Versions
import `in`.dragonbra.javasteam.util.WebHelpers
import `in`.dragonbra.javasteam.util.compat.Consumer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Represents a single interface that exists within the Web API.
 */
class WebAPI(
    private val client: OkHttpClient,
    val baseAddress: String,
    val `interface`: String,
    val webAPIKey: String,
) {

    /**
     * Manually calls the specified Web API function with the provided details. This method is synchronous.
     * @param httpMethod The http request method. Either "POST" or "GET".
     * @param function   The function name to call.
     * @param version    The version of the function to call.
     * @param parameters A map of string key value pairs representing arguments to be passed to the API.
     * @return A [KeyValue] object representing the results of the Web API call.
     * @throws IOException            if the request could not be executed
     * @throws WebAPIRequestException the request was successful but returned a non success response code
     */
    @JvmOverloads
    @Throws(IOException::class, WebAPIRequestException::class)
    fun call(
        httpMethod: String,
        function: String,
        version: Int = 1,
        parameters: Map<String, String>? = null,
    ): KeyValue {
        val request = buildRequest(httpMethod, function, version, parameters)
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw WebAPIRequestException(response)
        }

        return parseResponse(response)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is synchronous.
     * @param function   The function name to call.
     * @param version    The version of the function to call.
     * @param parameters A map of string key value pairs representing arguments to be passed to the API.
     * @return A [KeyValue] object representing the results of the Web API call.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(function: String, version: Int, parameters: Map<String, String>): KeyValue {
        return call("GET", function, version, parameters)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is synchronous.
     * @param httpMethod The http request method. Either "POST" or "GET".
     * @param function   The function name to call.
     * @param parameters A map of string key value pairs representing arguments to be passed to the API.
     * @return A [KeyValue] object representing the results of the Web API call.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(httpMethod: String, function: String, parameters: Map<String, String>): KeyValue {
        return call(httpMethod, function, 1, parameters)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is synchronous.
     * @param function   The function name to call.
     * @param parameters A map of string key value pairs representing arguments to be passed to the API.
     * @return A [KeyValue] object representing the results of the Web API call.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(function: String, parameters: Map<String, String>): KeyValue {
        return call("GET", function, 1, parameters)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is synchronous.
     * @param function The function name to call.
     * @param version  The version of the function to call.
     * @return A [KeyValue] object representing the results of the Web API call.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(function: String, version: Int): KeyValue {
        return call("GET", function, version, null)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is synchronous.
     * @param function The function name to call.
     * @return A [KeyValue] object representing the results of the Web API call.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(function: String): KeyValue {
        return call("GET", function, 1, null)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is asynchronous.
     * @param httpMethod The http request method. Either "POST" or "GET".
     * @param function   The function name to call.
     * @param version    The version of the function to call.
     * @param parameters A map of string key value pairs representing arguments to be passed to the API.
     * @param callback   the callback that will be called with the resulting [KeyValue] object.
     * @param error      the callback for handling response errors.
     */
    fun call(
        httpMethod: String,
        function: String,
        version: Int,
        parameters: Map<String, String>?,
        callback: Consumer<KeyValue>,
        error: Consumer<WebAPIRequestException>?,
    ) {
        val request = buildRequest(httpMethod, function, version, parameters)
        val cb = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                throw IllegalStateException("request unsuccessful", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    error?.accept(WebAPIRequestException(response))
                } else {
                    callback.accept(parseResponse(response))
                }
            }
        }

        client.newCall(request).enqueue(cb)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is asynchronous.
     *
     * @param httpMethod The http request method. Either "POST" or "GET".
     * @param function   The function name to call.
     * @param version    The version of the function to call.
     * @param callback   the callback that will be called with the resulting [KeyValue] object.
     * @param error      the callback for handling response errors.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(
        httpMethod: String,
        function: String,
        version: Int,
        callback: Consumer<KeyValue>,
        error: Consumer<WebAPIRequestException>,
    ) {
        call(httpMethod, function, version, null, callback, error)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is asynchronous.
     *
     * @param httpMethod The http request method. Either "POST" or "GET".
     * @param function   The function name to call.
     * @param parameters A map of string key value pairs representing arguments to be passed to the API.
     * @param callback   the callback that will be called with the resulting [KeyValue] object.
     * @param error      the callback for handling response errors.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(
        httpMethod: String,
        function: String,
        parameters: Map<String, String>,
        callback: Consumer<KeyValue>,
        error: Consumer<WebAPIRequestException>,
    ) {
        call(httpMethod, function, 1, parameters, callback, error)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is asynchronous.
     *
     * @param httpMethod The http request method. Either "POST" or "GET".
     * @param function   The function name to call.
     * @param callback   the callback that will be called with the resulting [KeyValue] object.
     * @param error      the callback for handling response errors.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(
        httpMethod: String,
        function: String,
        callback: Consumer<KeyValue>,
        error: Consumer<WebAPIRequestException>,
    ) {
        call(httpMethod, function, 1, null, callback, error)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is asynchronous.
     *
     * @param function The function name to call.
     * @param version  The version of the function to call.
     * @param callback the callback that will be called with the resulting [KeyValue] object.
     * @param error    the callback for handling response errors.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(
        function: String,
        version: Int,
        callback: Consumer<KeyValue>,
        error: Consumer<WebAPIRequestException>,
    ) {
        call("GET", function, version, null, callback, error)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is asynchronous.
     *
     * @param function   The function name to call.
     * @param parameters A map of string key value pairs representing arguments to be passed to the API.
     * @param callback   the callback that will be called with the resulting [KeyValue] object.
     * @param error      the callback for handling response errors.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(
        function: String,
        parameters: Map<String, String>,
        callback: Consumer<KeyValue>,
        error: Consumer<WebAPIRequestException>,
    ) {
        call("GET", function, 1, parameters, callback, error)
    }

    /**
     * Manually calls the specified Web API function with the provided details. This method is asynchronous.
     *
     * @param function The function name to call.
     * @param callback the callback that will be called with the resulting [KeyValue] object.
     * @param error    the callback for handling response errors.
     * @throws IOException if the request could not be executed
     */
    @Throws(IOException::class)
    fun call(function: String, callback: Consumer<KeyValue>, error: Consumer<WebAPIRequestException>?) {
        call("GET", function, 1, null, callback, error)
    }

    private fun parseResponse(response: Response): KeyValue {
        val kv = KeyValue()

        try {
            response.body.byteStream().use(kv::readAsText)
        } catch (e: Exception) {
            throw IllegalStateException(
                "An internal error occurred when attempting to parse the response from the WebAPI server. " +
                    "This can indicate a change in the VDF format.",
                e
            )
        }

        return kv
    }

    private fun buildRequest(
        httpMethod: String,
        function: String,
        version: Int,
        parameters: Map<String, String>?,
    ): Request {
        val params = parameters?.toMutableMap() ?: LinkedHashMap()

        require(
            !(
                !httpMethod.equals("GET", ignoreCase = true) &&
                    !httpMethod.equals("POST", ignoreCase = true)
                )
        ) {
            "only GET and POST is supported right now"
        }

        params["format"] = "vdf"

        if (webAPIKey.isNotEmpty()) {
            params["key"] = webAPIKey
        }

        val builder: Request.Builder = Request.Builder()
            .header("User-Agent", "JavaSteam-${Versions.getVersion()}")

        val urlBuilder: HttpUrl.Builder = baseAddress.toHttpUrl().newBuilder()
            .addPathSegment(`interface`)
            .addPathSegment(function)
            .addPathSegment("v$version")

        if (httpMethod.equals("GET", ignoreCase = true)) {
            params.forEach { (key, value) ->
                urlBuilder.addEncodedQueryParameter(WebHelpers.urlEncode(key), value)
            }
            builder.get()
        } else {
            val bodyBuilder: FormBody.Builder = FormBody.Builder()
            params.forEach { (key, value) ->
                bodyBuilder.addEncoded(WebHelpers.urlEncode(key), value)
            }
            builder.post(bodyBuilder.build())
        }

        val url: HttpUrl = urlBuilder.build()

        return builder.url(url).build()
    }

    /**
     * Thrown when WebAPI request fails (non success response code).
     */
    class WebAPIRequestException internal constructor(response: Response) : IOException(response.message) {
        /**
         * Gets the status code of the response.
         * @return the status code of the response.
         */
        val statusCode: Int = response.code

        /**
         * Gets the headers of the response.
         * @return headers of the response.
         */
        val headers: Map<String, List<String>> = response.headers.toMultimap()
    }

    companion object {
        const val DEFAULT_BASE_ADDRESS: String = "https://api.steampowered.com/"
    }
}
