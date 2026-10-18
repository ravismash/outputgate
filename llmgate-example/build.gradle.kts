// R10: the invoice example must run fully offline with no API key (FakeLlmClient).
dependencies {
    testImplementation(project(":llmgate-junit"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
