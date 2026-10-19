package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TypeGateTest {

    record Line(String description, int quantity, BigDecimal amount) {}

    record Invoice(String vendor, BigDecimal subtotal, List<Line> lineItems) {}

    record Simple(String vendor, int quantity) {}

    private static Verdict run(String json) {
        return Contract.builder().gate(TypeGate.of(Simple.class)).build().evaluate(json);
    }

    private static Violation only(Verdict v) {
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        assertEquals("type", v.violations().get(0).gate());
        return v.violations().get(0);
    }

    @Test
    @DisplayName("R4: an output that binds to the type passes")
    void bindsPasses() {
        assertTrue(run("{\"vendor\": \"Acme\", \"quantity\": 2}").passed());
    }

    @Test
    @DisplayName("R4: an unknown property fails with the Jackson message")
    void unknownProperty() {
        Violation v = only(run("{\"vendor\": \"Acme\", \"quantity\": 2, \"notes\": \"x\"}"));
        assertEquals("notes", v.path());
        assertTrue(v.message().contains("Unrecognized field \"notes\""), v.message());
    }

    @Test
    @DisplayName("R4: a null primitive fails with the Jackson message")
    void nullPrimitive() {
        Violation v = only(run("{\"vendor\": \"Acme\", \"quantity\": null}"));
        assertEquals("quantity", v.path());
        assertTrue(v.message().contains("null"), v.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "2.0"})
    @DisplayName("R4: a float for an int fails with the Jackson message")
    void floatForInt(String quantity) {
        Violation v = only(run("{\"vendor\": \"Acme\", \"quantity\": " + quantity + "}"));
        assertEquals("quantity", v.path());
        assertTrue(v.message().contains("Floating-point"), v.message());
    }

    @Test
    @DisplayName("R4: a number written as text is not coerced")
    void stringForIntFails() {
        assertEquals("quantity", only(run("{\"vendor\": \"Acme\", \"quantity\": \"2\"}")).path());
    }

    @Test
    @DisplayName("R4: a missing primitive fails instead of defaulting to 0")
    void missingPrimitiveFails() {
        Violation v = only(run("{\"vendor\": \"Acme\"}"));
        assertEquals("quantity", v.path());
        assertTrue(v.message().contains("quantity"), v.message());
    }

    @Test
    @DisplayName("R4/R9: evidence objects bind like bare values")
    void evidenceBinds() {
        String json = "{\"vendor\": {\"value\": \"Acme\", \"source_span\": {\"page\": 1, \"text\": \"ACME\"}},"
                + " \"quantity\": {\"value\": 2, \"source_span\": {\"page\": 1, \"text\": \"x 2\"}}}";
        assertTrue(run(json).passed(), () -> run(json).violations().toString());
    }

    @Test
    @DisplayName("R4: the path points into nested lists")
    void nestedPath() {
        Verdict v = Contract.builder().gate(TypeGate.of(Invoice.class)).build().evaluate(
                "{\"vendor\": \"Acme\", \"subtotal\": 40.00, \"lineItems\": ["
                        + "{\"description\": \"a\", \"quantity\": 1, \"amount\": 10},"
                        + "{\"description\": \"b\", \"quantity\": 1.5, \"amount\": 30}]}");
        assertEquals("lineItems[1].quantity", only(v).path());
    }
}
