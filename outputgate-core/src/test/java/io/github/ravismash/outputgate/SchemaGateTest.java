package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SchemaGateTest {

    private static final String RESOURCE = "schemas/invoice.schema.json";

    /** R9: every case runs on bare scalars and on evidence objects. */
    enum Shape {
        BARE,
        EVIDENCE;

        String v(String json) {
            return this == BARE
                    ? json
                    : "{\"value\": " + json + ", \"source_span\": {\"page\": 1, \"text\": \"x\"}}";
        }

        String invoice(String extraFields) {
            return "{\"vendor\": " + v("\"Acme\"") + ", \"subtotal\": " + v("100.00")
                    + ", \"line_items\": [{\"amount\": " + v("100.00") + "}]" + extraFields + "}";
        }
    }

    private static Verdict run(SchemaGate gate, String json) {
        return Contract.builder().gate(gate).build().evaluate(json);
    }

    private static SchemaGate gate() {
        return SchemaGate.fromClasspath(RESOURCE);
    }

    private static String schemaText() throws IOException {
        try (InputStream in = SchemaGateTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R3/R9: an output that matches the schema passes")
    void validPasses(Shape s) {
        Verdict v = run(gate(), s.invoice(""));
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R3: a missing required field produces a named violation")
    void missingRequiredIsNamed(Shape s) {
        String json = "{\"subtotal\": " + s.v("100.00") + ", \"line_items\": []}";
        Verdict v = run(gate(), json);
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        Violation violation = v.violations().get(0);
        assertEquals("schema", violation.gate());
        assertEquals("vendor", violation.path());
        assertTrue(violation.message().startsWith("required:"), violation.message());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R3: an additionalProperties breach produces a named violation")
    void additionalPropertyIsNamed(Shape s) {
        Verdict v = run(gate(), s.invoice(", \"notes\": " + s.v("\"thanks!\"")));
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        Violation violation = v.violations().get(0);
        assertEquals("notes", violation.path());
        assertTrue(violation.message().startsWith("additionalProperties:"), violation.message());
    }

    @Test
    @DisplayName("R3: both breaches in one output are both reported, ordered by path")
    void bothBreachesReported() {
        Verdict v = run(gate(), "{\"subtotal\": 1, \"line_items\": [], \"notes\": \"x\"}");
        assertEquals(List.of("notes", "vendor"), v.violations().stream().map(Violation::path).toList());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R3/R9: a wrong type deep in an array is reported at that element")
    void nestedTypeErrorHasElementPath(Shape s) {
        String json = "{\"vendor\": " + s.v("\"Acme\"") + ", \"subtotal\": " + s.v("3")
                + ", \"line_items\": [{\"amount\": " + s.v("1") + "}, {\"amount\": " + s.v("\"two\"") + "}]}";
        Verdict v = run(gate(), json);
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        assertEquals("line_items[1].amount", v.violations().get(0).path());
        assertTrue(v.violations().get(0).message().startsWith("type:"), v.violations().get(0).message());
    }

    @Test
    @DisplayName("R3: the same schema from a string and from the classpath gives the same verdicts")
    void stringAndClasspathAgree() throws IOException {
        SchemaGate fromString = SchemaGate.fromString(schemaText());
        for (String json : List.of(
                Shape.BARE.invoice(""),
                "{\"subtotal\": 1, \"line_items\": [], \"notes\": \"x\"}",
                Shape.EVIDENCE.invoice(", \"notes\": \"x\""))) {
            assertEquals(run(gate(), json), run(fromString, json));
        }
    }

    @Test
    @DisplayName("R3: a schema that declares another draft is rejected at build")
    void otherDraftRejected() {
        String draft7 = "{\"$schema\": \"http://json-schema.org/draft-07/schema#\", \"type\": \"object\"}";
        assertThrows(IllegalArgumentException.class, () -> SchemaGate.fromString(draft7));
    }

    @Test
    @DisplayName("R3: a schema with no $schema is read as 2020-12")
    void noDeclaredDraftIsRead() {
        SchemaGate g = SchemaGate.fromString("{\"type\": \"object\", \"required\": [\"a\"]}");
        assertEquals("a", run(g, "{}").violations().get(0).path());
    }

    @Test
    @DisplayName("R3: a schema that is not JSON, or a missing resource, is rejected at build")
    void badSchemaSourcesRejected() {
        assertThrows(IllegalArgumentException.class, () -> SchemaGate.fromString("{not json"));
        assertThrows(IllegalArgumentException.class, () -> SchemaGate.fromClasspath("schemas/nope.json"));
    }
}
