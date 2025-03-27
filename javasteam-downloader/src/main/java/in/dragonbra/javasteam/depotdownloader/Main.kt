package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.depotdownloader.ContentDownloader.ContentDownloaderException
import `in`.dragonbra.javasteam.steam.cdn.ClientLancache
import `in`.dragonbra.javasteam.util.Versions
import `in`.dragonbra.javasteam.util.log.LogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_PASSWORD_SIZE = 64

fun main(args: Array<String>) = runBlocking<Unit> {
    if (args.isEmpty()) {
        printVersion()
        printUsage()
        return@runBlocking
    }

    // No Debugging

    AccountSettingsStore.loadFromFile("account.config")

    // region Common Options

    // Not using hasParameter because it is case-insensitive
    if (args.size == 1 && (args[0] == "-V" || args[0] == "--version")) {
        printVersion(true)
        return@runBlocking
    }

    if (hasParameter(args, "-debug")) {
        printVersion(true)

        LogManager.addListener(
            object : LogListener {
                override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
                    val logMessage = message ?: "No message given"
                    println("\u001B[34m[${clazz.simpleName}] -> $logMessage\u001B[0m")
                    throwable?.printStackTrace()
                }

                override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
                    val logMessage = message ?: "No message given"
                    logE("[${clazz.simpleName}] -> $logMessage")
                    throwable?.printStackTrace()
                }
            },
        )

        // var httpEventListener = new HttpDiagnosticEventListener ()
    }

    val username = getParameter<String>(args, "-username") ?: getParameter<String>(args, "-user")
    val password = getParameter<String>(args, "-password") ?: getParameter<String>(args, "-pass")
    ContentDownloader.config.rememberPassword = hasParameter(args, "-remember-password")
    ContentDownloader.config.useQrCode = hasParameter(args, "-qr")

    if (username == null) {
        if (ContentDownloader.config.rememberPassword) {
            logI("Error: -remember-password can not be used without -username.")
            return@runBlocking
        }

        if (ContentDownloader.config.useQrCode) {
            logI("Error: -qr can not be used without -username.")
            return@runBlocking
        }
    }

    ContentDownloader.config.downloadManifestOnly = hasParameter(args, "-manifest-only")

    var cellId = getParameter(args, "-cellid", -1)
    if (cellId == -1) {
        cellId = 0
    }

    ContentDownloader.config.cellID = cellId!!

    val fileList = getParameter<String>(args, "-filelist")

    if (fileList != null) {
        val regexPrefix = "regex:"

        try {
            ContentDownloader.config.usingFileList = true
            // var filesToDownload: MutableSet<String> = TreeSet(String.CASE_INSENSITIVE_ORDER)
            ContentDownloader.config.filesToDownloadRegex = mutableListOf()

            val files = Files.readAllLines(Paths.get(fileList), StandardCharsets.UTF_8)

            files.forEach { fileEntry ->
                if (fileEntry.isBlank()) {
                    return@forEach
                }

                if (fileEntry.startsWith(regexPrefix)) {
                    val rgx = Regex(fileEntry.substring(regexPrefix.length), RegexOption.IGNORE_CASE)
                    ContentDownloader.config.filesToDownloadRegex.add(rgx)
                } else {
                    ContentDownloader.config.filesToDownload.add(fileEntry.replace('\\', '/'))
                }
            }

            logI("Using filelist: '$fileList'.")
        } catch (ex: Exception) {
            logI("Warning: Unable to load filelist: ${ex.message}")
        }
    }

    ContentDownloader.config.installDirectory = getParameter<String>(args, "-dir").orEmpty()

    ContentDownloader.config.verifyAll = hasParameter(args, "-verify-all") ||
        hasParameter(args, "-verify_all") ||
        hasParameter(args, "-validate")

    if (hasParameter(args, "-use-lancache")) {
        ClientLancache.detectLancacheServer().await()
        if (ClientLancache.useLanCacheServer) {
            logI("Detected Lancache server! Downloads will be directed through the Lancache.")
            // Increasing the number of concurrent downloads when the cache is detected since the downloads will likely
            // be served much faster than over the internet.  Steam internally has this behavior as well.
            if (!hasParameter(args, "-max-downloads")) {
                ContentDownloader.config.maxDownloads = 25
            }
        }
    }

    ContentDownloader.config.maxDownloads = getParameter(args, "-max-downloads", 8)!!
    ContentDownloader.config.loginID = if (hasParameter(args, "-loginid")) {
        getParameter<Int>(args, "-loginid")
    } else {
        null
    }

    // endregion

    val appId = getParameter(args, "-app", ContentDownloader.INVALID_APP_ID)
    if (appId == ContentDownloader.INVALID_APP_ID) {
        logI("Error: -app not specified!")
        return@runBlocking
    }

    val pubFile = getParameter(args, "-pubfile", ContentDownloader.INVALID_MANIFEST_ID)
    val ugcId = getParameter(args, "-ugc", ContentDownloader.INVALID_MANIFEST_ID)
    if (pubFile != ContentDownloader.INVALID_MANIFEST_ID) {
        // region Pubfile Downloading

        if (initializeSteam(username, password)) {
            val progressJob = monitorDownloadProgress()
            try {
                ContentDownloader.downloadPubfileAsync(appId!!, pubFile!!)
            } catch (ex: Exception) {
                when (ex) {
                    is ContentDownloaderException,
                    is CancellationException,
                    -> {
                        logE(ex.message)
                        return@runBlocking
                    }

                    else -> {
                        logI("(Pub) Download failed to due to an unhandled exception: ${ex.message}")
                        throw ex
                    }
                }
            } finally {
                progressJob.cancel()
                ContentDownloader.shutdownSteam3()
            }
        } else {
            logI("Error: PubFile initializeSteam failed")
            return@runBlocking
        }

        // endregion
    } else if (ugcId != ContentDownloader.INVALID_MANIFEST_ID) {
        // region UGC Downloading

        if (initializeSteam(username, password)) {
            val progressJob = monitorDownloadProgress()
            try {
                ContentDownloader.downloadUGCAsync(appId!!, ugcId!!)
            } catch (ex: Exception) {
                when (ex) {
                    is ContentDownloaderException,
                    is kotlinx.coroutines.CancellationException,
                    -> {
                        logE(ex.message)
                        return@runBlocking
                    }

                    else -> {
                        logI("(UGC) Download failed to due to an unhandled exception: ${ex.message}")
                        throw ex
                    }
                }
            } finally {
                progressJob.cancel()
                ContentDownloader.shutdownSteam3()
            }
        } else {
            logI("Error: UGC initializeSteam failed")
            return@runBlocking
        }

        // endregion
    } else {
        // region App downloading

        val branch = getParameter<String>(args, "-branch")
            ?: getParameter<String>(args, "-beta")
            ?: ContentDownloader.DEFAULT_BRANCH
        ContentDownloader.config.betaPassword = getParameter<String>(args, "-branchpassword")
            ?: getParameter<String>(args, "-betapassword").orEmpty()

        ContentDownloader.config.downloadAllPlatforms = hasParameter(args, "-all-platforms")
        val os = getParameter<String>(args, "-os")
        if (ContentDownloader.config.downloadAllPlatforms && !os.isNullOrEmpty()) {
            logI("Error: Cannot specify -os when -all-platforms is specified.")
            return@runBlocking
        }

        ContentDownloader.config.downloadAllArchs = hasParameter(args, "-all-archs")
        val arch = getParameter<String>(args, "-osarch")
        if (ContentDownloader.config.downloadAllArchs && !arch.isNullOrEmpty()) {
            logI("Error: Cannot specify -osarch when -all-archs is specified.")
            return@runBlocking
        }

        ContentDownloader.config.downloadAllLanguages = hasParameter(args, "-all-languages")
        val language = getParameter<String>(args, "-language")
        if (ContentDownloader.config.downloadAllLanguages && !language.isNullOrEmpty()) {
            logI("Error: Cannot specify -language when -all-languages is specified.")
            return@runBlocking
        }

        val lv = hasParameter(args, "-lowviolence")

        val depotManifestIds = mutableListOf<Pair<Int, Long>>()
        val isUGC = false

        val depotIdList = getParameterList<Int>(args, "-depot")
        val manifestIdList = getParameterList<Long>(args, "-manifest")
        if (manifestIdList.isNotEmpty()) {
            if (depotIdList.size != manifestIdList.size) {
                logI("Error: -manifest requires one id for every -depot specified")
                return@runBlocking
            }

            val zippedDepotManifest =
                depotIdList.zip(manifestIdList) { depotId, manifestId ->
                    depotId to manifestId
                }
            depotManifestIds.addAll(zippedDepotManifest)
        } else {
            depotManifestIds.addAll(
                depotIdList.map { depotId ->
                    depotId to ContentDownloader.INVALID_MANIFEST_ID
                },
            )
        }

        if (initializeSteam(username, password)) {
            val progressJob = monitorDownloadProgress()
            try {
                ContentDownloader.downloadAppAsync(appId!!, depotManifestIds, branch, os, arch, language, lv, isUGC)
            } catch (ex: Exception) {
                when (ex) {
                    is ContentDownloaderException,
                    is CancellationException,
                    -> {
                        logE(ex.message)
                        return@runBlocking
                    }

                    else -> {
                        logI("(App) Download failed to due to an unhandled exception: ${ex.message}")
                        throw ex
                    }
                }
            } finally {
                progressJob.cancel()
                ContentDownloader.shutdownSteam3()
            }
        } else {
            logI("Error: App initializeSteam failed")
            return@runBlocking
        }

        // endregion
    }

    return@runBlocking
}

private suspend fun initializeSteam(username: String?, password: String?): Boolean {
    var initPassword = password
    if (!ContentDownloader.config.useQrCode) {
        if (initPassword == null &&
            (
                !ContentDownloader.config.rememberPassword ||
                    !AccountSettingsStore.instance!!.loginTokens.containsKey(username)
                )
        ) {
            do {
                initPassword = readPassword("Enter account password for \"${username}\": ")
                logI()
            } while (initPassword.isNullOrEmpty())
        } else if (username == null) {
            logI("No username given. Using anonymous account with dedicated server subscription.")
        }
    }

    if (!initPassword.isNullOrEmpty()) {
        if (initPassword.length > MAX_PASSWORD_SIZE) {
            logE("Warning: Password is longer than $MAX_PASSWORD_SIZE characters, which is not supported by Steam.")
        }

        if (!initPassword.all { it.code < 128 }) {
            logI("Warning: Password contains non-ASCII characters, which is not supported by Steam.")
        }
    }

    return ContentDownloader.initializeSteam3(username, initPassword)
}

private fun indexOfParam(args: Array<String>, param: String): Int {
    for (x in args.indices) {
        if (args[x].equals(param, ignoreCase = true)) {
            return x
        }
    }

    return -1
}

private fun hasParameter(args: Array<String>, param: String): Boolean = indexOfParam(args, param) > -1

private inline fun <reified T> getParameter(args: Array<String>, param: String, defaultValue: T? = null): T? {
    val index = indexOfParam(args, param)

    if (index == -1 || index == (args.size - 1)) {
        return defaultValue
    }

    val strParam = args[index + 1]

    return try {
        when (T::class) {
            Int::class -> strParam.toInt() as T
            Long::class -> strParam.toLong() as T
            Double::class -> strParam.toDouble() as T
            Float::class -> strParam.toFloat() as T
            Boolean::class -> strParam.toBoolean() as T
            else -> strParam as T
        }
    } catch (e: Exception) {
        logE(e.message)
        defaultValue
    }
}

private inline fun <reified T> getParameterList(args: Array<String>, param: String): List<T> {
    val list = mutableListOf<T>()
    var index = indexOfParam(args, param)

    if (index == -1 || index == (args.size - 1)) {
        return list
    }

    index++

    while (index < args.size) {
        val strParam = args[index]

        if (strParam.startsWith("-")) break

        try {
            val value =
                when (T::class) {
                    Int::class -> strParam.toInt() as T
                    Long::class -> strParam.toLong() as T
                    Double::class -> strParam.toDouble() as T
                    Float::class -> strParam.toFloat() as T
                    Boolean::class -> strParam.toBoolean() as T
                    else -> strParam as T
                }
            list.add(value)
        } catch (e: Exception) {
            // Skip invalid conversions
            logE(e.message)
        }

        index++
    }

    return list
}

private fun printUsage() {
    // Do not use tabs to align parameters here because tab size may differ
    logI()
    logI("Usage: downloading one or all depots for an app:")
    logI("       depotdownloader -app <id> [-depot <id> [-manifest <id>]]")
    logI("                       [-username <username> [-password <password>]] [other options]")
    logI()
    logI("Usage: downloading a workshop item using pubfile id")
    logI("       depotdownloader -app <id> -pubfile <id> [-username <username> [-password <password>]]")
    logI("Usage: downloading a workshop item using ugc id")
    logI("       depotdownloader -app <id> -ugc <id> [-username <username> [-password <password>]]")
    logI()
    logI("Parameters:")
    logI("  -app <#>                 - the AppID to download.")
    logI("  -depot <#>               - the DepotID to download.")
    logI("  -manifest <id>           - manifest id of content to download (requires -depot, default: current for branch).")
    logI("  -branch <branchname>     - download from specified branch if available (default: ${ContentDownloader.DEFAULT_BRANCH}).")
    logI("  -branchpassword <pass>   - branch password if applicable.")
    logI("  -all-platforms           - downloads all platform-specific depots when -app is used.")
    logI("  -all-archs               - download all architecture-specific depots when -app is used.")
    logI(
        "  -os <os>                 - the operating system for which to download the game (windows, macos or linux, " +
            "default: OS the program is currently running on)",
    )
    logI("  -osarch <arch>           - the architecture for which to download the game (32 or 64, default: the host's architecture)")
    logI("  -all-languages           - download all language-specific depots when -app is used.")
    logI("  -language <lang>         - the language for which to download the game (default: english)")
    logI("  -lowviolence             - download low violence depots when -app is used.")
    logI()
    logI("  -ugc <#>                 - the UGC ID to download.")
    logI("  -pubfile <#>             - the PublishedFileId to download. (Will automatically resolve to UGC id)")
    logI()
    logI("  -username <user>         - the username of the account to login to for restricted content.")
    logI("  -password <pass>         - the password of the account to login to for restricted content.")
    logI("  -remember-password       - if set, remember the password for subsequent logins of this user.")
    logI("                             use -username <username> -remember-password as login credentials.")
    logI()
    logI("  -dir <installdir>        - the directory in which to place downloaded files.")
    logI("  -filelist <file.txt>     - the name of a local file that contains a list of files to download (from the manifest).")
    logI(
        "                             prefix file path with `regex:` if you want to match with regex. each file " +
            "path should be on their own line.",
    )
    logI()
    logI("  -validate                - include checksum verification of files already downloaded")
    logI("  -manifest-only           - downloads a human readable manifest for any depots that would be downloaded.")
    logI("  -cellid <#>              - the overridden CellID of the content server to download from.")
    logI("  -max-downloads <#>       - maximum number of chunks to download concurrently. (default: 8).")
    logI(
        "  -loginid <#>             - a unique 32-bit integer Steam LogonID in decimal, required if running " +
            "multiple instances of DepotDownloader concurrently.",
    )
    logI("  -use-lancache            - forces downloads over the local network via a Lancache instance.")
    logI()
    logI("  -debug                   - enable verbose debug logging.")
    logI("  -V or --version          - print version and runtime.")
}

private fun printVersion(printExtra: Boolean = false) {
    logI("DepotDownloader v${Versions.getVersion()}")

    if (!printExtra) {
        return
    }

    val javaVersion = System.getProperty("java.version")
    val javaVendor = System.getProperty("java.vendor")
    val osName = System.getProperty("os.name")
    val osVersion = System.getProperty("os.version")
    val osArch = System.getProperty("os.arch")
    logI("Runtime: $javaVendor Java $javaVersion on $osName $osVersion ($osArch)")
}
