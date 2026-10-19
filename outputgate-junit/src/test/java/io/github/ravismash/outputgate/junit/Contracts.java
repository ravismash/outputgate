package io.github.ravismash.outputgate.junit;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.RegexGate;

/** The contract the tests use. */
final class Contracts {

    private Contracts() {}

    static final Contract INVOICE = Contract.builder()
            .gate(MathGate.builder().sumEquals("line_items[*].amount", "subtotal").tolerance(0.01).build())
            .gate(RegexGate.builder().forbid("line_items[*].description", "(?i)\\b(sorry|as an ai)\\b").build())
            .build();
}
