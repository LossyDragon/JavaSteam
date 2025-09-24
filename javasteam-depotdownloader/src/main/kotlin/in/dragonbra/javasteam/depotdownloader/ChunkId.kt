package `in`.dragonbra.javasteam.depotdownloader

// Custom wrapper class for byte arrays to provide proper equals/hashCode matching C# behavior (ChunkIdComparer)
data class ChunkId(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkId) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        // ChunkID is SHA-1, so we can just use the first 4 bytes like the C# version
        require(bytes.size >= 4) { "ChunkId must be at least 4 bytes" }
        return (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
    }
}
