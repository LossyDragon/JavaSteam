package `in`.dragonbra.javasteam.steam.discovery

import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.protobufs.steam.discovery.BasicServerListProtos.BasicServer
import `in`.dragonbra.javasteam.protobufs.steam.discovery.BasicServerListProtos.BasicServerList
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Server provider that stores servers in a file using protobuf.
 *
 * @constructor Initialize a new instance of FileStorageServerListProvider
 * @param file the filename that will store the servers
 */
class FileServerListProvider(private val file: Path) : IServerListProvider {

    /**
     * Instantiates a [FileServerListProvider] object.
     * @param file the file that will store the servers
     */
    constructor(file: File) : this(file.toPath()) {
        try {
            file.absoluteFile.parentFile?.mkdirs()
            file.createNewFile()
        } catch (e: IOException) {
            logger.error(e)
        }
    }

    init {
        require(file.fileName.toString().isNotBlank()) { "FileName must not be blank" }
    }

    /**
     * Returns the last time the file was written on disk
     */
    override val lastServerListRefresh: Instant
        get() = Files.getLastModifiedTime(file).toInstant()

    /**
     * Read the stored list of servers from the file
     * @return List of servers if persisted, otherwise an empty list
     */
    override fun fetchServerList(): List<ServerRecord> = runCatching {
        Files.newInputStream(file).use { fis ->
            val serverList = BasicServerList.parseFrom(fis)
            List(serverList.serversCount) { i ->
                val server: BasicServer = serverList.getServers(i)
                ServerRecord.createServer(
                    server.getAddress(),
                    server.port,
                    ProtocolTypes.from(server.protocol)
                )
            }
        }
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            val message = when (error) {
                is FileNotFoundException -> "servers list file not found"
                is IOException -> "Failed to read server list file ${file.fileName}"
                else -> "Unknown error occurred"
            }
            logger.error(message, error)
            emptyList()
        }
    )

    /**
     * Writes the supplied list of servers to persistent storage
     * @param endpoints List of server endpoints
     */
    override fun updateServerList(endpoints: List<ServerRecord>) {
        val builder = BasicServerList.newBuilder().apply {
            endpoints.forEach { endpoint ->
                addServers(
                    BasicServer.newBuilder().apply {
                        address = endpoint.host
                        port = endpoint.port
                        protocol = ProtocolTypes.code(endpoint.protocolTypes)
                    }
                )
            }
        }

        try {
            Files.newOutputStream(file).use { fos ->
                builder.build().writeTo(fos)
            }
        } catch (e: IOException) {
            logger.error("Failed to write servers to file ${file.fileName}", e)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(FileServerListProvider::class.java)
    }
}
