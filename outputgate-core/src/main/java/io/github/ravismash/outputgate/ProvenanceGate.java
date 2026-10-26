package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requires fields to carry evidence, {@code {"value": ..., "source_span": {"page": n, "text": "..."}}}
 * (R6). With grounding on, each span's text must occur on the page the span names, in the source
 * document from the {@link EvalContext}, compared literally, or with
 * {@link Builder#groundedIgnoringWhitespace()} ignoring whitespace and the style of hyphen. A page the
 * document doesn't have is a violation. A missing required field, a bare value or a malformed span
 * is a violation, never a pass; an {@link Builder#optional optional} field may be missing or null,
 * but when it has a value it is checked like a required one. With
 * {@link Builder#numbersMatchQuotes()}, a numeric value must also be a number its own quote prints.
 */
public final class ProvenanceGate implements Gate {

    private static final String NAME = "provenance";

    /** Any run of whitespace, Unicode included (non-breaking spaces are common in PDF text). */
    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    /** Soft hyphen, the Unicode hyphens and dashes, and minus: PDF text uses them where a model types "-". */
    private static final Pattern HYPHEN = Pattern.compile("[\\u00AD\\u2010-\\u2015\\u2212]");

    private final List<String> paths;
    private final List<String> optionalPaths;
    private final boolean grounded;
    private final boolean ignoreWhitespace;
    private final boolean numbersMatchQuotes;

    /** A number as printed: digits with optional "," grouping and a decimal part. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?|\\.\\d+");

    private ProvenanceGate(List<String> paths, List<String> optionalPaths, boolean grounded, boolean ignoreWhitespace,
            boolean numbersMatchQuotes) {
        this.paths = List.copyOf(paths);
        this.optionalPaths = List.copyOf(optionalPaths);
        this.grounded = grounded;
        this.ignoreWhitespace = ignoreWhitespace;
        this.numbersMatchQuotes = numbersMatchQuotes;
    }

    /** Starts a new provenance gate. */
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
        List<String> pages = null;
        if (grounded) {
            Optional<SourceDocument> source = context.source();
            if (source.isEmpty()) {
                out.add(new Violation(NAME, "", "grounding is on but the context has no source document"));
            } else {
                pages = ignoreWhitespace
                        ? source.get().pages().stream().map(ProvenanceGate::loosen).toList()
                        : source.get().pages();
            }
        }
        check(output, paths, false, pages, out);
        check(output, optionalPaths, true, pages, out);
        return out;
    }

    private void check(JsonNode output, List<String> fieldPaths, boolean optional, List<String> pages, List<Violation> out) {
        for (String path : fieldPaths) {
            for (Paths.Hit hit : Paths.resolve(output, path, false)) {
                if (optional && (hit.node().isMissingNode() || hit.node().isNull())) {
                    continue;
                }
                String problem = problem(hit.node());
                if (problem == null && numbersMatchQuotes) {
                    problem = unquotedNumber(hit.node());
                }
                if (problem == null && pages != null) {
                    problem = ungrounded(hit.node().get("source_span"), pages);
                }
                if (problem != null) {
                    out.add(new Violation(NAME, hit.path(), problem));
                }
            }
        }
    }

    /** Why a well-formed span is not on the page it names, or null when it is. */
    private String ungrounded(JsonNode span, List<String> pages) {
        long page = span.get("page").asLong();
        if (page > pages.size()) {
            return "source_span.page " + page + " is past the end of the document (" + pages.size() + " pages)";
        }
        String quote = span.get("text").asText();
        String wanted = ignoreWhitespace ? loosen(quote) : quote;
        if (wanted.isEmpty()) {
            return "source_span.text is only whitespace";
        }
        if (pages.get((int) page - 1).contains(wanted)) {
            return null;
        }
        String where = ignoreWhitespace ? " (whitespace ignored)" : "";
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).contains(wanted)) {
                return "source_span.text \"" + quote + "\" is not on page " + page + " (it is on page " + (i + 1) + ")" + where;
            }
        }
        return "source_span.text \"" + quote + "\" not found in the source document" + where;
    }

    /**
     * Why a numeric value is not a number its quote prints, or null when it is (or the value is
     * not a number). Numbers in the quote are read with "," as grouping and "." as the decimal
     * point, and compared by size: the sign is left to other checks, since documents print
     * negatives as "(1,203)", "-1,203" or "1,203 CR".
     */
    private static String unquotedNumber(JsonNode evidence) {
        JsonNode value = evidence.get("value");
        if (!value.isNumber()) {
            return null;
        }
        BigDecimal wanted = value.decimalValue().abs();
        String quote = evidence.get("source_span").get("text").asText();
        List<String> printed = new ArrayList<>();
        Matcher m = NUMBER.matcher(quote);
        while (m.find()) {
            String digits = m.group().replace(",", "");
            try {
                if (new BigDecimal(digits).compareTo(wanted) == 0) {
                    return null;
                }
                printed.add(m.group());
            } catch (NumberFormatException e) {
                // a stray "," run is not a number
            }
        }
        return "value " + value.decimalValue().toPlainString() + " is not a number its quote prints ("
                + (printed.isEmpty() ? "the quote has no number" : "it prints " + String.join(", ", printed)) + ")";
    }

    /** All whitespace is dropped and every kind of hyphen becomes "-". */
    private static String loosen(String s) {
        return HYPHEN.matcher(WHITESPACE.matcher(s).replaceAll("")).replaceAll("-");
    }

    /** What is wrong with the evidence object, or null when it is well formed. */
    private static String problem(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return "no value";
        }
        if (!node.isObject() || !node.has("source_span")) {
            return "bare value: expected {value, source_span: {page, text}}";
        }
        if (!node.has("value")) {
            return "evidence object has no value";
        }
        JsonNode span = node.get("source_span");
        if (!span.isObject()) {
            return "source_span must be an object {page, text}";
        }
        JsonNode page = span.get("page");
        if (page == null || !page.isIntegralNumber() || page.asLong() < 1) {
            return "source_span.page must be a whole number of 1 or more";
        }
        JsonNode text = span.get("text");
        if (text == null || !text.isTextual() || text.asText().isEmpty()) {
            return "source_span.text must be non-empty text";
        }
        return null;
    }

    /** Builds a {@link ProvenanceGate}. */
    public static final class Builder {

        private final List<String> paths = new ArrayList<>();
        private final List<String> optionalPaths = new ArrayList<>();
        private boolean grounded;
        private boolean ignoreWhitespace;
        private boolean numbersMatchQuotes;

        private Builder() {}

        /**
         * Fields that must carry evidence; paths with {@code [*]} cover every element.
         *
         * @param paths field paths
         * @return this builder
         */
        public Builder require(String... paths) {
            Arrays.stream(paths).map(Paths::validate).forEach(this.paths::add);
            return this;
        }

        /**
         * Fields that may be missing or null (e.g. an agency that isn't printed), but must carry
         * evidence, checked like a required field, whenever they have a value.
         *
         * @param paths field paths
         * @return this builder
         */
        public Builder optional(String... paths) {
            Arrays.stream(paths).map(Paths::validate).forEach(this.optionalPaths::add);
            return this;
        }

        /**
         * Also check that each span's text occurs on the page it names, in the source document from
         * {@link EvalContext}.
         *
         * @return this builder
         */
        public Builder grounded() {
            this.grounded = true;
            this.ignoreWhitespace = false;
            return this;
        }

        /**
         * Like {@link #grounded()}, but whitespace doesn't count: in both the span and the document,
         * spaces, tabs and line breaks are dropped (so "$ 3,450.00" matches "$3,450.00"), and a soft
         * hyphen, Unicode hyphen, dash or minus is read as "-". Everything else still has to match
         * exactly: case, punctuation, currency signs, digits and separators. Use it when the
         * document's text comes from a PDF or OCR, where a quote the model copied faithfully can
         * still differ from the extracted text in spacing, line breaks or hyphen characters.
         *
         * @return this builder
         */
        public Builder groundedIgnoringWhitespace() {
            this.grounded = true;
            this.ignoreWhitespace = true;
            return this;
        }

        /**
         * Also check that every numeric value is a number its own quote prints: 13226.6 quoted as
         * "13,226.6" passes; 13226600 quoted as "13,226.6" does not, though the quote is on the page.
         * Grouping commas and currency signs in the quote are ignored, and so is the sign (a negative
         * printed as "(1,203)" matches -1203). It needs no source document. Use it when values are
         * copied as printed, not scaled or converted.
         *
         * @return this builder
         */
        public Builder numbersMatchQuotes() {
            this.numbersMatchQuotes = true;
            return this;
        }

        /**
         * Builds the gate; a provenance gate that requires nothing would pass everything, so that is an error.
         *
         * @return the gate
         */
        public ProvenanceGate build() {
            if (paths.isEmpty()) {
                throw new IllegalStateException("ProvenanceGate needs at least one required field");
            }
            return new ProvenanceGate(paths, optionalPaths, grounded, ignoreWhitespace, numbersMatchQuotes);
        }
    }
}
