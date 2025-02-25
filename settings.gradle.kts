plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "javasteam"
include(":javasteam-samples")
include(":javasteam-tf")
include(":javasteam-cs")
include("javasteam-contentdownloader")
