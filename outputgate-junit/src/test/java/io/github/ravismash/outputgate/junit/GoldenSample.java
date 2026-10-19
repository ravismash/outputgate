package io.github.ravismash.outputgate.junit;

import org.junit.jupiter.params.ParameterizedTest;

/** The way a user writes a golden-set test; also run by {@link GoldenSetSourceTest}. */
class GoldenSample {

    @ParameterizedTest
    @GoldenSetSource("golden/sample.jsonl")
    void invoice(GoldenCase c) {
        LlmAssert.assertThat(c).matches(Contracts.INVOICE);
    }
}
