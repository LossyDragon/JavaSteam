package `in`.dragonbra.javasteam.types

import `in`.dragonbra.javasteam.util.compat.ObjectsCompat
import java.util.*

/**
 * Represents a globally unique identifier within the Steam network.
 * Guaranteed to be unique across all racks and servers for a given Steam universe.
 *
 * @constructor Initializes a new instance of the [GlobalID] class.
 * @param gid The GID value.
 */
open class GlobalID @JvmOverloads constructor(gid: Long = -0x1L) {

    private val gidBits = BitVector64(gid)

    /**
     * Gets or Sets the sequential count for this GID.
     * @return The sequential count.
     */
    var sequentialCount: Long
        get() = gidBits.getMask(0.toShort(), 0xFFFFFL)
        set(value) {
            gidBits.setMask(0.toShort(), 0xFFFFFL, value)
        }

    /**
     * Gets or Sets the start time of the server that generated this GID.
     * @return The start time.
     */
    var startTime: Date
        get() {
            val startTimeS = gidBits.getMask(20.toShort(), 0x3FFFFFFFL)
            return Date(startTimeS * 1000L)
        }
        set(startTime) {
            val startTimeS = (startTime.time - 1104537600000L) / 1000L
            gidBits.setMask(20.toShort(), 0x3FFFFFFFL, startTimeS)
        }

    /**
     * Gets or Sets the process ID of the server that generated this GID.
     * @return The process ID.
     */
    var processID: Long
        get() = gidBits.getMask(50.toShort(), 0xFL)
        set(value) {
            gidBits.setMask(50.toShort(), 0xFL, value)
        }

    /**
     * Gets or Sets the box ID of the server that generated this GID.
     * @return The box ID.
     */
    var boxID: Long
        get() = gidBits.getMask(54.toShort(), 0x3FFL)
        set(value) {
            gidBits.setMask(54.toShort(), 0x3FFL, value)
        }

    /**
     * Gets or Sets the entire 64bit value of this GID.
     * @return The value.
     */
    var value: Long
        get() = gidBits.data!!
        set(value) {
            gidBits.data = value
        }

    /**
     * Determines whether the specified [Object] is equal to this instance.
     * @param other The [Object] to compare with this instance.
     * @return **true** if the specified [Object] is equal to this instance; otherwise, **false**.
     */
    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (other !is GlobalID) {
            return false
        }

        return ObjectsCompat.equals(gidBits.data, other.gidBits.data)
    }

    /**
     * Returns a hash code for this instance.
     * @return A hash code for this instance, suitable for use in hashing algorithms and data structures like a hash table.
     */
    override fun hashCode(): Int = gidBits.data.hashCode()

    /**
     * Returns a [String] that represents this instance.
     * @return a [String] that represents this instance.
     */
    override fun toString(): String = value.toString()
}
