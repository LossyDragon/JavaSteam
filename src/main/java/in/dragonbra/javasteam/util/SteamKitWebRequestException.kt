package `in`.dragonbra.javasteam.util

import okhttp3.Headers
import okhttp3.Response
import java.lang.Exception

/**
 * Thrown when an HTTP request fails.
 *
 * @constructor Initializes a new instance of the [SteamKitWebRequestException] class.
 * @param message The message that describes the error.
 * @param response HTTP response message including the status code and data.
 */
@Suppress("unused")
class SteamKitWebRequestException(message: String, response: Response? = null) : Exception(message) {

    /**
     * Represents the status code of the HTTP response.
     */
    val statusCode: Int = response?.code ?: 0

    /**
     * Represents the collection of HTTP response headers.
     */
    val headers: Headers? = response?.headers
}
