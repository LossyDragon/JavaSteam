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

/**
 * Server provider that stores servers in a file using protobuf.
 *
 * @constructor Initialize a new instance of [FileServerListProvider]
 * @param file the file that will store the servers
 */
class FileServerListProvider(private val file: File) : IServerListProvider {

    init {
        try {
            if (!file.exists()) {
                file.absoluteFile.parentFile.mkdirs()
                file.createNewFile()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun fetchServerList(): List<ServerRecord> {
        try {
            FileInputStream(file).use { fis ->
                val records: MutableList<ServerRecord> = ArrayList()
                val serverList = BasicServerList.parseFrom(fis)
                (0 until serverList.serversCount).map { i ->
                    val server = serverList.getServers(i)
                    ServerRecord.createServer(
                        server.address,
                        server.port,
                        ProtocolTypes.from(server.protocol)
                    ).also(records::add)
                }

                return records
            }
        } catch (e: FileNotFoundException) {
            logger.debug("servers list file not found")
        } catch (e: IOException) {
            logger.debug("Failed to read server list file " + file.absolutePath)
        }

        return listOf()
    }

    override fun updateServerList(endpoints: List<ServerRecord>) {
        val builder = BasicServerList.newBuilder()

        endpoints.map { endpoint ->
            builder.addServers(
                BasicServer.newBuilder()
                    .setAddress(endpoint.host)
                    .setPort(endpoint.port)
                    .setProtocol(ProtocolTypes.code(endpoint.protocolTypes))
            )
        }

        try {
            FileOutputStream(file, false).use { fos ->
                builder.build().writeTo(fos)
                fos.flush()
            }
        } catch (e: IOException) {
            logger.debug("Failed to write servers to file " + file.absolutePath, e)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(FileServerListProvider::class.java)
    }
}
