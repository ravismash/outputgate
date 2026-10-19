package io.github.ravismash.outputgate.junit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Loads JSONL golden sets (R12). One JSON object per line:
 * <pre>{@code
 * {"name": "clean invoice", "output": {...}, "expected": {"subtotal": 74.50}}
 * {"name": "dropped line item", "output": {...}, "expect_reject": "math"}
 * }</pre>
 * {@code name} and {@code output} are required ({@code output} may be text or JSON);
 * {@code input} is optional; {@code expected} (an object) and {@code expect_reject}
 * (a gate name, or {@code true} for any gate) are optional and exclusive. Blank lines
 * are skipped. Any other key is an error, so a typo can't silently weaken a test.
 */
public final class GoldenSet {

    private static final Set<String> KEYS = Set.of("name", "output", "input", "expected", "expect_reject");

    private GoldenSet() {}

    /**
     * Loads every case from a classpath resource.
     *
     * @param resource e.g. {@code golden/invoices.jsonl}
     * @return the cases, in file order
     * @throws IllegalArgumentException if the resource is missing or a line is invalid (the message names the line)
     */
    public static List<GoldenCase> load(String resource) {
        String text = read(resource);
        List<GoldenCase> cases = new ArrayList<>();
        Set<String> names = new HashSet<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String where = resource + " line " + (i + 1) + ": ";
            GoldenCase c = parse(lines[i], where);
            if (!names.add(c.name())) {
                throw new IllegalArgumentException(where + "duplicate name \"" + c.name() + "\"");
            }
            cases.add(c);
        }
        return List.copyOf(cases);
    }

    private static GoldenCase parse(String line, String where) {
        JsonNode node;
        try {
            node = StrictJson.parse(line);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(where + "not JSON: " + e.getOriginalMessage(), e);
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException(where + "each line must be a JSON object");
        }
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException(where + "unknown key \"" + key + "\"; allowed: " + KEYS);
            }
        }
        JsonNode name = node.get("name");
        if (name == null || !name.isTextual() || name.asText().isBlank()) {
            throw new IllegalArgumentException(where + "\"name\" (non-empty text) is required");
        }
        JsonNode output = node.get("output");
        if (output == null || output.isNull()) {
            throw new IllegalArgumentException(where + "\"output\" is required");
        }
        JsonNode input = node.get("input");
        JsonNode expected = node.get("expected");
        if (expected != null && !expected.isObject()) {
            throw new IllegalArgumentException(where + "\"expected\" must be an object");
        }
        String reject = expectReject(node.get("expect_reject"), where);
        if (expected != null && reject != null) {
            throw new IllegalArgumentException(where + "a case has either \"expected\" or \"expect_reject\", not both");
        }
        return new GoldenCase(name.asText(), text(output), input == null || input.isNull() ? null : text(input),
                expected, reject);
    }

    private static String expectReject(JsonNode node, String where) {
        if (node == null || node.isNull() || (node.isBoolean() && !node.asBoolean())) {
            return null;
        }
        if (node.isBoolean()) {
            return "*";
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            return node.asText();
        }
        throw new IllegalArgumentException(where + "\"expect_reject\" must be a gate name or true");
    }

    private static String text(JsonNode node) {
        return node.isTextual() ? node.asText() : StrictJson.write(node);
    }

    private static String read(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = GoldenSet.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("golden set not found on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("could not read golden set " + resource + ": " + e.getMessage(), e);
        }
    }
}
