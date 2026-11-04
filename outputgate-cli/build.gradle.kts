// Command-line runner: checks JSON files against a contract written in YAML or JSON, so CI jobs and
// non-JVM projects can use the gates without writing Java. Not published to Maven Central; the
// release workflow attaches its self-contained jar to each GitHub release, which the GitHub Action
// downloads.
plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
}

application {
    mainClass = "io.github.ravismash.outputgate.cli.Main"
    applicationName = "outputgate"
}

// The jar runs on Java 17 like the library modules.
tasks.named<JavaCompile>("compileJava") { options.release = 17 }

dependencies {
    implementation(project(":outputgate-core"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest { attributes("Implementation-Version" to project.version) }
}

// build/libs/outputgate-cli-<version>-all.jar: java -jar it with no classpath.
tasks.shadowJar {
    manifest { attributes("Implementation-Version" to project.version) }
    mergeServiceFiles()
}
