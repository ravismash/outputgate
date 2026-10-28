package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The opt-in folds for text from a PDF or OCR: each folds one named difference and nothing else. */
class ProvenanceGatePdfTextTest {

    private static String ev(String value, String text) {
        return "{\"value\": " + value + ", \"source_span\": {\"page\": 1, \"text\": \"" + text + "\"}}";
    }

    private static Verdict run(ProvenanceGate.Builder gate, String page, String value, String quote) {
        String json = "{\"field\": " + ev(value, quote) + "}";
        return Contract.builder().gate(gate.build()).build()
                .evaluate(json, EvalContext.empty().withSource(SourceDocument.ofPages(page)));
    }

    private static ProvenanceGate.Builder literal() {
        return ProvenanceGate.builder().require("field").grounded();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Certiﬁcate of ﬂow testing|Certificate of flow testing",
            "Total： １２，３４５|Total: 12,345",
            "Amount ٣٤٥|Amount 345",
            "Patient’s “fasting” sample|Patient's \\\"fasting\\\" sample",
            "See notes…|See notes...",
            "Net income|Net income",
            "Gl­ucose|Glucose",
            "Zero​width|Zerowidth",
    })
    @DisplayName("ignoringTypography: ligatures, full-width forms, digits of other scripts, curly quotes, odd spaces and invisible characters fold")
    void typographyFolds(String page, String quote) {
        assertEquals(Status.REJECTED, run(literal(), page, "\"x\"", quote).status(), "literal grounding still differs");
        Verdict v = run(literal().ignoringTypography(), page, "\"x\"", quote);
        assertTrue(v.passed(), () -> v.violations().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Glucose 0.8", "glucose 5.8", "GIucose 5.8", "Glucose 5,8", "Glucose 58"})
    @DisplayName("ignoringTypography: case, OCR look-alikes (l/I, 0/O) and every digit and separator still must match")
    void typographyChangesNoContent(String quote) {
        Verdict v = run(literal().ignoringTypography(), "Glucose 5.8", "\"x\"", quote);
        assertEquals(Status.REJECTED, v.status(), quote);
        assertTrue(v.violations().get(0).message().contains("(typography ignored)"), v.violations().get(0).message());
    }

    @Test
    @DisplayName("joiningHyphenatedLines: a word the page breaks across lines grounds joined, or as printed")
    void hyphenatedWordJoins() {
        String page = "Reference range for inter-\nnational units";
        assertEquals(Status.REJECTED, run(literal(), page, "\"x\"", "international units").status());
        for (String quote : new String[] {"international units", "inter-\\nnational units"}) {
            Verdict v = run(literal().joiningHyphenatedLines(), page, "\"x\"", quote);
            assertTrue(v.passed(), () -> quote + ": " + v.violations());
        }
        Verdict soft = run(literal().joiningHyphenatedLines(), "Haemo­\nglobin", "\"x\"", "Haemoglobin");
        assertTrue(soft.passed(), () -> soft.violations().toString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Account 1234-\\n5678|12345678",
            "Ward B-\\nNorth|BNorth",
            "well-\\nknown|well known",
    })
    @DisplayName("joiningHyphenatedLines: numbers, codes and capitalised words are never joined, and nothing is joined without a line break")
    void onlyWordsJoin(String page, String quote) {
        Verdict v = run(literal().joiningHyphenatedLines(), page.replace("\\n", "\n"), "\"x\"", quote);
        assertEquals(Status.REJECTED, v.status(), quote);
        assertTrue(v.violations().get(0).message().contains("(line-end hyphens ignored)"), v.violations().get(0).message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "1,234,567", "1'234'567", "1 234 567", "1 234,567"})
    @DisplayName("ignoringDigitGrouping: grouping marks between digits don't count, on either side")
    void groupingIgnored(String quote) {
        for (String page : new String[] {"Total 1,234,567", "Total 1'234'567", "Total 1234567"}) {
            Verdict v = run(literal().ignoringDigitGrouping().numbersMatchQuotes(), page, "1234567", quote);
            assertTrue(v.passed(), () -> page + " / " + quote + ": " + v.violations());
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Rate 12.345|12345",
            "Cells 12 345|12345",
            "Total 1,23,456|123456",
            "Total 1,234|1,235",
    })
    @DisplayName("ignoringDigitGrouping: \".\", plain spaces and groups that aren't three digits are left alone, and digits must match")
    void groupingFoldsNothingElse(String page, String quote) {
        Verdict v = run(literal().ignoringDigitGrouping(), page, "\"x\"", quote);
        assertEquals(Status.REJECTED, v.status(), page + " / " + quote);
    }

    @Test
    @DisplayName("ignoringDigitGrouping: numbersMatchQuotes reads a quote grouped with apostrophes or thin spaces")
    void numbersMatchGroupedQuote() {
        String page = "Total 1'234'567";
        Verdict strict = run(ProvenanceGate.builder().require("field").numbersMatchQuotes(), page, "1234567", "1'234'567");
        assertEquals(Status.REJECTED, strict.status());
        Verdict folded = run(literal().ignoringDigitGrouping().numbersMatchQuotes(), page, "1234567", "1'234'567");
        assertTrue(folded.passed(), () -> folded.violations().toString());
        Verdict wrong = run(literal().ignoringDigitGrouping().numbersMatchQuotes(), page, "1234568", "1'234'567");
        assertEquals(Status.REJECTED, wrong.status());
    }

    @Test
    @DisplayName("the folds combine with whitespace, currency signs and regions, and a fabricated quote is still rejected")
    void foldsCombine() {
        String page = "Summary\nTotal ＄ １ ２３４ ５６７\nDetail\nTotal $ 9,999,999";
        ProvenanceGate.Builder all = ProvenanceGate.builder().require("field")
                .groundedIgnoringWhitespace().ignoringCurrencySigns().ignoringTypography().ignoringDigitGrouping()
                .joiningHyphenatedLines().numbersMatchQuotes()
                .within(Region.from("^Summary").before("^Detail"), "field");
        Verdict ok = run(all, page, "1234567", "Total $1,234,567");
        assertTrue(ok.passed(), () -> ok.violations().toString());
        assertEquals(Status.REJECTED, run(all, page, "9999999", "Total $9,999,999").status(), "outside the region");
        Verdict fabricated = run(all, page, "1234568", "Total $1,234,568");
        assertEquals(Status.REJECTED, fabricated.status());
        assertTrue(fabricated.violations().get(0).message().contains(
                "(whitespace, currency signs, typography, digit grouping and line-end hyphens ignored)"),
                fabricated.violations().get(0).message());
    }
}
