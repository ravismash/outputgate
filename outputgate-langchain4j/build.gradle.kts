// R11 / NFR-3: the LangChain4j adapter is its own module so core stays light.
// LangChain4j 1.x renamed the FRD's ChatLanguageModel to ChatModel.
val langchain4j = "1.22.0"

dependencies {
    api(project(":outputgate-core"))
    api("dev.langchain4j:langchain4j-core:$langchain4j")

    testImplementation(project(":outputgate-junit"))
    testImplementation(project(":outputgate-example"))
    testImplementation("dev.langchain4j:langchain4j-ollama:$langchain4j")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
