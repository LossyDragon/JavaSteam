package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientHeartBeat
import `in`.dragonbra.javasteam.steam.CMClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the [java.util.function.Supplier] based constructors from Kotlin, where the body type must be
 * inferred from a `newBuilder` callable reference (which is overloaded on the generated message classes).
 */
class ClientMsgProtobufKotlinTest {

    @Test
    fun `body type is inferred from the newBuilder reference`() {
        val msg = ClientMsgProtobuf(CMsgClientHeartBeat::newBuilder, EMsg.ClientHeartBeat)

        // would not compile if BodyType were inferred as anything other than CMsgClientHeartBeat.Builder
        val body: CMsgClientHeartBeat.Builder = msg.body
        body.sendReply = true

        assertEquals(EMsg.ClientHeartBeat, msg.msgType)
        assertTrue(msg.body.sendReply)
    }

    @Test
    fun `round trips through a packet`() {
        val outgoing = ClientMsgProtobuf(CMsgClientHeartBeat::newBuilder, EMsg.ClientHeartBeat).apply {
            body.sendReply = true
        }

        val packet = CMClient.getPacketMsg(outgoing.serialize())
        val incoming = ClientMsgProtobuf(CMsgClientHeartBeat::newBuilder, packet)

        assertTrue(incoming.body.sendReply)
    }
}
