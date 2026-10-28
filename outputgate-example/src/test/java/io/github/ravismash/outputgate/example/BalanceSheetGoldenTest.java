package io.github.ravismash.outputgate.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.junit.GoldenCase;
import io.github.ravismash.outputgate.junit.GoldenRunner;
import io.github.ravismash.outputgate.junit.GoldenSetSource;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * A real balance sheet (Corteva's, from SEC EDGAR) and recorded model answers to it: the two
 * recorded answers come from the benchmark's held-out run; the other cases each change one thing
 * in the correct one.
 */
class BalanceSheetGoldenTest {

    private static final String SET = "golden/balance-sheets.jsonl";

    @ParameterizedTest
    @GoldenSetSource(SET)
    void balanceSheet(GoldenCase c) {
        LlmAssert.assertThat(c).withContext(BalanceSheetExample.context()).matches(BalanceSheetExample.contract());
    }

    @Test
    @DisplayName("pass-rate gate: every golden case behaves as recorded")
    void passRate() {
        GoldenRunner.of(BalanceSheetExample.contract())
                .withContext(BalanceSheetExample.context())
                .run(SET)
                .assertPassRateAtLeast(1.0);
    }

    @Test
    @DisplayName("without the region, the subsidiary's figures pass: every quote is on the page and every sum holds")
    void groundingAloneAcceptsTheWrongStatement() throws IOException {
        Contract unscoped = Contract.builder()
                .gate(MathGate.builder()
                        .sumEqualsRounded("current_assets[*].amount", "total_current_assets")
                        .sumEqualsRounded("current_liabilities[*].amount", "total_current_liabilities")
                        .tolerance(1)
                        .build())
                .gate(ProvenanceGate.builder()
                        .require("total_current_assets", "total_current_liabilities", "current_liabilities[*].amount")
                        .groundedIgnoringWhitespace()
                        .ignoringCurrencySigns()
                        .numbersMatchQuotes()
                        .build())
                .build();
        String qwen = recorded(1);
        Verdict v = unscoped.evaluate(qwen, BalanceSheetExample.context());
        assertTrue(v.passed(), () -> v.violations().toString());
        LlmAssert.assertThat(qwen).withContext(BalanceSheetExample.context())
                .isRejectedBy(BalanceSheetExample.contract()).byGate("provenance");
    }

    /** The output of the golden set's case on line {@code index}, counted from 0. */
    private static String recorded(int index) throws IOException {
        try (InputStream in = BalanceSheetGoldenTest.class.getClassLoader().getResourceAsStream(SET)) {
            String line = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")[index];
            return new ObjectMapper().readTree(line).get("output").toString();
        }
    }
}
