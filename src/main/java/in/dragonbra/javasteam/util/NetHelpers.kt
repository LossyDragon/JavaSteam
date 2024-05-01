package `in`.dragonbra.javasteam.util

import org.apache.commons.validator.routines.InetAddressValidator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer

/**
 * @author lngtr
 * @since 2018-02-22
 */
object NetHelpers {

    @JvmStatic
    fun getIPAddress(ipAddr: Int): InetAddress? {
        val b = ByteBuffer.allocate(4)
        b.putInt(ipAddr)

        val addr = try {
            InetAddress.getByAddress(b.array())
        } catch (e: UnknownHostException) {
            null
        }

        return addr
    }

    @JvmStatic
    fun getIPAddress(ip: InetAddress?): Int {
        val buff = ByteBuffer.wrap(ip?.address).getInt().toLong()
        return (buff and 0xFFFFFFFFL).toInt()
    }

    @JvmStatic
    fun tryParseIPEndPoint(address: String?): InetSocketAddress? {
        if (address == null) {
            return null
        }

        val split = address.split(":")

        if (!InetAddressValidator.getInstance().isValidInet4Address(split[0])) {
            return null
        }

        try {
            if (split.size > 1) {
                return InetSocketAddress(split[0], split[1].toInt())
            }
        } catch (exception: IllegalArgumentException) {
            // no-op
        }

        return null
    }
}
