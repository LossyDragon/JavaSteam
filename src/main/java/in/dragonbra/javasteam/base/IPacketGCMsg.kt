package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.types.JobID

/**
 * Represents a simple unified interface into game coordinator messages recieved from the network.
 * This is contrasted with [IClientGCMsg] in that this interface is packet body agnostic
 * and only allows simple access into the header. This interface is also immutable, and the underlying
 * data cannot be modified.
 */
interface IPacketGCMsg {

    /**
     * Gets a value indicating whether this packet message is protobuf backed.
     * @return **true** if this instance is protobuf backed; otherwise, **false**.
     */
    val isProto: Boolean

    /**
     * Gets the network message type of this packet message.
     */
    val msgType: Int

    /**
     * Gets the target job id for this packet message.
     */
    val targetJobID: JobID

    /**
     * Gets the source job id for this packet message.
     */
    val sourceJobID: JobID

    /**
     * Gets the underlying data that represents this client message.
     */
    val data: ByteArray
}
