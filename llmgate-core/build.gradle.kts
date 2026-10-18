// NFR-2 (FRD p.3): no runtime dependency beyond Jackson and the schema validator.
// The schema validator is not chosen yet (open question Q3); add it with R3.
dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
