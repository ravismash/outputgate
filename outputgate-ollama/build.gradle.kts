// R11 / NFR-3: the Ollama adapter is its own module. It talks to Ollama's HTTP API with the
// JDK's HttpClient and core's Jackson, so it adds no dependency of its own.
dependencies {
    api(project(":outputgate-core"))

    testImplementation(project(":outputgate-junit"))
    testImplementation(project(":outputgate-example"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Live tests call a real local Ollama; they run only when asked: OUTPUTGATE_LIVE_OLLAMA=1
tasks.withType<Test>().configureEach {
    environment("OUTPUTGATE_LIVE_OLLAMA", System.getenv("OUTPUTGATE_LIVE_OLLAMA") ?: "")
}
