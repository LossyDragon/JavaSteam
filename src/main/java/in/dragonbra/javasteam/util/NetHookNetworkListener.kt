package `in`.dragonbra.javasteam.util

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/**
 * Dump any network messages sent to and received from the Steam server that the client is connected to.
 * These messages are dumped to file, and can be analyzed further with NetHookAnalyzer, a hex editor, or your own purpose-built tools.
 *
 * Be careful with this, sensitive data may be written to the disk (such as your Steam password).
 */
class NetHookNetworkListener @JvmOverloads constructor(path: String = "netlogs") : IDebugNetworkListener {

    companion object {
        private val logger: Logger = LogManager.getLogger(NetHookNetworkListener::class.java)

        private val FORMAT = SimpleDateFormat("yyyy_MM_dd_H_m_s_S")
    }

    private val messageNumber = AtomicLong(0L)

    private val logDirectory: File

    init {
        val dir = File(path)
        dir.mkdirs()

        logDirectory = File(dir, FORMAT.format(Date()))
        logDirectory.mkdirs()
    }

    override fun onIncomingNetworkMessage(msgType: EMsg, data: ByteArray) {
        logger.debug(String.format("<- Recv'd EMsg: %s (%d)", msgType, msgType.code()))
        writeFile("in", msgType, data)
    }

    override fun onOutgoingNetworkMessage(msgType: EMsg, data: ByteArray) {
        logger.debug(String.format("Sent -> EMsg: %s", msgType))
        writeFile("out", msgType, data)
    }

    // Combine incoming|outgoing writer methods.
    private fun writeFile(direction: String, msgType: EMsg, data: ByteArray) {
        try {
            val file = File(logDirectory, getFile(direction, msgType))
            Files.write(file.toPath(), data)
        } catch (e: IOException) {
            logger.error(e)
        }
    }

    private fun getFile(direction: String, msgType: EMsg): String =
        "${messageNumber.getAndIncrement()}_${direction}_${msgType.code()}_k_EMsg$msgType.bin"
}
