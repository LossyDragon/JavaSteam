package `in`.dragonbra.javasteam.contentdownloader

sealed class DirectoryResult {
    data class Success(val installDir: String) : DirectoryResult()
    data object Failed : DirectoryResult()
}
