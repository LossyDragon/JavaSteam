package `in`.dragonbra.javasteam.steam.contentdownloader

import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.FileData

/**
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
data class DepotFilesData(
    val depotDownloadInfo: DepotDownloadInfo,
    val depotCounter: DepotDownloadCounter,
    val stagingDir: String,
    val manifest: DepotManifest,
    val previousManifest: DepotManifest?,
    val filteredFiles: List<FileData>,
    val allFileNames: HashSet<String>,
)
