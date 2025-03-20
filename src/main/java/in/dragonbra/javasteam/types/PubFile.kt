package `in`.dragonbra.javasteam.types

/**
 * The base class used for wrapping common Long types, to introduce type safety and distinguish between common types.
 */
abstract class LongHandle : Comparable<LongHandle> {
    /**
     * Gets or sets the value.
     */
    protected var value: Long = 0

    /**
     * Initializes a new instance of the LongHandle class.
     */
    constructor()

    /**
     * Initializes a new instance of the LongHandle class.
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
        if (other is LongHandle) {
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
     * Compares this object with the specified object for order.
     * @param other the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     */
    override fun compareTo(other: LongHandle): Int = value.compareTo(other.value)
}

/**
 * Represents a handle to a published file on the Steam workshop.
 *
 * @constructor Initializes a new instance of the PublishedFileID class.
 * @param fileId The file id.
 */
class PublishedFileID(fileId: Long = Long.MAX_VALUE) : LongHandle(fileId) {

    companion object {
        /**
         * Converts a PublishedFileID to a Long.
         * @param file The published file.
         * @return The result of the conversion.
         */
        @JvmStatic
        fun toLong(file: PublishedFileID): Long = file.value

        /**
         * Converts a Long to a PublishedFileID.
         * @param fileId The file id.
         * @return The result of the conversion.
         */
        @JvmStatic
        fun fromLong(fileId: Long): PublishedFileID = PublishedFileID(fileId)

        /**
         * Checks if two PublishedFileID objects are equal.
         */
        fun equals(a: PublishedFileID?, b: PublishedFileID?): Boolean {
            if (a === b) {
                return true
            }

            if (a == null || b == null) {
                return false
            }

            return a.value == b.value
        }
    }

    /**
     * Overrides the equals operator.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublishedFileID) return false
        return value == other.value
    }

    /**
     * Returns a hash code for this instance.
     * @return A hash code for this instance, suitable for use in hashing algorithms and data structures like a hash table.
     */
    override fun hashCode(): Int = value.hashCode()
}
