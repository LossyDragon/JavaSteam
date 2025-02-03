plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf.gradle)
    id("maven-publish")
    id("signing")
    webuirpcinterfacegen
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    withSourcesJar()
    withJavadocJar()
}

/* Protobufs */
protobuf.protoc {
    artifact = libs.protobuf.protoc.get().toString()
}

/* Java Docs */
tasks.javadoc {
    exclude("**/in/dragonbra/javasteam/protobufs/**")
}

/* Source Sets */
sourceSets.main {
    java.srcDirs(
        // builtBy() fixes gradle warning "Execution optimizations have been disabled for task"
        files("build/generated/source/javasteam/main/java").builtBy("generateWebUiRpcMethods")
    )
}

tasks["compileJava"].dependsOn("generateWebUiRpcMethods")

dependencies {
    implementation(rootProject)
    implementation(libs.protobuf.java)
}

// TODO promote to actual lib?
