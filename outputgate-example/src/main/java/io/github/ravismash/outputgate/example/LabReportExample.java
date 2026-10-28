package io.github.ravismash.outputgate.example;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.RegexGate;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.TypeGate;

/**
 * A record read off a form whose text came from OCR: a synthetic two-page lab report with the
 * usual artifacts (a ligature in the laboratory's name, a no-break space in a date, a test name
 * hyphenated across two lines, a result grouped with a narrow no-break space). A model types the
 * plain forms, so the contract grounds with the folds for PDF and OCR text; each folds one kind of
 * difference that carries no content, and every letter and digit must still match.
 */
public final class LabReportExample {

    private static final String DOCUMENT = "documents/lab-report-ocr.txt";

    private LabReportExample() {}

    /** @return the grounding settings for OCR text, shared by {@link #contract()} and its tests */
    static ProvenanceGate.Builder provenance() {
        return ProvenanceGate.builder()
                .require("laboratory", "specimen_id", "collected_date", "results[*].test", "results[*].result")
                .groundedIgnoringWhitespace()
                .ignoringTypography()
                .joiningHyphenatedLines()
                .ignoringDigitGrouping()
                .numbersMatchQuotes();
    }

    /** @return the lab-report contract: type, math (plausible values), provenance (grounded in OCR text) and regex gates */
    public static Contract contract() {
        return Contract.builder()
                .gate(TypeGate.of(LabReport.class))
                .gate(MathGate.builder()
                        .range("results[*].result", 0, 100_000)
                        .build())
                .gate(provenance().build())
                .gate(RegexGate.builder()
                        .require("specimen_id", "NCL-\\d{4}-\\d{6}")
                        .isoDate("collected_date")
                        .require("results[*].unit", "mmol/L|g/L|ng/L")
                        .require("results[*].flag", "[HLN]")
                        .forbid("results[*].test", "(?i)\\b(sorry|unknown|n/a|as an ai)\\b")
                        .build())
                .build();
    }

    /** @return the synthetic report, page by page */
    public static SourceDocument document() {
        return Resources.document(DOCUMENT);
    }

    /** @return a context carrying {@link #document()}, for grounding */
    public static EvalContext context() {
        return EvalContext.empty().withSource(document());
    }
}
