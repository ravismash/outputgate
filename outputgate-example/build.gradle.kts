// R10: the invoice example runs fully offline with no API key (FakeLlmClient).
dependencies {
    implementation(project(":outputgate-core"))
    testImplementation(project(":outputgate-junit"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
