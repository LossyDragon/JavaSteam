package `in`.dragonbra.javasteam.util

/**
 * A data class wrapper to hold information to connect via a proxy
 *
 * @param proxyAddress the address the proxy is hosted at.
 * @param proxyPort the port for the proxy.
 * @param proxyAuthUserName Optional: the username to login to the proxy.
 * @param proxyAuthPassword Optional: the password to login to the proxy.
 */
data class ProxyWrapper(
    val proxyAddress: String,
    val proxyPort: Int,
    val proxyAuthUserName: String? = null,
    val proxyAuthPassword: String? = null,
)
