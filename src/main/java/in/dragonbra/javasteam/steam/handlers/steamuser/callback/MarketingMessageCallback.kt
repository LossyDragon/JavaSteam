package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.enums.EMarketingMessageFlags
import `in`.dragonbra.javasteam.generated.MsgClientMarketingMessageUpdate2
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.GlobalID
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * This callback is fired when the client receives a marketing message update.
 */
class MarketingMessageCallback(body: MsgClientMarketingMessageUpdate2, payload: ByteArray) : CallbackMsg() {

    /**
     * Gets the time of this marketing message update.
     * @return the time of this marketing message update.
     */
    val updateTime: Date = Date(body.marketingMessageUpdateTime * 1000L)

    /**
     * Gets the messages.
     * @return the messages as a collection of [Message]
     */
    val messages: Collection<Message>

    init {
        val msgList: MutableList<Message> = ArrayList()

        try {
            BinaryReader(ByteArrayInputStream(payload)).use { br ->
                for (i in 0 until body.count) {
                    val dataLen = br.readInt() - 4 // total length includes the 4 byte length
                    val messageData = br.readBytes(dataLen)

                    msgList.add(Message(messageData))
                }
            }
        } catch (e: IOException) {
            logger.debug(e)
        }

        messages = Collections.unmodifiableList(msgList)
    }

    /**
     * Represents a single marketing message.
     */
    class Message internal constructor(data: ByteArray) {

        /**
         * @return the unique identifier for this marketing message. See [GlobalID].
         */
        var id: GlobalID? = null
            private set

        /**
         * @return the URL for this marketing message.
         */
        var url: String? = null
            private set

        /**
         * @return the marketing message flags. See [EMarketingMessageFlags].
         */
        var flags: EnumSet<EMarketingMessageFlags>? = null
            private set

        init {
            try {
                BinaryReader(ByteArrayInputStream(data)).use { br ->
                    id = GlobalID(br.readLong())
                    url = br.readNullTermString(StandardCharsets.UTF_8)
                    flags = EMarketingMessageFlags.from(br.readInt())
                }
            } catch (e: IOException) {
                logger.debug(e)
            }
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(MarketingMessageCallback::class.java)
    }
}
