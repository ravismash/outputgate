// Evaluation harness for real documents (FRD Days 11-15): reads PDFs into SourceDocuments and runs
// models over them. Not part of the library: it is never published, and core never depends on it.
dependencies {
    implementation(project(":outputgate-core"))
    implementation("org.apache.pdfbox:pdfbox:3.0.8")

    testImplementation(project(":outputgate-junit"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
