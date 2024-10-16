package `in`.dragonbra.javasteam.steam.discovery

import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.protobufs.steam.discovery.BasicServerListProtos.BasicServer
import `in`.dragonbra.javasteam.protobufs.steam.discovery.BasicServerListProtos.BasicServerList
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant

/**
 * Server provider that stores servers in a file using protobuf.
 */
class FileServerListProvider(private val file: File) : IServerListProvider {

    /**
     * Instantiates a [FileServerListProvider] object.
     * @param file the file that will store the servers
     */
    init {
        try {
            file.absoluteFile.parentFile?.mkdirs()
            file.createNewFile()
        } catch (e: IOException) {
            logger.error(e)
        }
    }

    // TODO validate, above may cause conflict since we will make it during init if it doesnt exist.
    /**
     * Returns the last time the file was written on disk
     */
    override val lastServerListRefresh: Instant
        get() = Instant.ofEpochMilli(file.lastModified())

    /**
     * Read the stored list of servers from the file
     * @return List of servers if persisted, otherwise an empty list
     */
    override fun fetchServerList(): List<ServerRecord> = try {
        FileInputStream(file).use { fis ->
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
    } catch (e: FileNotFoundException) {
        logger.error("servers list file not found", e)
        emptyList()
    } catch (e: IOException) {
        logger.error("Failed to read server list file ${file.absolutePath}", e)
        emptyList()
    }

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
            FileOutputStream(file, false).use { fos ->
                builder.build().writeTo(fos)
                fos.flush()
            }
        } catch (e: IOException) {
            logger.error("Failed to write servers to file ${file.absolutePath}", e)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(FileServerListProvider::class.java)
    }
}
