package io.github.ravismash.outputgate.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.junit.GoldenCase;
import io.github.ravismash.outputgate.junit.GoldenRunner;
import io.github.ravismash.outputgate.junit.GoldenSetSource;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/** A record read off an OCR'd form: the folds for OCR text accept faithful quotes and still reject wrong ones. */
class LabReportGoldenTest {

    private static final String SET = "golden/lab-reports.jsonl";

    private static String clean() throws IOException {
        try (InputStream in = LabReportGoldenTest.class.getClassLoader().getResourceAsStream("golden/clean-lab-report.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @GoldenSetSource(SET)
    void labReport(GoldenCase c) {
        LlmAssert.assertThat(c).withContext(LabReportExample.context()).matches(LabReportExample.contract());
    }

    @Test
    @DisplayName("pass-rate gate: every golden case behaves as recorded")
    void passRate() {
        GoldenRunner.of(LabReportExample.contract())
                .withContext(LabReportExample.context())
                .run(SET)
                .assertPassRateAtLeast(1.0);
    }

    @Test
    @DisplayName("without the folds, the faithful answer is rejected for the ligature, the line-break hyphen and the grouping")
    void eachFoldIsNeeded() throws IOException {
        Contract whitespaceOnly = Contract.builder().gate(ProvenanceGate.builder()
                .require("laboratory", "specimen_id", "collected_date", "results[*].test", "results[*].result")
                .groundedIgnoringWhitespace()
                .numbersMatchQuotes()
                .build()).build();
        Verdict v = whitespaceOnly.evaluate(clean(), LabReportExample.context());
        assertEquals(List.of("laboratory", "results[1].test", "results[2].result"),
                v.violations().stream().map(x -> x.path()).toList(), () -> v.violations().toString());

        Verdict folded = LabReportExample.contract().evaluate(clean(), LabReportExample.context());
        assertTrue(folded.passed(), () -> folded.violations().toString());
    }
}
