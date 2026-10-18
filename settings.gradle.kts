plugins {
    // Lets the Java 21 toolchain below be downloaded automatically when it isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "llmgate"

include("llmgate-core", "llmgate-junit", "llmgate-example")
