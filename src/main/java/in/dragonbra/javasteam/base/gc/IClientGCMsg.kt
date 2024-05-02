package `in`.dragonbra.javasteam.base.gc

import `in`.dragonbra.javasteam.types.JobID

/**
 * Represents a unified interface into client messages.
 */
interface IClientGCMsg {

    /**
     * Gets a value indicating whether this client message is protobuf backed.
     * @return **true** if this instance is protobuf backed; otherwise, **false**.
     */
    val isProto: Boolean

    /**
     * Gets the network message type of this client message.
     * @return The message type.
     */
    val msgType: Int

    /**
     * Gets or sets the target job id for this client message.
     * @return The target job id.
     */
    var targetJobID: JobID

    /**
     * Gets or sets the source job id for this client message.
     * @return The source job id.
     */
    var sourceJobID: JobID

    /**
     * Serializes this client message instance to a byte array.
     * @return Data representing a client message.
     */
    fun serialize(): ByteArray

    /**
     * Initializes this client message by deserializing the specified data.
     * @param data The data representing a client message.
     */
    fun deserialize(data: ByteArray)
}
