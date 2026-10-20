// Evaluation harness for real documents (FRD Days 11-15): reads PDFs into SourceDocuments and runs
// models over them. Not part of the library: it is never published, and core never depends on it.
plugins {
    application
}

// The benchmark over an EvalRun: ./gradlew :outputgate-eval:run --args="report /abs/docs /abs/run" (see BenchmarkCli).
application {
    mainClass = "io.github.ravismash.outputgate.eval.BenchmarkCli"
}

dependencies {
    implementation(project(":outputgate-core"))
    implementation(project(":outputgate-ollama"))
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(project(":outputgate-junit"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Day 12 dry run on local Ollama (needs Ollama running; not part of `build`):
//   ./gradlew :outputgate-eval:runFccOllama -Ppdfs=<folder> -Pout=<folder> [-Pmodel=qwen2.5:14b] [-Pctx=16384]
tasks.register<JavaExec>("runFccOllama") {
    group = "verification"
    description = "Runs the FCC invoice contract over a folder of PDFs with a local Ollama model."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.ravismash.outputgate.eval.FccOllamaRun")
    args = listOf(
        (project.findProperty("pdfs") ?: error("-Ppdfs=<folder> is required")).toString(),
        (project.findProperty("out") ?: error("-Pout=<folder> is required")).toString(),
        (project.findProperty("model") ?: "qwen2.5:14b").toString(),
        (project.findProperty("ctx") ?: "16384").toString(),
    )
}
