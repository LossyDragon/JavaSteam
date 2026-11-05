package in.dragonbra.javasteam.base;

import in.dragonbra.javasteam.enums.EMsg;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientHeartBeat;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientLogOff;
import in.dragonbra.javasteam.steam.CMClient;
import in.dragonbra.javasteam.types.JobID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@link java.util.function.Supplier} based constructors from Java, where the body type
 * must be inferred from a {@code newBuilder} method reference through the diamond operator.
 */
public class ClientMsgProtobufTest {

    @Test
    public void sendConstructorInfersBodyTypeFromMethodReference() {
        var msg = new ClientMsgProtobuf<>(CMsgClientLogOff::newBuilder, EMsg.ClientLogOff);

        // the inferred type must be the concrete builder, otherwise this assignment would not compile
        CMsgClientLogOff.Builder body = msg.getBody();

        assertEquals(EMsg.ClientLogOff, msg.getMsgType());
        assertTrue(msg.isProto());
        assertEquals(0, body.build().getSerializedSize());
    }

    @Test
    public void receiveConstructorRoundTripsThroughPacket() {
        var outgoing = new ClientMsgProtobuf<>(CMsgClientHeartBeat::newBuilder, EMsg.ClientHeartBeat);
        outgoing.getBody().setSendReply(true);
        outgoing.setSourceJobID(new JobID(1234));

        IPacketMsg packet = CMClient.getPacketMsg(outgoing.serialize());
        var incoming = new ClientMsgProtobuf<>(CMsgClientHeartBeat::newBuilder, packet);

        assertEquals(EMsg.ClientHeartBeat, incoming.getMsgType());
        assertTrue(incoming.getBody().getSendReply());
        assertEquals(new JobID(1234), incoming.getSourceJobID());
    }

    @Test
    public void replyConstructorTargetsSourceJob() {
        var request = new ClientMsgProtobuf<>(CMsgClientHeartBeat::newBuilder, EMsg.ClientHeartBeat);
        request.setSourceJobID(new JobID(42));

        var reply = new ClientMsgProtobuf<>(CMsgClientHeartBeat::newBuilder, EMsg.ClientHeartBeat, request);

        assertEquals(new JobID(42), reply.getTargetJobID());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedClassConstructorStillWorks() {
        ClientMsgProtobuf<CMsgClientHeartBeat.Builder> outgoing = new ClientMsgProtobuf<>(CMsgClientHeartBeat.class, EMsg.ClientHeartBeat);
        outgoing.getBody().setSendReply(true);

        IPacketMsg packet = CMClient.getPacketMsg(outgoing.serialize());
        ClientMsgProtobuf<CMsgClientHeartBeat.Builder> incoming = new ClientMsgProtobuf<>(CMsgClientHeartBeat.class, packet);

        assertTrue(incoming.getBody().getSendReply());
    }
}
