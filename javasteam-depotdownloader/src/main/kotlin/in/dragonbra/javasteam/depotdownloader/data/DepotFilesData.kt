package `in`.dragonbra.javasteam.depotdownloader.data

import `in`.dragonbra.javasteam.depotdownloader.DepotDownloadInfo
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.FileData

data class DepotFilesData(
    var depotDownloadInfo: DepotDownloadInfo? = null,
    var depotCounter: DepotDownloadCounter? = null,
    var stagingDir: String? = "",
    var manifest: DepotManifest? = null,
    var previousManifest: DepotManifest? = null,
    var filteredFiles: ArrayList<FileData> = arrayListOf(),
    var allFileNames: HashSet<String> = hashSetOf(),
)
