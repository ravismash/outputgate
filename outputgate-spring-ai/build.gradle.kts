// R11 / NFR-3: the Spring AI adapter is its own module so core stays light.
// Published against Spring AI 1.1.x (Spring 6, Jackson 2). Core shades its networknt validator, so
// Spring AI 2.x (which brings networknt 3.x) works too. CI's adapter job re-runs this module's tests
// against every version in the README's matrix: ./gradlew :outputgate-spring-ai:test -PspringAiVersion=2.0.2
val springAi = providers.gradleProperty("springAiVersion").getOrElse("1.1.8")

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
