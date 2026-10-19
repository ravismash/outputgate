package io.github.ravismash.outputgate.example;

import io.github.ravismash.outputgate.junit.GoldenCase;
import io.github.ravismash.outputgate.junit.GoldenRunner;
import io.github.ravismash.outputgate.junit.GoldenSetSource;
import io.github.ravismash.outputgate.junit.LlmAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

/**
 * R12 acceptance: the example golden set shows 7 named tests in the JUnit report, and
 * one pass-rate gate. Every case is a recorded model output; nothing calls a model.
 */
class InvoiceGoldenTest {

    private static final String SET = "golden/invoices.jsonl";

    @ParameterizedTest
    @GoldenSetSource(SET)
    void invoice(GoldenCase c) {
        LlmAssert.assertThat(c).withContext(InvoiceExample.context()).matches(InvoiceExample.contract());
    }

    @Test
    @DisplayName("pass-rate gate: every golden case behaves as recorded")
    void passRate() {
        GoldenRunner.of(InvoiceExample.contract())
                .withContext(InvoiceExample.context())
                .run(SET)
                .assertPassRateAtLeast(1.0);
    }
}
