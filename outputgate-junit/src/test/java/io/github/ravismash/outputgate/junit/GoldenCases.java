package io.github.ravismash.outputgate.junit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Fixtures for golden-case tests. */
final class GoldenCases {

    private GoldenCases() {}

    static final String CLEAN =
            "{\"vendor\": \"Acme\", \"subtotal\": 74.50, \"line_items\": [{\"description\": \"Widgets\", \"amount\": 74.50}]}";

    static JsonNode json(String text) {
        try {
            return new ObjectMapper().readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
