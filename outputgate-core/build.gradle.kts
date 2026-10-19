// NFR-2 (FRD p.3): no runtime dependency beyond Jackson and the schema validator.
// The schema validator (networknt 1.5.9, with its `itu` dependency) is shaded and relocated into
// this jar. Spring AI 1.1.x pulls networknt 2.0.0 and 2.x pulls 3.0.1, and both removed the
// classes SchemaGate uses; relocation means a user's version can never replace ours.
plugins {
    id("com.gradleup.shadow") version "9.6.1"
}

val shaded: Configuration = configurations.create("shaded")
configurations.compileOnly { extendsFrom(shaded) }
configurations.testImplementation { extendsFrom(shaded) }

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("org.slf4j:slf4j-api:2.0.17")
    shaded("com.networknt:json-schema-validator:1.5.9") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.dataformat")
        exclude(group = "org.slf4j")
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val shadedPackage = "io.github.ravismash.outputgate.shaded"

tasks.shadowJar {
    archiveClassifier.set("")
    configurations.set(listOf(shaded))
    relocate("com.networknt", "$shadedPackage.networknt")
    relocate("com.ethlo", "$shadedPackage.ethlo")
    relocate("jsv-messages", "$shadedPackage.networknt.jsv-messages")
    exclude("META-INF/maven/**", "META-INF/native-image/**")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Other modules (and, later, Maven Central) get the shaded jar, not the plain one.
listOf(configurations.apiElements, configurations.runtimeElements).forEach { elements ->
    elements.configure {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
    }
}
