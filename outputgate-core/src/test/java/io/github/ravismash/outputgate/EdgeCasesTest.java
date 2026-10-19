package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Edge cases found by mutation testing (PIT, 2026-10-19): behaviour the earlier tests ran
 * but did not pin down.
 */
class EdgeCasesTest {

    private static Verdict run(Gate gate, String json) {
        return Contract.builder().gate(gate).build().evaluate(json);
    }

    private static List<String> paths(Verdict v) {
        return v.violations().stream().map(Violation::path).toList();
    }

    @Nested
    class ContractEdges {

        @Test
        @DisplayName("R1: a gate that returns null is a failure, never a pass")
        void nullViolationsFail() {
            Gate returnsNull = new Gate() {
                @Override
                public String name() {
                    return "lazy";
                }

                @Override
                public List<Violation> check(JsonNode output, EvalContext context) {
                    return null;
                }
            };
            Verdict v = run(returnsNull, "{}");
            assertEquals(Status.REJECTED, v.status());
            assertEquals("lazy", v.results().get(0).gate());
            assertTrue(v.violations().get(0).message().contains("null"), v::toString);
        }

        @Test
        @DisplayName("R2: null output is rejected as json-parse")
        void nullOutput() {
            Verdict v = Contract.builder().build().evaluate((String) null);
            assertEquals(List.of("json-parse"), v.results().stream().map(GateResult::gate).toList());
        }

        @Test
        @DisplayName("a null gate is rejected when building the contract")
        void nullGate() {
            assertThrows(IllegalArgumentException.class, () -> Contract.builder().gate(null));
        }

        @Test
        @DisplayName("every gate reports its own name in the verdict")
        void gateNames() {
            Contract c = Contract.builder()
                    .gate(SchemaGate.fromString("{\"type\": \"object\"}"))
                    .gate(TypeGate.of(Object.class))
                    .gate(MathGate.builder().range("n", 0, 1).build())
                    .gate(ProvenanceGate.builder().require("n").build())
                    .gate(BudgetGate.builder().maxCost(1).build())
                    .gate(RegexGate.builder().forbid("n", "x").build())
                    .build();
            assertEquals(List.of("schema", "type", "math", "provenance", "budget", "regex"),
                    c.evaluate("{\"n\": 1}").results().stream().map(GateResult::gate).toList());
        }
    }

    @Nested
    class ZeroIsAValidLimit {

        @Test
        @DisplayName("R7: zero ceilings are allowed, and zero usage meets them")
        void zeroCeilings() {
            BudgetGate g = assertDoesNotThrow(() -> BudgetGate.builder().maxCost(BigDecimal.ZERO)
                    .maxOutputTokens(0).maxLatency(Duration.ZERO).build());
            Verdict v = Contract.builder().gate(g).build()
                    .evaluate(new LlmOutput("{}", Usage.of(BigDecimal.ZERO, Duration.ZERO, 0)));
            assertTrue(v.passed(), v::toString);
        }

        @Test
        @DisplayName("R11: zero prices and zero usage are valid values")
        void zeroPricesAndUsage() {
            assertDoesNotThrow(() -> TokenPricing.perMillionTokens(BigDecimal.ZERO, BigDecimal.ZERO));
            assertDoesNotThrow(() -> new Usage(BigDecimal.ZERO, Duration.ZERO, 0L));
            assertThrows(IllegalArgumentException.class, () -> new Usage(null, Duration.ofMillis(-1), null));
            assertThrows(IllegalArgumentException.class, () -> new Usage(null, null, -1L));
            assertThrows(IllegalArgumentException.class, () -> TokenPricing.perMillionTokens(BigDecimal.ONE, new BigDecimal("-0.01")));
        }

        @Test
        @DisplayName("R5: zero tolerance and a range with min == max are allowed")
        void zeroToleranceAndPointRange() {
            MathGate g = assertDoesNotThrow(() -> MathGate.builder().range("n", 5, 5).tolerance(0).build());
            assertTrue(run(g, "{\"n\": 5}").passed());
            assertEquals(Status.REJECTED, run(g, "{\"n\": 5.0001}").status());
        }
    }

    @Nested
    class MathEdges {

        @Test
        @DisplayName("R5: a null number is a violation")
        void nullNumber() {
            Verdict v = run(MathGate.builder().range("n", 0, 10).build(), "{\"n\": null}");
            assertEquals(List.of("n"), paths(v));
            assertTrue(v.violations().get(0).message().contains("no value"), v::toString);
        }

        @Test
        @DisplayName("R5: sumEquals needs exactly one total")
        void manyTotals() {
            Verdict v = run(MathGate.builder().sumEquals("items[*]", "totals[*]").build(),
                    "{\"items\": [1, 2], \"totals\": [3, 3]}");
            assertTrue(v.violations().get(0).message().contains("expected one total, found 2"), v::toString);
        }

        @Test
        @DisplayName("R5: messages show the arithmetic, × for products and + for sums")
        void messagesShowArithmetic() {
            Verdict p = run(MathGate.builder().productEquals("amount", "qty", "price").build(),
                    "{\"qty\": 3, \"price\": 4.00, \"amount\": 10.00}");
            assertTrue(p.violations().get(0).message().contains("3 × 4.00 is 12.00"), p::toString);
            Verdict s = run(MathGate.builder().equalsSum("total", "a", "b").build(), "{\"a\": 1, \"b\": 2, \"total\": 4}");
            assertTrue(s.violations().get(0).message().contains("1 + 2 is 3"), s::toString);
            Verdict len = run(MathGate.builder().equalsSum("t[*]", "a[*]").build(), "{\"t\": [1], \"a\": [1, 2]}");
            assertTrue(len.violations().get(0).message().contains("t[*]=1, a[*]=2"), len::toString);
        }

        @Test
        @DisplayName("R5: a custom rule that reads an array as one number fails at that path")
        void customGetOnArray() {
            Verdict v = run(MathGate.builder().custom("one total", n -> n.get("items[*]").signum() > 0).build(),
                    "{\"items\": [1, 2]}");
            assertTrue(v.violations().get(0).message().contains("expected one value, found 2"), v::toString);
        }

        @Test
        @DisplayName("an empty path is rejected at build")
        void emptyPath() {
            assertThrows(IllegalArgumentException.class, () -> MathGate.builder().range("", 0, 1));
            assertThrows(IllegalArgumentException.class, () -> MathGate.builder().range(null, 0, 1));
        }
    }

    @Nested
    class PathEdges {

        @Test
        @DisplayName("[*] on something that is not an array is 'no value', so require can't pass by finding nothing")
        void starOnNonArray() {
            Verdict v = run(RegexGate.builder().require("items[*].code", "A\\d").build(), "{\"items\": \"oops\"}");
            assertEquals(List.of("items"), paths(v));
            assertTrue(v.violations().get(0).message().contains("no value"), v::toString);
        }

        @Test
        @DisplayName("a missing parent is reported at the parent, the first missing step")
        void missingParent() {
            Verdict v = run(RegexGate.builder().require("vendor.name", ".+").build(), "{}");
            assertEquals(List.of("vendor"), paths(v));
        }

        @Test
        @DisplayName("R6: [*] elements that are themselves evidence objects are checked as evidence")
        void starElementsAsEvidence() {
            ProvenanceGate g = ProvenanceGate.builder().require("amounts[*]").build();
            String ev = "{\"value\": 40.00, \"source_span\": {\"page\": 2, \"text\": \"40.00\"}}";
            assertTrue(run(g, "{\"amounts\": [" + ev + ", " + ev + "]}").passed());
            assertEquals(List.of("amounts[1]"), paths(run(g, "{\"amounts\": [" + ev + ", 34.50]}")));
        }
    }

    @Nested
    class ProvenanceMessages {

        private Violation one(String subtotal) {
            Verdict v = run(ProvenanceGate.builder().require("subtotal").build(), "{\"subtotal\": " + subtotal + "}");
            assertEquals(1, v.violations().size(), v::toString);
            return v.violations().get(0);
        }

        @Test
        @DisplayName("R6: each kind of malformed evidence says exactly what is wrong")
        void specificMessages() {
            assertEquals("no value", one("null").message());
            assertTrue(one("74.50").message().startsWith("bare value"));
            assertEquals("evidence object has no value", one("{\"source_span\": {\"page\": 1, \"text\": \"x\"}}").message());
            assertEquals("source_span must be an object {page, text}", one("{\"value\": 1, \"source_span\": \"p2\"}").message());
            assertTrue(one("{\"value\": 1, \"source_span\": {\"page\": 0, \"text\": \"x\"}}").message().startsWith("source_span.page"));
            assertTrue(one("{\"value\": 1, \"source_span\": {\"page\": 1, \"text\": \"\"}}").message().startsWith("source_span.text"));
        }
    }

    @Nested
    class RegexEdges {

        @Test
        @DisplayName("R8: a null value is 'no value', not 'not text'")
        void nullIsNoValue() {
            Verdict v = run(RegexGate.builder().require("id", "x").build(), "{\"id\": null}");
            assertEquals("no value", v.violations().get(0).message());
        }

        @Test
        @DisplayName("R8: forbid checks numbers as text, and skips objects and arrays")
        void forbidOnNonText() {
            RegexGate g = RegexGate.builder().forbid("v", "13").build();
            assertEquals(List.of("v"), paths(run(g, "{\"v\": 1313}")));
            assertTrue(run(g, "{\"v\": {\"a\": \"13\"}}").passed());
        }
    }

    @Nested
    class BudgetEdges {

        @Test
        @DisplayName("R7: unknown latency with a latency ceiling is a violation")
        void unknownLatency() {
            Verdict v = Contract.builder().gate(BudgetGate.builder().maxLatency(Duration.ofSeconds(1)).build()).build()
                    .evaluate(new LlmOutput("{}", new Usage(BigDecimal.ONE, null, 10L)));
            assertTrue(v.violations().get(0).message().startsWith("latency unknown"), v::toString);
        }
    }

    @Nested
    class SchemaEdges {

        @Test
        @DisplayName("R3: a schema that is JSON but not an object is rejected at build")
        void nonObjectSchema() {
            assertThrows(IllegalArgumentException.class, () -> SchemaGate.fromString("[]"));
            assertThrows(IllegalArgumentException.class, () -> SchemaGate.fromString("true"));
        }

        @Test
        @DisplayName("R3: a schema whose keyword has the wrong type is rejected at build, not at the first test")
        void invalidKeywordRejected() {
            assertThrows(IllegalArgumentException.class, () -> SchemaGate.fromString("{\"type\": 5}"));
            assertThrows(IllegalArgumentException.class, () -> SchemaGate.fromString("{\"type\": \"strnig\"}"));
            // Fail-open before the fix: "required" as a string was ignored, so every output passed.
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> SchemaGate.fromString("{\"required\": \"vendor\"}"));
            assertTrue(e.getMessage().contains("required"), e.getMessage());
        }

        @Test
        @DisplayName("R3: a $schema of 2020-12 with a trailing # is accepted")
        void trailingHash() {
            assertDoesNotThrow(() -> SchemaGate.fromString("{\"$schema\": \"https://json-schema.org/draft/2020-12/schema#\"}"));
        }
    }

    @Nested
    class NullArguments {

        @Test
        @DisplayName("null arguments are rejected up front")
        void nulls() {
            assertThrows(IllegalArgumentException.class, () -> new SourceDocument(null));
            assertThrows(IllegalArgumentException.class, () -> new SourceDocument(Arrays.asList("a", null)));
            assertThrows(IllegalArgumentException.class, () -> SourceDocument.ofPages((String[]) null));
            assertThrows(IllegalArgumentException.class, () -> EvalContext.empty().withSource(null));
            assertThrows(IllegalArgumentException.class, () -> TypeGate.of(null));
        }

        @Test
        @DisplayName("SourceDocument.of(null) is one empty page; withUsage(null) and FakeLlmClient.withUsage(null) mean unknown")
        void nullMeansEmptyOrUnknown() {
            assertEquals(List.of(""), SourceDocument.of(null).pages());
            assertEquals(Usage.UNKNOWN, EvalContext.empty().withUsage(null).usage());
            assertEquals(Usage.UNKNOWN, FakeLlmClient.replying("{}").withUsage(null).complete("p").usage());
            assertTrue(EvalContext.empty().withSource(SourceDocument.of("x")).source().isPresent());
        }

        @Test
        @DisplayName("FakeLlmClient says which call ran out of replies")
        void fakeCallNumber() {
            FakeLlmClient f = FakeLlmClient.inOrder("a");
            f.complete("p");
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> f.complete("p"));
            assertTrue(e.getMessage().contains("call 2 has none"), e.getMessage());
        }
    }
}
