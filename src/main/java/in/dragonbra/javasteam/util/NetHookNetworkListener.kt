package `in`.dragonbra.javasteam.util

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.util.log.LogManager.getLogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Dump any network messages sent to and received from the Steam server that the client is connected to.
 * These messages are dumped to file, and can be analyzed further with NetHookAnalyzer, a hex editor, or your own purpose-built tools.
 * <strong>Be careful with this, sensitive data may be written to the disk (such as your Steam password.</strong>
 */
class NetHookNetworkListener @JvmOverloads constructor(path: String = "netlogs") : IDebugNetworkListener {

    private val messageNumber = AtomicLong(0L)

    private val logDirectory = File(path, SimpleDateFormat("yyyy_MM_dd_H_m_s_S").format(Date())).apply {
        mkdirs()
    }

    override fun onIncomingNetworkMessage(msgType: EMsg, data: ByteArray) {
        logMessage(DIRECTION.INCOMING, msgType, data)
    }

    override fun onOutgoingNetworkMessage(msgType: EMsg, data: ByteArray) {
        logMessage(DIRECTION.OUTGOING, msgType, data)
    }

    private fun logMessage(direction: DIRECTION, msgType: EMsg, data: ByteArray) {
        val fileName = String.format(
            "%d_%s_%d_k_EMsg%s.bin",
            messageNumber.getAndIncrement(),
            direction,
            msgType.code(),
            msgType
        )

        try {
            val file = File(logDirectory, fileName)
            val msg = when (direction) {
                DIRECTION.INCOMING -> String.format("<- Rec'd EMsg: %s (%d)", msgType, msgType.code())
                DIRECTION.OUTGOING -> String.format("Sent -> EMsg: %s", msgType)
            }

            logger.debug(msg)
            Files.write(Paths.get(file.absolutePath), data)
        } catch (e: IOException) {
            logger.debug("Error writing message to file: ${e.message}", e)
        }
    }

    internal enum class DIRECTION {
        INCOMING,
        OUTGOING,
    }

    companion object {
        private val logger = getLogger(NetHookNetworkListener::class.java)
    }
}
