// R15 artifact: <group>:outputgate-junit. Golden sets and GoldenRunner (R12–R14) land here.
dependencies {
    api(project(":outputgate-core"))
    api(platform("org.junit:junit-bom:5.11.4"))
    api("org.junit.jupiter:junit-jupiter-api")
    api("org.junit.jupiter:junit-jupiter-params")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
