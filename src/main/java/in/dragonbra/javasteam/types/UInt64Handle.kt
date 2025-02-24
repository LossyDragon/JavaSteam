package `in`.dragonbra.javasteam.types

/**
 * The base class used for wrapping common Long types, to introduce type safety and distinguish between common types.
 */
abstract class UInt64Handle {

    /**
     * Gets or sets the value.
     */
    protected var value: Long

    /**
     * Initializes a new instance of the UInt64Handle class.
     */
    constructor() {
        this.value = 0L
    }

    /**
     * Initializes a new instance of the UInt64Handle class.
     * @param value The value to initialize this handle to.
     */
    protected constructor(value: Long) {
        this.value = value
    }

    /**
     * Returns a hash code for this instance.
     * @return A hash code for this instance, suitable for use in hashing algorithms and data structures like a hash table.
     */
    override fun hashCode(): Int = value.hashCode()

    /**
     * Determines whether the specified object is equal to this instance.
     * @param other The object to compare with this instance.
     * @return true if the specified object is equal to this instance; otherwise, false.
     */
    override fun equals(other: Any?): Boolean {
        if (other is UInt64Handle) {
            return other.value == value
        }

        return false
    }

    /**
     * Returns a string that represents this instance.
     * @return A string that represents this instance.
     */
    override fun toString(): String = value.toString()

    /**
     * Indicates whether the current object is equal to another object of the same type.
     * @param other An object to compare with this object.
     * @return true if the current object is equal to the other parameter; otherwise, false.
     */
    fun equals(other: UInt64Handle?): Boolean {
        if (other == null) {
            return false
        }
        return value == other.value
    }
}
