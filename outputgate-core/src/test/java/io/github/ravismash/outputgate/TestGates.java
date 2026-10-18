package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Small gates for contract tests. */
final class TestGates {

    private TestGates() {}

    static Gate failing(String name, List<String> calls) {
        return gate(name, calls, () -> List.of(new Violation(name, "", name + " failed")));
    }

    static Gate passing(String name, List<String> calls) {
        return gate(name, calls, List::of);
    }

    static Gate throwing(String name, List<String> calls) {
        return gate(name, calls, () -> {
            throw new IllegalStateException("boom");
        });
    }

    static List<String> calls() {
        return new ArrayList<>();
    }

    private interface Body {
        List<Violation> run();
    }

    private static Gate gate(String name, List<String> calls, Body body) {
        return new Gate() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<Violation> check(JsonNode output, EvalContext context) {
                calls.add(name);
                return body.run();
            }
        };
    }
}
