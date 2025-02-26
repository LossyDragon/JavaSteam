package in.dragonbra.javasteam.base;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.GeneratedMessage;
import in.dragonbra.javasteam.generated.MsgGCHdrProtoBuf;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase.CMsgProtoBufHeader;
import in.dragonbra.javasteam.types.JobID;
import in.dragonbra.javasteam.util.log.LogManager;
import in.dragonbra.javasteam.util.log.Logger;
import in.dragonbra.javasteam.util.stream.BinaryReader;
import in.dragonbra.javasteam.util.stream.SeekOrigin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Represents a protobuf backed game coordinator message.
 *
 * @param <BodyType> The body type of this message.
 */
@SuppressWarnings("unused")
public class ClientGCMsgProtobuf<BodyType extends GeneratedMessage.Builder<BodyType>> extends GCMsgBase<MsgGCHdrProtoBuf> {

    private static final Logger logger = LogManager.getLogger(ClientGCMsgProtobuf.class);

    private BodyType body;

    private final Class<? extends AbstractMessage> clazz;

    /**
     * Initializes a new instance of the {@link ClientMsgProtobuf} class.
     * This is a client send constructor.
     *
     * @param clazz the type of the body
     * @param msg   The network message type this client message represents.
     */
    public ClientGCMsgProtobuf(Class<? extends AbstractMessage> clazz, IPacketGCMsg msg) {
        this(clazz, msg.getMsgType());
        if (!msg.isProto()) {
            logger.debug("ClientMsgProtobuf<" + clazz.getSimpleName() + "> used for non-proto message!");
        }
        deserialize(msg.getData());
    }

    /**
     * Initializes a new instance of the {@link ClientGCMsgProtobuf} class.
     * This is a client send constructor.
     *
     * @param clazz the type of the body
     * @param eMsg  The network message type this client message represents.
     */
    public ClientGCMsgProtobuf(Class<? extends AbstractMessage> clazz, int eMsg) {
        this(clazz, eMsg, 64);
    }

    /**
     * Initializes a new instance of the {@link ClientGCMsgProtobuf} class.
     * This is a client send constructor.
     *
     * @param clazz          the type of the body
     * @param eMsg           The network message type this client message represents.
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    @SuppressWarnings("unchecked")
    public ClientGCMsgProtobuf(Class<? extends AbstractMessage> clazz, int eMsg, int payloadReserve) {
        super(MsgGCHdrProtoBuf.class, payloadReserve);
        this.clazz = clazz;

        try {
            final Method m = clazz.getMethod("newBuilder");
            body = (BodyType) m.invoke(null);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            logger.debug(e);
        }

        getHeader().setEMsg(eMsg);
    }

    /**
     * Initializes a new instance of the {@link ClientGCMsgProtobuf} class.
     * This is a reply constructor.
     *
     * @param clazz the type of the body
     * @param eMsg  The network message type this client message represents.
     * @param msg   The message that this instance is a reply for.
     */
    public ClientGCMsgProtobuf(Class<? extends AbstractMessage> clazz, int eMsg, GCMsgBase<MsgGCHdrProtoBuf> msg) {
        this(clazz, eMsg, msg, 64);
    }

    /**
     * Initializes a new instance of the {@link ClientGCMsgProtobuf} class.
     * This is a reply constructor.
     *
     * @param clazz          the type of the body
     * @param eMsg           The network message type this client message represents.
     * @param msg            The message that this instance is a reply for.
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    public ClientGCMsgProtobuf(Class<? extends AbstractMessage> clazz, int eMsg, GCMsgBase<MsgGCHdrProtoBuf> msg, int payloadReserve) {
        this(clazz, eMsg, payloadReserve);

        if (msg == null) {
            throw new IllegalArgumentException("msg is null");
        }

        // our target is where the message came from
        getHeader().getProto().setJobidTarget(msg.getHeader().getProto().getJobidSource());
    }

    @Override
    public boolean isProto() {
        return true;
    }

    @Override
    public int getMsgType() {
        return getHeader().getMsg();
    }

    @Override
    public JobID getTargetJobID() {
        return new JobID(getProtoHeader().getJobidTarget());
    }

    @Override
    public void setTargetJobID(JobID jobID) {
        if (jobID == null) {
            throw new IllegalArgumentException("jobID is null");
        }
        getProtoHeader().setJobidTarget(jobID.getValue());
    }

    @Override
    public JobID getSourceJobID() {
        return new JobID(getProtoHeader().getJobidSource());
    }

    @Override
    public void setSourceJobID(JobID jobID) {
        if (jobID == null) {
            throw new IllegalArgumentException("jobID is null");
        }
        getProtoHeader().setJobidSource(jobID.getValue());
    }

    @Override
    public byte[] serialize() {
        try (var baos = new ByteArrayOutputStream()) {
            getHeader().serialize(baos);
            body.build().writeTo(baos);
            baos.write(payload.toByteArray());
            return baos.toByteArray();
        } catch (IOException ignored) {
        }

        return new byte[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    public void deserialize(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }

        try (var ms = new BinaryReader(new ByteArrayInputStream(data))) {
            getHeader().deserialize(ms);
            final Method m = clazz.getMethod("newBuilder");
            body = (BodyType) m.invoke(null);
            body.mergeFrom(ms);
            payload.write(data, ms.getPosition(), ms.available());
            payload.seek(0, SeekOrigin.BEGIN);
        } catch (IOException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            logger.debug(e);
        }
    }

    /**
     * Shorthand accessor for the protobuf header.
     *
     * @return the protobuf header
     */
    public CMsgProtoBufHeader.Builder getProtoHeader() {
        return getHeader().getProto();
    }

    /**
     * @return the body structure of this message
     */
    public BodyType getBody() {
        return body;
    }
}
