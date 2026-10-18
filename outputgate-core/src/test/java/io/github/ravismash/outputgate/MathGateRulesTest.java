package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** R5 rules beyond sumEquals; each numeric case runs on both value shapes (R9). */
class MathGateRulesTest {

    enum Shape {
        BARE,
        EVIDENCE;

        String n(String number) {
            return this == BARE
                    ? number
                    : "{\"value\": " + number + ", \"source_span\": {\"page\": 1, \"text\": \"" + number + "\"}}";
        }
    }

    private static Verdict run(MathGate gate, String json) {
        return Contract.builder().gate(gate).build().evaluate(json);
    }

    private static Violation only(Verdict v) {
        assertEquals(Status.REJECTED, v.status());
        assertEquals(1, v.violations().size(), () -> v.violations().toString());
        return v.violations().get(0);
    }

    @Nested
    class EqualsSum {

        private final MathGate totalGate = MathGate.builder()
                .equalsSum("total", "subtotal", "tax")
                .tolerance(0.01)
                .build();

        private String totals(Shape s, String subtotal, String tax, String total) {
            return "{\"subtotal\": " + s.n(subtotal) + ", \"tax\": " + s.n(tax) + ", \"total\": " + s.n(total) + "}";
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: total equal to subtotal + tax passes")
        void passes(Shape s) {
            assertTrue(run(totalGate, totals(s, "100.00", "18.00", "118.00")).passed());
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: total that is not subtotal + tax is rejected with both numbers")
        void wrongTotalFails(Shape s) {
            Violation v = only(run(totalGate, totals(s, "100.00", "18.00", "120.00")));
            assertEquals("total", v.path());
            assertTrue(v.message().contains("118.00") && v.message().contains("120.00"), v.message());
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: [*] paths are matched element by element")
        void elementWise(Shape s) {
            MathGate gate = MathGate.builder()
                    .equalsSum("line_items[*].gross", "line_items[*].net", "line_items[*].tax")
                    .build();
            String json = "{\"line_items\": ["
                    + "{\"net\": " + s.n("10.00") + ", \"tax\": " + s.n("1.80") + ", \"gross\": " + s.n("11.80") + "},"
                    + "{\"net\": " + s.n("20.00") + ", \"tax\": " + s.n("3.60") + ", \"gross\": " + s.n("25.00") + "}]}";
            assertEquals("line_items[1].gross", only(run(gate, json)).path());
        }

        @Test
        @DisplayName("R5: a missing part is a violation at that part")
        void missingPartFails() {
            assertEquals("tax", only(run(totalGate, "{\"subtotal\": 100, \"total\": 100}")).path());
        }

        @Test
        @DisplayName("R5: [*] paths with different lengths are a violation, not a guess")
        void lengthMismatchFails() {
            MathGate gate = MathGate.builder().equalsSum("total", "line_items[*].amount").build();
            Verdict v = run(gate, "{\"total\": 3, \"line_items\": [{\"amount\": 1}, {\"amount\": 2}]}");
            assertEquals(Status.REJECTED, v.status());
            assertTrue(v.violations().get(0).message().contains("element by element"), v.violations().toString());
        }
    }

    @Nested
    class ProductEquals {

        private final MathGate lineGate = MathGate.builder()
                .productEquals("line_items[*].amount", "line_items[*].quantity", "line_items[*].unit_price")
                .tolerance(0.01)
                .build();

        private String lines(Shape s, String[]... rows) {
            var b = new StringBuilder("{\"line_items\": [");
            for (int i = 0; i < rows.length; i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append("{\"quantity\": ").append(s.n(rows[i][0]))
                        .append(", \"unit_price\": ").append(s.n(rows[i][1]))
                        .append(", \"amount\": ").append(s.n(rows[i][2])).append('}');
            }
            return b.append("]}").toString();
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: quantity × unit price equal to amount on every line passes")
        void passes(Shape s) {
            Verdict v = run(lineGate, lines(s, new String[] {"2", "12.50", "25.00"}, new String[] {"3", "3.33", "9.99"}));
            assertTrue(v.passed(), () -> v.violations().toString());
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: a line whose amount is not quantity × unit price is rejected at that line")
        void wrongLineFails(Shape s) {
            Violation v = only(run(lineGate, lines(s, new String[] {"2", "12.50", "25.00"}, new String[] {"3", "4.00", "10.00"})));
            assertEquals("line_items[1].amount", v.path());
            assertTrue(v.message().contains("12.00"), v.message());
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: product rounding inside tolerance passes")
        void roundingInsideTolerancePasses(Shape s) {
            assertTrue(run(lineGate, lines(s, new String[] {"3", "3.333", "10.00"})).passed());
        }
    }

    @Nested
    class Range {

        private final MathGate rangeGate = MathGate.builder()
                .range("line_items[*].amount", 0, 10000)
                .build();

        private String amounts(Shape s, String... values) {
            var b = new StringBuilder("{\"line_items\": [");
            for (int i = 0; i < values.length; i++) {
                b.append(i > 0 ? ", " : "").append("{\"amount\": ").append(s.n(values[i])).append('}');
            }
            return b.append("]}").toString();
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: values inside the range, and exactly on both bounds, pass")
        void insideAndOnBoundsPass(Shape s) {
            assertTrue(run(rangeGate, amounts(s, "0", "45.10", "10000")).passed());
        }

        @ParameterizedTest
        @EnumSource(Shape.class)
        @DisplayName("R5: a value outside the range is rejected at that element")
        void outsideFails(Shape s) {
            Violation v = only(run(rangeGate, amounts(s, "45.10", "-5.00")));
            assertEquals("line_items[1].amount", v.path());
            assertTrue(v.message().contains("-5.00"), v.message());
        }

        @Test
        @DisplayName("R5: min greater than max is rejected at build")
        void badBoundsRejected() {
            assertThrows(IllegalArgumentException.class, () -> MathGate.builder().range("a", 5, 1));
        }
    }

    @Nested
    class Custom {

        private static final String INVOICE = "{\"subtotal\": 100.00, \"discount\": 25.00}";

        private static MathGate discountAtMost20Percent() {
            return MathGate.builder()
                    .custom("discount at most 20% of subtotal",
                            n -> n.get("discount").compareTo(n.get("subtotal").multiply(new BigDecimal("0.20"))) <= 0)
                    .build();
        }

        @Test
        @DisplayName("R5: a custom rule that holds passes")
        void holds() {
            assertTrue(run(discountAtMost20Percent(), "{\"subtotal\": 100.00, \"discount\": 20.00}").passed());
        }

        @Test
        @DisplayName("R5: a custom rule that fails is reported by name")
        void failsByName() {
            Violation v = only(run(discountAtMost20Percent(), INVOICE));
            assertTrue(v.message().contains("discount at most 20% of subtotal"), v.message());
        }

        @Test
        @DisplayName("R5: a custom rule reading a missing value fails at that path")
        void missingValueFails() {
            assertEquals("discount", only(run(discountAtMost20Percent(), "{\"subtotal\": 100.00}")).path());
        }

        @Test
        @DisplayName("R5: a custom rule that throws is a violation, never a pass")
        void throwingRuleFails() {
            MathGate gate = MathGate.builder()
                    .custom("explodes", n -> {
                        throw new IllegalStateException("boom");
                    })
                    .build();
            Violation v = only(run(gate, INVOICE));
            assertTrue(v.message().contains("explodes") && v.message().contains("boom"), v.message());
        }

        @Test
        @DisplayName("R5: all() reads every element of a [*] path")
        void allReadsEveryElement() {
            MathGate gate = MathGate.builder()
                    .custom("at least two items", n -> n.all("line_items[*].amount").size() >= 2)
                    .build();
            assertTrue(run(gate, "{\"line_items\": [{\"amount\": 1}, {\"amount\": 2}]}").passed());
            assertEquals(Status.REJECTED, run(gate, "{\"line_items\": [{\"amount\": 1}]}").status());
        }
    }

    @Test
    @DisplayName("R5: several rules in one gate report every violation")
    void severalRulesReportEverything() {
        MathGate gate = MathGate.builder()
                .equalsSum("total", "subtotal", "tax")
                .range("tax", 0, 50)
                .build();
        Verdict v = run(gate, "{\"subtotal\": 100, \"tax\": 60, \"total\": 100}");
        assertEquals(2, v.violations().size(), () -> v.violations().toString());
    }
}
