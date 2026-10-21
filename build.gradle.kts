import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    // R15: Maven Central through the Central Portal. Applied below to the published modules only.
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

// The library artifacts. outputgate-example and outputgate-eval are never published.
val publishedModules = mapOf(
    "outputgate-core" to "Deterministic contract tests for LLM output on the JVM: the contract and its gates.",
    "outputgate-junit" to "JUnit 5 assertions, golden sets and HTML reports for outputgate contracts.",
    "outputgate-spring-ai" to "outputgate LlmClient over a Spring AI ChatClient.",
    "outputgate-langchain4j" to "outputgate LlmClient over a LangChain4j ChatModel.",
    "outputgate-ollama" to "outputgate LlmClient for Ollama's HTTP API, with no extra dependency.",
)

// NFR (FRD p.3): Java 21, MIT; adapters live in separate modules so core stays light.
subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
        // Published modules get their sources and javadoc jars from the publish plugin below.
        if (project.name !in publishedModules) {
            withSourcesJar()
            withJavadocJar()
        }
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    if (name in publishedModules) {
        apply(plugin = "com.vanniktech.maven.publish")

        // Credentials and the signing key come from Gradle properties, never from this repo:
        // mavenCentralUsername, mavenCentralPassword, signingInMemoryKey, signingInMemoryKeyPassword
        // (ORG_GRADLE_PROJECT_<name> in CI; see .github/workflows/release.yml).
        extensions.configure<MavenPublishBaseExtension> {
            configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
            publishToMavenCentral()
            if (!version.toString().endsWith("-SNAPSHOT")) {
                signAllPublications()
            }
            coordinates(group.toString(), name, version.toString())

            pom {
                name.set(project.name)
                description.set(publishedModules.getValue(project.name))
                url.set("https://github.com/ravismash/outputgate")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("ravismash")
                        name.set("Ravi Prakash Meena")
                        url.set("https://github.com/ravismash")
                    }
                }
                scm {
                    url.set("https://github.com/ravismash/outputgate")
                    connection.set("scm:git:https://github.com/ravismash/outputgate.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ravismash/outputgate.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/ravismash/outputgate/issues")
                }
            }
        }
    }
}
