package `in`.dragonbra.generators.rpc

import org.gradle.api.Plugin
import org.gradle.api.Project

class WebUiRpcGenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("generateWebUiRpcMethods", WebUiRpcGenTask::class.java)
    }
}
