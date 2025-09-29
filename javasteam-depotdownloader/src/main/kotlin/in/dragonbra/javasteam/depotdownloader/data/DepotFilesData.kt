package `in`.dragonbra.javasteam.depotdownloader.data

import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.FileData
import okio.Path

data class DepotFilesData(
    val depotDownloadInfo: DepotDownloadInfo,
    val depotCounter: DepotDownloadCounter,
    val stagingDir: Path,
    val manifest: DepotManifest,
    val previousManifest: DepotManifest?,
    val filteredFiles: MutableList<FileData>,
    val allFileNames: HashSet<String>,
)
