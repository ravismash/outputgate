// R10: the examples (invoice, balance sheet, lab report) run fully offline with no API key.
dependencies {
    implementation(project(":outputgate-core"))
    testImplementation(project(":outputgate-junit"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
