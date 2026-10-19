// NFR-2 (FRD p.3): no runtime dependency beyond Jackson and the schema validator.
dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    // networknt 1.5.x is the line built on Jackson 2.18; 2.x moves to Jackson 2.22, 3.x to Jackson 3.
    // Schemas are read as JSON only, so the YAML module it pulls in is left out.
    implementation("com.networknt:json-schema-validator:1.5.9") {
        exclude(group = "com.fasterxml.jackson.dataformat", module = "jackson-dataformat-yaml")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
