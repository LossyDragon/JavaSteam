package `in`.dragonbra.javasteam.types

class PubFile {

    /**
     * The base class used for wrapping common ulong types, to introduce type safety and distinguish between common types.
     *
     * @constructor Initializes a new instance of the [UInt64Handle] class.
     * @param value Gets or sets the value.
     */
    abstract class UInt64Handle @JvmOverloads constructor(
        protected var value: Long = 0
    ) {

        /**
         * Returns a hash code for this instance.
         * @return A hash code for this instance, suitable for use in hashing algorithms and data structures like a hash table.
         */
        override fun hashCode(): Int = value.hashCode()

        /**
         * Indicates whether the current object is equal to another object of the same type.
         * @param other An object to compare with this object.
         * @return true if the current object is equal to the other parameter; otherwise, false.
         */
        override fun equals(other: Any?): Boolean {
            if (other is UInt64Handle) {
                return other.value == value
            }
            return false
        }

        /**
         * Returns a [String] that represents this instance.
         * @return a [String] that represents this instance.
         */
        override fun toString(): String = value.toString()
    }

    /**
     * Represents a handle to a published file on the Steam workshop.
     * @constructor Initializes a new instance of the [PublishedFileID] class.
     * @param fileId The file id.
     */
    class PublishedFileID @JvmOverloads constructor(
        fileId: Long = Long.MAX_VALUE
    ) : UInt64Handle(fileId) {

        @JvmName("toLong")
        fun toLong(): Long = value

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PublishedFileID) return false
            return value == other.value
        }

        override fun hashCode(): Int = value.hashCode()
    }
}
