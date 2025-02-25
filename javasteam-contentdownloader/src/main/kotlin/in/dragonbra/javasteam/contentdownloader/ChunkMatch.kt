package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.types.ChunkData

/**
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
data class ChunkMatch(
    val oldChunk: ChunkData,
    val newChunk: ChunkData,
)
