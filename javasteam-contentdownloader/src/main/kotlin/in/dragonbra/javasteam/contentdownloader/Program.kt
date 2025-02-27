package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.contentdownloader.ContentDownloader.DepotManifestIds
import `in`.dragonbra.javasteam.steam.cdn.ClientLancache
import `in`.dragonbra.javasteam.util.log.LogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

private const val VERSION = "1.0.0"

// TODOs
// Waiting for callbacks...
// Got depot key for 736261 result: OK
// Download failed to due to an unhandled exception: null
// Waiting for callbacks...

// Failed to load account settings: Unexpected JSON token at offset 0: Expected start of the object '{', but had 'x' instead at path: $
// JSON input: x��e�x�e�Oo�0ſ��
// L�mzK�fk.....
// Connecting to Steam3...

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printVersion()
        printUsage()
        return
    }

    runBlocking {
        AccountSettingsStore.loadFromFile("account.config")

        //region Common Options

        // Not using hasParameter because it is case-insensitive
        if (args.size == 1 && (args[0] == "-V" || args[0] == "--version")) {
            printVersion(true)
            return@runBlocking
        }

        if (hasParameter(args, "-debug")) {
            printVersion(true)

            val logger = object : LogListener {
                override fun onLog(clazz: Class<*>?, message: String?, throwable: Throwable?) {
                    println(clazz?.simpleName + ": " + message)
                    throwable?.printStackTrace()
                }

                override fun onError(clazz: Class<*>?, message: String?, throwable: Throwable?) {
                    System.err.println(clazz?.simpleName + ": " + message)
                    throwable?.printStackTrace()
                }
            }
            LogManager.addListener(logger)
        }

        val username = getParameter<String>(args, "-username") ?: getParameter<String>(args, "-user")
        val password = getParameter<String>(args, "-password") ?: getParameter<String>(args, "-pass")
        ContentDownloader.config.rememberPassword = hasParameter(args, "-remember-password")
        ContentDownloader.config.useQrCode = hasParameter(args, "-qr")

        if (username == null) {
            if (ContentDownloader.config.rememberPassword) {
                println("Error: -remember-password can not be used without -username.")
                return@runBlocking
            }

            if (ContentDownloader.config.useQrCode) {
                println("Error: -qr can not be used without -username.")
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
                ContentDownloader.config.filesToDownload = hashSetOf()
                ContentDownloader.config.filesToDownloadRegex = mutableListOf()

                val files = File(fileList).readLines()

                files.forEach { fileEntry ->
                    if (fileEntry.isBlank()) {
                        return@forEach
                    }

                    if (fileEntry.startsWith(regexPrefix)) {
                        val rgx = Regex(fileEntry.substring(regexPrefix.length), setOf(RegexOption.IGNORE_CASE))
                        ContentDownloader.config.filesToDownloadRegex.add(rgx)
                    } else {
                        ContentDownloader.config.filesToDownload.add(fileEntry.replace('\\', '/'))
                    }
                }

                println("Using filelist: '$fileList'.")
            } catch (e: Exception) {
                println("Warning: Unable to load filelist: ${e.message}")
            }
        }

        ContentDownloader.config.installDirectory =
            getParameter<String>(args, "-dir")
        ContentDownloader.config.verifyAll =
            hasParameter(args, "-verify-all") ||
            hasParameter(args, "-verify_all") ||
            hasParameter(args, "-validate")
        ContentDownloader.config.maxServers =
            getParameter(args, "-max-servers", 20)!!

        if (hasParameter(args, "-use-lancache")) {
            ClientLancache.detectLancacheServer().await()
            if (ClientLancache.useLanCacheServer) {
                println("Detected Lancache server! Downloads will be directed through the Lancache.")

                // Increasing the number of concurrent downloads when the cache is detected since the downloads will likely
                // be served much faster than over the internet.  Steam internally has this behavior as well.
                if (!hasParameter(args, "-max-downloads")) {
                    ContentDownloader.config.maxDownloads = 25
                }
            }
        }

        ContentDownloader.config.maxDownloads =
            getParameter(args, "-max-downloads", 8)!!
        ContentDownloader.config.maxServers =
            maxOf(ContentDownloader.config.maxServers, ContentDownloader.config.maxDownloads)
        ContentDownloader.config.loginID =
            if (hasParameter(args, "-loginid")) getParameter<Int>(args, "-loginid") else null

        //endregion

        val appId = getParameter(args, "-app", ContentDownloader.INVALID_APP_ID)
        if (appId == ContentDownloader.INVALID_APP_ID) {
            println("Error: -app not specified!")
            return@runBlocking
        }

        val pubFile = getParameter(args, "-pubfile", ContentDownloader.INVALID_MANIFEST_ID)
        val ugcId = getParameter(args, "-ugc", ContentDownloader.INVALID_MANIFEST_ID)
        if (pubFile != ContentDownloader.INVALID_MANIFEST_ID) {
            //region Pubfile Downloading

            if (initializeSteam(username, password)) {
                try {
                    ContentDownloader.downloadPubfile(appId!!, pubFile!!)
                } catch (e: Exception) {
                    when {
                        e is ContentDownloaderException || e is CancellationException -> {
                            println(e.message)
                            return@runBlocking
                        }

                        else -> {
                            println("Download failed to due to an unhandled exception: ${e.message}")
                            throw e
                        }
                    }
                } finally {
                    ContentDownloader.shutdownSteam3()
                }
            } else {
                println("Error: InitializeSteam failed")
                return@runBlocking
            }

            //endregion
        } else if (ugcId != ContentDownloader.INVALID_MANIFEST_ID) {
            //region UGC Downloading

            if (initializeSteam(username, password)) {
                try {
                    ContentDownloader.downloadUGC(appId!!, ugcId!!)
                } catch (e: Exception) {
                    when {
                        e is ContentDownloaderException || e is CancellationException -> {
                            println(e.message)
                            return@runBlocking
                        }

                        else -> {
                            println("Download failed to due to an unhandled exception: ${e.message}")
                            throw e
                        }
                    }
                } finally {
                    ContentDownloader.shutdownSteam3()
                }
            } else {
                println("Error: InitializeSteam failed")
                return@runBlocking
            }

            //endregion
        } else {
            //region App downloading

            val branch = getParameter<String>(args, "-branch") ?: getParameter<String>(args, "-beta")
                ?: ContentDownloader.DEFAULT_BRANCH

            ContentDownloader.config.betaPassword =
                getParameter<String>(args, "-branchpassword") ?: getParameter<String>(args, "-betapassword")

            ContentDownloader.config.downloadAllPlatforms = hasParameter(args, "-all-platforms")

            val os = getParameter<String>(args, "-os")

            if (ContentDownloader.config.downloadAllPlatforms && !os.isNullOrEmpty()) {
                println("Error: Cannot specify -os when -all-platforms is specified.")
                return@runBlocking
            }

            ContentDownloader.config.downloadAllArchs = hasParameter(args, "-all-archs")

            val arch = getParameter<String>(args, "-osarch")

            if (ContentDownloader.config.downloadAllArchs && !arch.isNullOrEmpty()) {
                println("Error: Cannot specify -osarch when -all-archs is specified.")
                return@runBlocking
            }

            ContentDownloader.config.downloadAllLanguages = hasParameter(args, "-all-languages")
            val language = getParameter<String>(args, "-language")

            if (ContentDownloader.config.downloadAllLanguages && !language.isNullOrEmpty()) {
                println("Error: Cannot specify -language when -all-languages is specified.")
                return@runBlocking
            }

            val lv = hasParameter(args, "-lowviolence")

            val depotManifestIds = mutableListOf<DepotManifestIds>()
            val isUGC = false

            val depotIdList = getParameterList<Int>(args, "-depot")
            val manifestIdList = getParameterList<Long>(args, "-manifest")
            if (manifestIdList.isNotEmpty()) {
                if (depotIdList.size != manifestIdList.size) {
                    println("Error: -manifest requires one id for every -depot specified")
                    return@runBlocking
                }

                depotIdList.forEachIndexed { index, depotId ->
                    depotManifestIds.add(DepotManifestIds(depotId, manifestIdList[index]))
                }
            } else {
                depotIdList.forEach { depotId ->
                    depotManifestIds.add(DepotManifestIds(depotId, ContentDownloader.INVALID_MANIFEST_ID))
                }
            }

            if (initializeSteam(username, password)) {
                try {
                    ContentDownloader.downloadApp(appId!!, depotManifestIds, branch, os, arch, language, lv, isUGC)
                } catch (e: Exception) {
                    when {
                        e is ContentDownloaderException || e is CancellationException -> {
                            println(e.message)
                            return@runBlocking
                        }

                        else -> {
                            println("Download failed to due to an unhandled exception: ${e.message}")
                            println(e)
                        }
                    }
                    throw e
                } finally {
                    ContentDownloader.shutdownSteam3()
                }
            } else {
                println("Error: InitializeSteam failed")
                return@runBlocking
            }

            //endregion
        }

        return@runBlocking
        //endregion
    }
}

fun initializeSteam(username: String?, password: String?): Boolean {
    var finalPassword = password

    if (!ContentDownloader.config.useQrCode) {
        if (username != null &&
            finalPassword == null &&
            (
                !ContentDownloader.config.rememberPassword ||
                    !AccountSettingsStore.instance!!.loginTokens.containsKey(username)
                )
        ) {
            do {
                print("Enter account password for \"$username\": ")
                finalPassword = if (System.console() == null) {
                    readlnOrNull()
                } else {
                    // Avoid console echoing of password
                    Util.readPassword()
                }
                println()
            } while (finalPassword.isNullOrEmpty())
        } else if (username == null) {
            println("No username given. Using anonymous account with dedicated server subscription.")
        }
    }

    return ContentDownloader.initializeSteam3(username, finalPassword)
}

fun indexOfParam(args: Array<String>, param: String): Int {
    for (x in args.indices) {
        if (args[x].equals(param, ignoreCase = true)) {
            return x
        }
    }
    return -1
}

fun hasParameter(args: Array<String>, param: String): Boolean = indexOfParam(args, param) > -1

private inline fun <reified T> getParameter(args: Array<String>, param: String, defaultValue: T? = null): T? {
    val index = indexOfParam(args, param)
    if (index == -1 || index == args.size - 1) {
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
    } catch (_: Exception) {
        defaultValue
    }
}

private inline fun <reified T> getParameterList(args: Array<String>, param: String): List<T> {
    val list = mutableListOf<T>()
    var index = indexOfParam(args, param)
    if (index == -1 || index == args.size - 1) {
        return list
    }

    index++
    while (index < args.size) {
        val strParam = args[index]
        if (strParam.startsWith("-")) break

        try {
            val value = when (T::class) {
                Int::class -> strParam.toInt() as T
                Long::class -> strParam.toLong() as T
                Double::class -> strParam.toDouble() as T
                Float::class -> strParam.toFloat() as T
                Boolean::class -> strParam.toBoolean() as T
                else -> strParam as T
            }
            list.add(value)
        } catch (_: Exception) {
            // Skip values that can't be converted
        }

        index++
    }

    return list
}

private fun printUsage() {
    // Do not use tabs to align parameters here because tab size may differ
    println()
    println("Usage: downloading one or all depots for an app:")
    println("       depotdownloader -app <id> [-depot <id> [-manifest <id>]]")
    println("                       [-username <username> [-password <password>]] [other options]")
    println()
    println("Usage: downloading a workshop item using pubfile id")
    println("       depotdownloader -app <id> -pubfile <id> [-username <username> [-password <password>]]")
    println("Usage: downloading a workshop item using ugc id")
    println("       depotdownloader -app <id> -ugc <id> [-username <username> [-password <password>]]")
    println()
    println("Parameters:")
    println("  -app <#>                 - the AppID to download.")
    println("  -depot <#>               - the DepotID to download.")
    println("  -manifest <id>           - manifest id of content to download (requires -depot, default: current for branch).")
    println("  -branch <branchname>    - download from specified branch if available (default: ${ContentDownloader.DEFAULT_BRANCH}).")
    println("  -branchpassword <pass>   - branch password if applicable.")
    println("  -all-platforms           - downloads all platform-specific depots when -app is used.")
    println("  -all-archs               - download all architecture-specific depots when -app is used.")
    println("  -os <os>                 - the operating system for which to download the game (windows, macos or linux, default: OS the program is currently running on)")
    println("  -osarch <arch>           - the architecture for which to download the game (32 or 64, default: the host's architecture)")
    println("  -all-languages           - download all language-specific depots when -app is used.")
    println("  -language <lang>         - the language for which to download the game (default: english)")
    println("  -lowviolence             - download low violence depots when -app is used.")
    println()
    println("  -ugc <#>                 - the UGC ID to download.")
    println("  -pubfile <#>             - the PublishedFileId to download. (Will automatically resolve to UGC id)")
    println()
    println("  -username <user>         - the username of the account to login to for restricted content.")
    println("  -password <pass>         - the password of the account to login to for restricted content.")
    println("  -remember-password       - if set, remember the password for subsequent logins of this user.")
    println("                             use -username <username> -remember-password as login credentials.")
    println()
    println("  -dir <installdir>        - the directory in which to place downloaded files.")
    println("  -filelist <file.txt>     - the name of a local file that contains a list of files to download (from the manifest).")
    println("                             prefix file path with `regex:` if you want to match with regex. each file path should be on their own line.")
    println()
    println("  -validate                - include checksum verification of files already downloaded")
    println("  -manifest-only           - downloads a human readable manifest for any depots that would be downloaded.")
    println("  -cellid <#>              - the overridden CellID of the content server to download from.")
    println("  -max-servers <#>         - maximum number of content servers to use. (default: 20).")
    println("  -max-downloads <#>       - maximum number of chunks to download concurrently. (default: 8).")
    println("  -loginid <#>             - a unique 32-bit integer Steam LogonID in decimal, required if running multiple instances of DepotDownloader concurrently.")
    println("  -use-lancache            - forces downloads over the local network via a Lancache instance.")
}

private fun printVersion(printExtra: Boolean = false) {
    println("JavaSteam DepotDownloader $VERSION")

    if (!printExtra) {
        return
    }
}
