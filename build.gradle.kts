// NFR (FRD p.3): Java 21, MIT; adapters live in separate modules so core stays light.
subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
