// R11 / NFR-3: the Spring AI adapter is its own module so core stays light.
// Spring AI 1.1.x (Spring 6, Jackson 2). Spring AI 2.x pulls networknt 3.x, which removed the
// classes SchemaGate uses; see the open question in the tracker before moving to 2.x.
val springAi = "1.1.8"

dependencies {
    api(project(":outputgate-core"))
    api("org.springframework.ai:spring-ai-client-chat:$springAi")

    testImplementation(project(":outputgate-junit"))
    testImplementation(project(":outputgate-example"))
    testImplementation("org.springframework.ai:spring-ai-ollama:$springAi")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
