package `in`.dragonbra.javasteam.types

/**
 * Represents a handle to a published file on the Steam workshop.
 */
class PublishedFileID : UInt64Handle {

    /**
     * Initializes a new instance of the PublishedFileID class.
     * @param fileId The file id.
     */
    constructor(fileId: Long = Long.MAX_VALUE) : super(fileId)

    /**
     * Implements the operator ==.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (other !is PublishedFileID) return false
        return value == other.value
    }

    /**
     * Implements the operator !=.
     */
    override fun hashCode(): Int = value.hashCode()

    companion object {

        @JvmStatic
        fun PublishedFileID.toLong(): Long = this.value

        @JvmStatic
        fun Long.toPublishedFileID(): PublishedFileID = PublishedFileID(this)

        /**
         * Compares two PublishedFileID instances for equality.
         *
         * @param a The first published file.
         * @param b The second published file.
         * @return true if the instances are equal; otherwise, false.
         */
        fun areEqual(a: PublishedFileID?, b: PublishedFileID?): Boolean {
            if (a === b) {
                return true
            }

            if (a == null || b == null) {
                return false
            }

            return a.value == b.value
        }
    }
}
