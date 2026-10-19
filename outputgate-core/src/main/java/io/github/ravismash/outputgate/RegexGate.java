package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Text patterns per field (R8). {@code require}: the whole value must match.
 * {@code forbid}: no part of the value may match. {@code isoDate}: a real
 * {@code yyyy-MM-dd} date. {@code currencyCode}: an ISO 4217 code. Values are read
 * through evidence objects (R9). A missing or non-text value fails {@code require},
 * {@code isoDate} and {@code currencyCode}; {@code forbid} has nothing to check in a missing field.
 */
public final class RegexGate implements Gate {

    private static final String NAME = "regex";
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private enum Kind { REQUIRE, FORBID, ISO_DATE, CURRENCY }

    private record Rule(Kind kind, String path, Pattern pattern) {}

    private final List<Rule> rules;

    private RegexGate(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Starts a new regex gate. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Violation> check(JsonNode output, EvalContext context) {
        List<Violation> out = new ArrayList<>();
        for (Rule rule : rules) {
            for (Paths.Hit hit : Paths.resolve(output, rule.path())) {
                String problem = problem(rule, hit.node());
                if (problem != null) {
                    out.add(new Violation(NAME, hit.path(), problem));
                }
            }
        }
        return out;
    }

    private static String problem(Rule rule, JsonNode node) {
        boolean missing = node.isMissingNode() || node.isNull();
        if (rule.kind() == Kind.FORBID) {
            if (missing || !node.isValueNode()) {
                return null;
            }
            Matcher m = rule.pattern().matcher(node.asText());
            return m.find() ? "forbidden pattern /" + rule.pattern() + "/ matched \"" + m.group() + "\"" : null;
        }
        if (missing) {
            return "no value";
        }
        if (!node.isTextual()) {
            return "not text: " + node;
        }
        String text = node.asText();
        return switch (rule.kind()) {
            case REQUIRE -> rule.pattern().matcher(text).matches()
                    ? null
                    : "\"" + text + "\" does not match /" + rule.pattern() + "/";
            case ISO_DATE -> isoDate(text) ? null : "\"" + text + "\" is not a real ISO-8601 date (yyyy-MM-dd)";
            case CURRENCY -> currency(text) ? null : "\"" + text + "\" is not an ISO 4217 currency code";
            case FORBID -> throw new IllegalStateException("handled above");
        };
    }

    private static boolean isoDate(String text) {
        if (!ISO_DATE.matcher(text).matches()) {
            return false;
        }
        try {
            LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean currency(String text) {
        if (!CURRENCY.matcher(text).matches()) {
            return false;
        }
        try {
            Currency.getInstance(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Builds a {@link RegexGate}. */
    public static final class Builder {

        private final List<Rule> rules = new ArrayList<>();

        private Builder() {}

        /**
         * The whole value at {@code path} must match {@code regex}.
         *
         * @param path field path; {@code [*]} covers every element
         * @param regex a {@link Pattern} regex
         * @return this builder
         */
        public Builder require(String path, String regex) {
            rules.add(new Rule(Kind.REQUIRE, Paths.validate(path), compile(regex)));
            return this;
        }

        /**
         * No part of the value at {@code path} may match {@code regex}.
         *
         * @param path field path; {@code [*]} covers every element
         * @param regex a {@link Pattern} regex
         * @return this builder
         */
        public Builder forbid(String path, String regex) {
            rules.add(new Rule(Kind.FORBID, Paths.validate(path), compile(regex)));
            return this;
        }

        /**
         * The value at {@code path} must be a real ISO-8601 date ({@code yyyy-MM-dd}).
         *
         * @param path field path
         * @return this builder
         */
        public Builder isoDate(String path) {
            rules.add(new Rule(Kind.ISO_DATE, Paths.validate(path), ISO_DATE));
            return this;
        }

        /**
         * The value at {@code path} must be an ISO 4217 currency code, e.g. {@code USD}.
         *
         * @param path field path
         * @return this builder
         */
        public Builder currencyCode(String path) {
            rules.add(new Rule(Kind.CURRENCY, Paths.validate(path), CURRENCY));
            return this;
        }

        /**
         * Builds the gate; a regex gate with no rule would pass everything, so that is an error.
         *
         * @return the gate
         */
        public RegexGate build() {
            if (rules.isEmpty()) {
                throw new IllegalStateException("RegexGate needs at least one rule");
            }
            return new RegexGate(rules);
        }

        private static Pattern compile(String regex) {
            try {
                return Pattern.compile(regex);
            } catch (PatternSyntaxException | NullPointerException e) {
                throw new IllegalArgumentException("invalid regex: " + regex, e);
            }
        }
    }
}
