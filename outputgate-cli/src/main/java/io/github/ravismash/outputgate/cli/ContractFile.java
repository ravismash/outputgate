package io.github.ravismash.outputgate.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.RegexGate;
import io.github.ravismash.outputgate.SchemaGate;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a contract from a YAML or JSON file. Gates run in the order schema, regex, math,
 * provenance, whatever order the file lists them in. Every key is checked: a misspelt key is an
 * error, never a silently skipped rule, because a rule that doesn't run passes everything.
 *
 * <pre>
 * schema: record.schema.json          # a path relative to this file, or the schema inline
 * regex:
 *   - require: { path: id, pattern: "[A-Z]{2}-\\d+" }
 *   - forbid: { path: "notes", pattern: "(?i)lorem ipsum" }
 *   - isoDate: issued_on
 *   - currencyCode: currency
 * math:
 *   tolerance: 0.01
 *   rules:
 *     - sumEquals: { items: "items[*].amount", total: subtotal }
 *     - sumEqualsRounded: { items: "rows[*].value", total: total }
 *     - equalsSum: { total: total, parts: [subtotal, tax] }
 *     - productEquals: { result: "items[*].amount", factors: ["items[*].quantity", "items[*].unit_price"] }
 *     - range: { path: "items[*].quantity", min: 0, max: 10000 }
 * provenance:
 *   require: [id, total]
 *   optional: [notes]
 *   grounded: true                    # or: ignoring-whitespace
 *   numbersMatchQuotes: true
 *   ignoringCurrencySigns: true
 *   ignoringTypography: true
 *   joiningHyphenatedLines: true
 *   ignoringDigitGrouping: true
 * </pre>
 */
public final class ContractFile {

    private static final ObjectMapper YAML = YAMLMapper.builder().build();
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final Set<String> TOP = Set.of("schema", "regex", "math", "provenance");
    private static final Set<String> PROVENANCE = Set.of("require", "optional", "grounded", "numbersMatchQuotes",
            "ignoringCurrencySigns", "ignoringTypography", "joiningHyphenatedLines", "ignoringDigitGrouping");

    private ContractFile() {}

    /**
     * Loads the contract at {@code file}.
     *
     * @param file a YAML or JSON contract; a schema path in it resolves against its directory
     * @return the contract
     * @throws ContractException if the file can't be read or doesn't describe a valid contract
     */
    public static Contract load(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ContractException("cannot read contract " + file + ": " + e.getMessage());
        }
        JsonNode root;
        try {
            root = (isJson(file) ? JSON : YAML).readTree(text);
        } catch (IOException e) {
            throw new ContractException("contract " + file + " is not valid " + (isJson(file) ? "JSON" : "YAML")
                    + ": " + e.getMessage());
        }
        Path base = file.toAbsolutePath().getParent();
        try {
            return build(root, base);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ContractException("contract " + file + ": " + e.getMessage());
        }
    }

    /** Builds a contract from an already parsed document; schema paths resolve against {@code base}. */
    static Contract build(JsonNode root, Path base) {
        if (root == null || !root.isObject()) {
            throw new ContractException("a contract must be a mapping with at least one of " + sorted(TOP));
        }
        keys(root, TOP, "contract");
        if (root.isEmpty()) {
            throw new ContractException("the contract has no gates; add at least one of " + sorted(TOP));
        }
        Contract.Builder contract = Contract.builder();
        if (root.has("schema")) {
            contract.gate(schema(root.get("schema"), base));
        }
        if (root.has("regex")) {
            contract.gate(regex(root.get("regex")));
        }
        if (root.has("math")) {
            contract.gate(math(root.get("math")));
        }
        if (root.has("provenance")) {
            contract.gate(provenance(root.get("provenance")));
        }
        return contract.build();
    }

    private static SchemaGate schema(JsonNode node, Path base) {
        if (node.isObject()) {
            return SchemaGate.fromString(node.toString());
        }
        if (!node.isTextual()) {
            throw new ContractException("schema must be a file path or an inline JSON Schema");
        }
        Path file = base == null ? Path.of(node.asText()) : base.resolve(node.asText());
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ContractException("cannot read schema " + file + ": " + e.getMessage());
        }
        if (!isJson(file)) {
            try {
                text = YAML.readTree(text).toString();
            } catch (IOException e) {
                throw new ContractException("schema " + file + " is not valid YAML: " + e.getMessage());
            }
        }
        return SchemaGate.fromString(text);
    }

    private static RegexGate regex(JsonNode node) {
        RegexGate.Builder b = RegexGate.builder();
        for (JsonNode rule : list(node, "regex")) {
            Map.Entry<String, JsonNode> r = single(rule, "regex rule");
            switch (r.getKey()) {
                case "require" -> {
                    keys(r.getValue(), Set.of("path", "pattern"), "regex require");
                    b.require(text(r.getValue(), "path", "regex require"), text(r.getValue(), "pattern", "regex require"));
                }
                case "forbid" -> {
                    keys(r.getValue(), Set.of("path", "pattern"), "regex forbid");
                    b.forbid(text(r.getValue(), "path", "regex forbid"), text(r.getValue(), "pattern", "regex forbid"));
                }
                case "isoDate" -> b.isoDate(path(r.getValue(), "regex isoDate"));
                case "currencyCode" -> b.currencyCode(path(r.getValue(), "regex currencyCode"));
                default -> throw new ContractException("unknown regex rule '" + r.getKey()
                        + "'; expected require, forbid, isoDate or currencyCode");
            }
        }
        return b.build();
    }

    private static MathGate math(JsonNode node) {
        if (!node.isObject()) {
            throw new ContractException("math must be a mapping with 'rules' and an optional 'tolerance'");
        }
        keys(node, Set.of("tolerance", "rules"), "math");
        MathGate.Builder b = MathGate.builder();
        if (node.has("tolerance")) {
            b.tolerance(number(node.get("tolerance"), "math tolerance"));
        }
        if (!node.has("rules")) {
            throw new ContractException("math needs 'rules'");
        }
        for (JsonNode rule : list(node.get("rules"), "math rules")) {
            Map.Entry<String, JsonNode> r = single(rule, "math rule");
            String what = "math " + r.getKey();
            JsonNode v = r.getValue();
            switch (r.getKey()) {
                case "sumEquals" -> {
                    keys(v, Set.of("items", "total"), what);
                    b.sumEquals(text(v, "items", what), text(v, "total", what));
                }
                case "sumEqualsRounded" -> {
                    keys(v, Set.of("items", "total"), what);
                    b.sumEqualsRounded(text(v, "items", what), text(v, "total", what));
                }
                case "equalsSum" -> {
                    keys(v, Set.of("total", "parts"), what);
                    b.equalsSum(text(v, "total", what), texts(v.get("parts"), what + " parts"));
                }
                case "productEquals" -> {
                    keys(v, Set.of("result", "factors"), what);
                    b.productEquals(text(v, "result", what), texts(v.get("factors"), what + " factors"));
                }
                case "range" -> {
                    keys(v, Set.of("path", "min", "max"), what);
                    b.range(text(v, "path", what), number(v.get("min"), what + " min"), number(v.get("max"), what + " max"));
                }
                default -> throw new ContractException("unknown math rule '" + r.getKey()
                        + "'; expected sumEquals, sumEqualsRounded, equalsSum, productEquals or range");
            }
        }
        return b.build();
    }

    private static ProvenanceGate provenance(JsonNode node) {
        if (!node.isObject()) {
            throw new ContractException("provenance must be a mapping");
        }
        keys(node, PROVENANCE, "provenance");
        ProvenanceGate.Builder b = ProvenanceGate.builder();
        if (node.has("require")) {
            b.require(texts(node.get("require"), "provenance require"));
        }
        if (node.has("optional")) {
            b.optional(texts(node.get("optional"), "provenance optional"));
        }
        JsonNode grounded = node.get("grounded");
        if (grounded != null) {
            if (grounded.isBoolean()) {
                if (grounded.booleanValue()) {
                    b.grounded();
                }
            } else if (grounded.isTextual() && grounded.asText().equals("ignoring-whitespace")) {
                b.groundedIgnoringWhitespace();
            } else {
                throw new ContractException("provenance grounded must be true, false or ignoring-whitespace");
            }
        }
        if (flag(node, "numbersMatchQuotes")) {
            b.numbersMatchQuotes();
        }
        if (flag(node, "ignoringCurrencySigns")) {
            b.ignoringCurrencySigns();
        }
        if (flag(node, "ignoringTypography")) {
            b.ignoringTypography();
        }
        if (flag(node, "joiningHyphenatedLines")) {
            b.joiningHyphenatedLines();
        }
        if (flag(node, "ignoringDigitGrouping")) {
            b.ignoringDigitGrouping();
        }
        return b.build();
    }

    // --- small readers -------------------------------------------------------------------------

    private static boolean isJson(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static void keys(JsonNode node, Set<String> allowed, String what) {
        if (!node.isObject()) {
            throw new ContractException(what + " must be a mapping with " + sorted(allowed));
        }
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if (!allowed.contains(key)) {
                throw new ContractException("unknown key '" + key + "' in " + what + "; expected one of " + sorted(allowed));
            }
        }
    }

    private static List<JsonNode> list(JsonNode node, String what) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new ContractException(what + " must be a non-empty list");
        }
        List<JsonNode> out = new ArrayList<>();
        node.forEach(out::add);
        return out;
    }

    private static Map.Entry<String, JsonNode> single(JsonNode node, String what) {
        if (!node.isObject() || node.size() != 1) {
            throw new ContractException("each " + what + " must be a mapping with exactly one key, got " + node);
        }
        return node.fields().next();
    }

    private static String text(JsonNode node, String key, String what) {
        JsonNode v = node.get(key);
        if (v == null || !v.isTextual() || v.asText().isEmpty()) {
            throw new ContractException(what + " needs '" + key + "' as text");
        }
        return v.asText();
    }

    private static String path(JsonNode node, String what) {
        if (!node.isTextual() || node.asText().isEmpty()) {
            throw new ContractException(what + " takes a field path, got " + node);
        }
        return node.asText();
    }

    private static String[] texts(JsonNode node, String what) {
        if (node != null && node.isTextual()) {
            return new String[] {node.asText()};
        }
        List<String> out = new ArrayList<>();
        for (JsonNode v : list(node, what)) {
            if (!v.isTextual()) {
                throw new ContractException(what + " must be field paths, got " + v);
            }
            out.add(v.asText());
        }
        return out.toArray(String[]::new);
    }

    private static BigDecimal number(JsonNode node, String what) {
        if (node == null || !node.isNumber()) {
            throw new ContractException(what + " must be a number");
        }
        return node.decimalValue();
    }

    private static boolean flag(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null) {
            return false;
        }
        if (!v.isBoolean()) {
            throw new ContractException("provenance " + key + " must be true or false");
        }
        return v.booleanValue();
    }

    private static String sorted(Set<String> keys) {
        return keys.stream().sorted().toList().toString();
    }

    /** A contract file that can't be read or doesn't describe a valid contract. */
    public static final class ContractException extends RuntimeException {
        ContractException(String message) {
            super(message);
        }
    }
}
