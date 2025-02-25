plugins {
    alias(libs.plugins.protobuf.gradle)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kotlinter)
    kotlin("plugin.serialization") version "2.1.10"
    id("maven-publish")
    id("signing")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "in.dragonbra.javasteam.contentdownloader.ProgramKt"
    }
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

dependencies {
    implementation(rootProject)

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.stdib)
    implementation(libs.protobuf.java)
    implementation(libs.qrCode)
    implementation(libs.commons.io)
    implementation(libs.commons.lang3)
    implementation(libs.commons.validator)
    implementation(libs.bundles.ktor)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
