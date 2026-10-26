package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MathGateTest {

    /** R9: every math test runs on bare scalars and on evidence objects. */
    enum Shape {
        BARE {
            @Override
            String num(String n) {
                return n;
            }
        },
        EVIDENCE {
            @Override
            String num(String n) {
                return "{\"value\": " + n + ", \"source_span\": {\"page\": 1, \"text\": \"" + n + "\"}}";
            }
        };

        abstract String num(String n);

        String invoice(String subtotal, String... amounts) {
            var items = new StringBuilder();
            for (String a : amounts) {
                if (!items.isEmpty()) {
                    items.append(", ");
                }
                items.append("{\"description\": \"item\", \"amount\": ").append(num(a)).append('}');
            }
            return "{\"line_items\": [" + items + "], \"subtotal\": " + num(subtotal) + "}";
        }
    }

    private static Contract sumContract() {
        return Contract.builder()
                .gate(MathGate.builder()
                        .sumEquals("line_items[*].amount", "subtotal")
                        .tolerance(0.01)
                        .build())
                .build();
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R5/R9: line items that add up to the subtotal pass")
    void consistentInvoicePasses(Shape shape) {
        Verdict v = sumContract().evaluate(shape.invoice("100.00", "40.00", "25.50", "34.50"));
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R5/R9: dropped line item with unchanged subtotal is caught by sumEquals alone")
    void droppedLineItemIsCaught(Shape shape) {
        Verdict v = sumContract().evaluate(shape.invoice("100.00", "40.00", "34.50"));

        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size());
        Violation violation = v.violations().get(0);
        assertEquals("math", violation.gate());
        assertEquals("subtotal", violation.path());
        assertTrue(violation.message().contains("74.50"), violation.message());
        assertTrue(violation.message().contains("100.00"), violation.message());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R5/R9: rounding inside tolerance passes")
    void roundingInsideTolerancePasses(Shape shape) {
        Verdict v = sumContract().evaluate(shape.invoice("100.00", "33.33", "33.33", "33.33"));
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("R5/R9: a difference just over tolerance is rejected")
    void justOverToleranceIsRejected(Shape shape) {
        Verdict v = sumContract().evaluate(shape.invoice("100.00", "33.33", "33.33", "33.32"));
        assertEquals(Status.REJECTED, v.status());
    }

    @Test
    @DisplayName("R5: a missing total is a violation, not a pass (fail-closed)")
    void missingTotalFails() {
        Verdict v = sumContract().evaluate("{\"line_items\": [{\"amount\": 1}]}");
        assertEquals(Status.REJECTED, v.status());
        assertEquals("subtotal", v.violations().get(0).path());
    }

    @Test
    @DisplayName("R5: a non-numeric amount is a violation, not a pass (fail-closed)")
    void nonNumericAmountFails() {
        Verdict v = sumContract().evaluate(
                "{\"line_items\": [{\"amount\": \"forty\"}], \"subtotal\": 40}");
        assertEquals(Status.REJECTED, v.status());
        assertEquals("line_items[0].amount", v.violations().get(0).path());
    }

    @Test
    @DisplayName("R5: a math gate with no rule is a build error, since it would pass everything")
    void gateWithoutRulesIsRejectedAtBuild() {
        assertThrows(IllegalStateException.class, () -> MathGate.builder().build());
    }

    @Test
    @DisplayName("R5: negative tolerance and bad paths are rejected at build")
    void badConfigIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MathGate.builder().tolerance(-0.01));
        assertThrows(IllegalArgumentException.class, () -> MathGate.builder().sumEquals("$.items", "total"));
    }

    @ParameterizedTest
    @EnumSource(Shape.class)
    @DisplayName("sumEqualsRounded allows half a unit of the last digit per figure, and no more")
    void roundedSum(Shape shape) {
        Contract rounded = Contract.builder().gate(MathGate.builder().sumEqualsRounded("line_items[*].amount", "subtotal").build()).build();
        // A real filing: five lines in millions add up to 15,071 against a printed total of 15,069.
        assertTrue(rounded.evaluate(shape.invoice("15069", "3368", "3268", "1694", "2918", "3823")).passed());
        Verdict off = rounded.evaluate(shape.invoice("15065", "3368", "3268", "1694", "2918", "3823"));
        assertEquals("sum of line_items[*].amount is 15071 but subtotal is 15065 (allowed for rounding 3.0)",
                off.violations().get(0).message());
        // With one decimal place the allowance is a tenth as large.
        assertTrue(rounded.evaluate(shape.invoice("10.1", "3.3", "3.3", "3.3")).passed());
        assertEquals(Status.REJECTED, rounded.evaluate(shape.invoice("10.3", "3.3", "3.3", "3.3")).status());
    }
}
