plugins {
    // Lets the Java 21 toolchain below be downloaded automatically when it isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "outputgate"

include("outputgate-core", "outputgate-junit", "outputgate-example", "outputgate-spring-ai", "outputgate-langchain4j", "outputgate-ollama")
