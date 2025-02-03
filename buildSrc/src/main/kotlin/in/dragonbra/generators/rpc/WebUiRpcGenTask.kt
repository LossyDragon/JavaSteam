package `in`.dragonbra.generators.rpc

import `in`.dragonbra.generators.rpc.parser.ProtoParser
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

open class WebUiRpcGenTask : DefaultTask() {

    companion object {
        private const val KDOC_AUTHOR = "Lossy"
        private const val KDOC_DATE = "2025-02-03"

        private const val RPC_PACKAGE = "in.dragonbra.javasteam.rpc"
        private const val SERVICE_PACKAGE = "${RPC_PACKAGE}.service.webui"

        val kDocClass = """
            |@author $KDOC_AUTHOR
            |@since $KDOC_DATE
            """
            .trimMargin()
    }

    private val outputDir = File(
        project.layout.buildDirectory.get().asFile,
        "generated/source/javasteam/main/java/"
    )

    private val protoDirectory = project.file("src/main/proto")

    @TaskAction
    fun generate() {
        println("Generating Web UI RPC service methods as interfaces")

        outputDir.mkdirs()

        val protoParser = ProtoParser(outputDir, SERVICE_PACKAGE)

        protoDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "proto" }
            .forEach { file ->
                protoParser.parseFile(file, "in.dragonbra.javasteam.protobufs.webui.")
            }
    }
}
