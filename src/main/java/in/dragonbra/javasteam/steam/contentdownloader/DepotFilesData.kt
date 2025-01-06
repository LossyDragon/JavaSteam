package `in`.dragonbra.javasteam.steam.contentdownloader

import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.FileData

data class DepotFilesData(
    val depotDownloadInfo: DepotDownloadInfo,
    val depotCounter: DepotDownloadCounter,
    val stagingDir: String,
    val manifest: DepotManifest,
    val previousManifest: DepotManifest?,
    val filteredFiles: ArrayList<FileData> = arrayListOf(),
    val allFileNames: Set<String> = emptySet(),
)
