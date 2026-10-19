package io.github.ravismash.outputgate.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.RegexGate;
import io.github.ravismash.outputgate.SchemaGate;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The README's code, run as tests. If the README and this file disagree, the README is wrong.
 * Keep the snippets here byte-for-byte what README.md shows.
 */
class ReadmeExamplesTest {

    // README: "Quick start"
    static Contract invoiceContract() {
        return Contract.builder()
                .gate(SchemaGate.fromString("""
                        {"type": "object", "required": ["vendor", "subtotal", "line_items"]}"""))
                .gate(MathGate.builder()
                        .sumEquals("line_items[*].amount", "subtotal")
                        .tolerance(0.01)
                        .build())
                .gate(RegexGate.builder()
                        .forbid("line_items[*].description", "(?i)\\b(sorry|as an ai)\\b")
                        .build())
                .build();
    }

    static final String MODEL_OUTPUT = """
            {"vendor": "Acme", "subtotal": 100.00,
             "line_items": [{"description": "Widgets", "amount": 40.00},
                            {"description": "Sorry, I could not read this line", "amount": 34.50}]}""";

    @Test
    @DisplayName("README quick start: the verdict and its violations are exactly as printed")
    void quickStart() {
        Verdict verdict = invoiceContract().evaluate(MODEL_OUTPUT);
        List<String> printed = verdict.violations().stream()
                .map(v -> "[" + v.gate() + "] " + v.path() + ": " + v.message())
                .toList();

        assertEquals("REJECTED", verdict.status().toString());
        assertEquals(List.of(
                "[math] subtotal: sum of line_items[*].amount is 74.50 but subtotal is 100.00 (tolerance 0.01)",
                "[regex] line_items[1].description: forbidden pattern /(?i)\\b(sorry|as an ai)\\b/ matched \"Sorry\""),
                printed);
    }

    @Test
    @DisplayName("README JUnit example: passes and isRejectedBy(..).byGate(..)")
    void junitExample() {
        String good = """
                {"vendor": "Acme", "subtotal": 74.50,
                 "line_items": [{"description": "Widgets", "amount": 40.00},
                                {"description": "Bolts", "amount": 34.50}]}""";

        LlmAssert.assertThat(good).passes(invoiceContract());
        LlmAssert.assertThat(MODEL_OUTPUT).isRejectedBy(invoiceContract()).byGate("math").byGate("regex");
    }
}
