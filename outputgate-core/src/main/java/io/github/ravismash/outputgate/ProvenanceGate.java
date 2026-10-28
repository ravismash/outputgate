package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * With {@link Builder#within}, a field's quote must also lie inside a {@link Region} of the page, such
 * as one table of several that print the same labels. For text from a PDF or OCR,
 * {@link Builder#ignoringTypography()}, {@link Builder#joiningHyphenatedLines()} and
 * {@link Builder#ignoringDigitGrouping()} each fold one named kind of difference that carries no
 * content, in the quote and the document alike; no option makes matching fuzzy.
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
    private final boolean ignoreCurrency;
    private final boolean ignoreTypography;
    private final boolean joinHyphenatedLines;
    private final boolean ignoreGrouping;
    private final List<Scope> scopes;

    /** Fields whose quotes must lie inside a region. */
    private record Scope(Region region, List<String> paths) {}

    /** Currency signs ($, €, £, ¥, ₹ and the rest of Unicode's Sc category). */
    private static final Pattern CURRENCY = Pattern.compile("\\p{Sc}");

    /** A number as printed: digits with optional "," grouping and a decimal part. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?|\\.\\d+");

    /**
     * A digit-grouping mark: ",", an apostrophe or a thin or narrow no-break space (and their
     * full-width forms), between a digit and exactly three more digits. "." is never one: it is a
     * decimal point too often to fold without changing a number.
     */
    private static final Pattern GROUPING = Pattern.compile("(?U)(?<=\\d)[,'\\u2019\\u2009\\u202F\\uFF07\\uFF0C](?=\\d{3}(?!\\d))");

    /** A word broken across lines: a letter, a hyphen, a line break, then a lower-case letter. */
    private static final Pattern LINE_END_HYPHEN = Pattern.compile("(?<=\\p{L})[-\\u00AD\\u2010\\u2011]\\h*\\R\\h*(?=\\p{Ll})");

    /** Characters PDF text carries that print as nothing: soft hyphen, zero-width spaces and joiners, BOM. */
    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]");

    private ProvenanceGate(Builder b) {
        this.paths = List.copyOf(b.paths);
        this.optionalPaths = List.copyOf(b.optionalPaths);
        this.grounded = b.grounded;
        this.ignoreWhitespace = b.ignoreWhitespace;
        this.numbersMatchQuotes = b.numbersMatchQuotes;
        this.ignoreCurrency = b.ignoreCurrency;
        this.ignoreTypography = b.ignoreTypography;
        this.joinHyphenatedLines = b.joinHyphenatedLines;
        this.ignoreGrouping = b.ignoreGrouping;
        this.scopes = List.copyOf(b.scopes);
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
        List<List<String>> pages = null;
        Map<Region, Optional<Located>> regions = null;
        if (grounded) {
            Optional<SourceDocument> source = context.source();
            if (source.isEmpty()) {
                out.add(new Violation(NAME, "", "grounding is on but the context has no source document"));
            } else {
                pages = source.get().pages().stream().map(this::page).toList();
                regions = new HashMap<>();
                for (Scope scope : scopes) {
                    regions.computeIfAbsent(scope.region(), r -> locate(r, source.get()));
                }
            }
        }
        check(output, paths, false, pages, regions, out);
        check(output, optionalPaths, true, pages, regions, out);
        return out;
    }

    /** A region found in the document, with its text on each page as grounding compares it. */
    private record Located(Region.Span span, List<List<String>> pages) {}

    private Optional<Located> locate(Region region, SourceDocument document) {
        return region.locate(document).map(span -> {
            List<List<String>> text = new ArrayList<>();
            for (int p = 0; p < document.pages().size(); p++) {
                text.add(page(Region.textOn(document, span, p)));
            }
            return new Located(span, text);
        });
    }

    private void check(JsonNode output, List<String> fieldPaths, boolean optional, List<List<String>> pages,
            Map<Region, Optional<Located>> regions, List<Violation> out) {
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
                for (Scope scope : scopes) {
                    if (problem == null && regions != null && scope.paths().contains(path)) {
                        problem = outside(hit.node().get("source_span"), scope.region(), regions.get(scope.region()));
                    }
                }
                if (problem != null) {
                    out.add(new Violation(NAME, hit.path(), problem));
                }
            }
        }
    }

    /** Why a well-formed span is not on the page it names, or null when it is. */
    private String ungrounded(JsonNode span, List<List<String>> pages) {
        long page = span.get("page").asLong();
        if (page > pages.size()) {
            return "source_span.page " + page + " is past the end of the document (" + pages.size() + " pages)";
        }
        String quote = span.get("text").asText();
        String wanted = normalize(quote);
        if (wanted.isEmpty()) {
            return "source_span.text is only whitespace" + (ignoreCurrency ? " or currency signs" : "");
        }
        if (contains(pages.get((int) page - 1), wanted)) {
            return null;
        }
        String where = ignored();
        for (int i = 0; i < pages.size(); i++) {
            if (contains(pages.get(i), wanted)) {
                return "source_span.text \"" + quote + "\" is not on page " + page + " (it is on page " + (i + 1) + ")" + where;
            }
        }
        return "source_span.text \"" + quote + "\" not found in the source document" + where;
    }

    /** Why a grounded span is not inside the region its field must be quoted from, or null when it is. */
    private String outside(JsonNode span, Region region, Optional<Located> located) {
        if (located.isEmpty()) {
            return "the region this field must be quoted from is not in the source document (no line matches its start): "
                    + region;
        }
        int page = (int) span.get("page").asLong() - 1;
        String quote = span.get("text").asText();
        if (contains(located.get().pages().get(page), normalize(quote))) {
            return null;
        }
        return "source_span.text \"" + quote + "\" is on page " + (page + 1) + " but not inside the part of the document"
                + " this field must be quoted from (" + located.get().span().describe() + ": " + region
                + "); quote the value printed there";
    }

    /**
     * Why a numeric value is not a number its quote prints, or null when it is (or the value is
     * not a number). Numbers in the quote are read with "," as grouping and "." as the decimal
     * point, and compared by size: the sign is left to other checks, since documents print
     * negatives as "(1,203)", "-1,203" or "1,203 CR".
     */
    private String unquotedNumber(JsonNode evidence) {
        JsonNode value = evidence.get("value");
        if (!value.isNumber()) {
            return null;
        }
        BigDecimal wanted = value.decimalValue().abs();
        String quote = fold(evidence.get("source_span").get("text").asText());
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

    /** The text as grounding compares it, after the loosening the builder asked for. */
    private String normalize(String s) {
        String t = fold(s);
        t = ignoreWhitespace ? loosen(t) : t;
        return ignoreCurrency ? CURRENCY.matcher(t).replaceAll("") : t;
    }

    /** Typography and digit grouping folded, as the builder asked; nothing else changes. */
    private String fold(String s) {
        String t = ignoreGrouping ? GROUPING.matcher(s).replaceAll("") : s;
        return ignoreTypography ? Typography.fold(t) : t;
    }

    /**
     * A page's text as grounding compares it: one form, or with {@link Builder#joiningHyphenatedLines()}
     * two, as printed and with words broken across lines joined again. A quote may match either.
     */
    private List<String> page(String text) {
        String asIs = normalize(text);
        if (!joinHyphenatedLines) {
            return List.of(asIs);
        }
        String joined = normalize(INVISIBLE.matcher(LINE_END_HYPHEN.matcher(text).replaceAll("")).replaceAll(""));
        return joined.equals(asIs) ? List.of(asIs) : List.of(asIs, joined);
    }

    private static boolean contains(List<String> forms, String wanted) {
        return forms.stream().anyMatch(f -> f.contains(wanted));
    }

    /** What grounding ignored, for a message: empty when it compared literally. */
    private String ignored() {
        List<String> what = new ArrayList<>();
        if (ignoreWhitespace) {
            what.add("whitespace");
        }
        if (ignoreCurrency) {
            what.add("currency signs");
        }
        if (ignoreTypography) {
            what.add("typography");
        }
        if (ignoreGrouping) {
            what.add("digit grouping");
        }
        if (joinHyphenatedLines) {
            what.add("line-end hyphens");
        }
        if (what.isEmpty()) {
            return "";
        }
        String last = what.remove(what.size() - 1);
        return " (" + (what.isEmpty() ? last : String.join(", ", what) + " and " + last) + " ignored)";
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
        private boolean ignoreCurrency;
        private boolean ignoreTypography;
        private boolean joinHyphenatedLines;
        private boolean ignoreGrouping;
        private final List<Scope> scopes = new ArrayList<>();

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
         * With grounding, also ignore currency signs ($, €, £ and the rest) in both the quote and the
         * document, so a quote of "$ 37,794" grounds where the page prints "37,794". A model often
         * adds or drops the sign when it copies an amount; the digits still have to match. Without
         * it, that costs a rejection and a retry for an answer that is right.
         *
         * @return this builder
         */
        public Builder ignoringCurrencySigns() {
            this.ignoreCurrency = true;
            return this;
        }

        /**
         * With grounding, also fold typographic variants of the same character, in the quote and the
         * document alike: the ligatures ﬀ ﬁ ﬂ ﬃ ﬄ ﬅ ﬆ become their letters, full-width forms
         * ("１２３", "，") their ASCII characters, a decimal digit of any script ("٣", "३") its ASCII
         * digit, curly quotes and primes straight ones, "…" three dots, any no-break or fixed-width
         * space a plain space, and soft hyphens, zero-width spaces and joiners are dropped. PDF and OCR
         * text is full of these, and a model usually types the plain form. Nothing else is folded:
         * letters and digits that OCR confuses ("0" and "O", "1" and "l") must still match, and so
         * must case, superscripts and every other character.
         *
         * @return this builder
         */
        public Builder ignoringTypography() {
            this.ignoreTypography = true;
            return this;
        }

        /**
         * With grounding, also let a quote join a word the document breaks across lines with a
         * hyphen: "international" grounds where the page prints "inter-" at the end of one line and
         * "national" at the start of the next. Only a hyphen between a letter and a line break that is
         * followed by a lower-case letter is joined, so numbers, codes and capitalised names are never
         * joined. The page as printed still matches too, so "inter-national" (or the line break as
         * printed) grounds as before.
         *
         * @return this builder
         */
        public Builder joiningHyphenatedLines() {
            this.joinHyphenatedLines = true;
            return this;
        }

        /**
         * With grounding, also ignore digit-grouping marks, in the quote and the document alike: a
         * ",", apostrophe, or thin or narrow no-break space between a digit and exactly three more,
         * so "1234567", "1,234,567", "1'234'567" and "1 234 567" (with U+202F) all match each
         * other. "." is never folded, because it is a decimal point as often as a separator, and a
         * plain space is not, because it also separates table columns ("12 345" can be two cells). It
         * applies to {@link #numbersMatchQuotes()} too.
         *
         * @return this builder
         */
        public Builder ignoringDigitGrouping() {
            this.ignoreGrouping = true;
            return this;
        }

        /**
         * With grounding, also require that these fields' quotes lie inside {@code region} on the
         * page they name, not just somewhere on it. Use it when a document prints the same labels
         * or numbers in more than one place (a summary and a detail table, a parent company's
         * statement and a subsidiary's, this year's table and last year's), so a quote found on the
         * page can still come from the wrong one. With whitespace or currency signs ignored, they
         * are ignored here too. A quote that runs across the region's edge is outside it.
         *
         * @param region the part of the document the quotes must come from
         * @param paths fields declared with {@link #require} or {@link #optional}, written the same way
         * @return this builder
         */
        public Builder within(Region region, String... paths) {
            if (region == null || paths.length == 0) {
                throw new IllegalArgumentException("within needs a region and at least one field");
            }
            scopes.add(new Scope(region, Arrays.stream(paths).map(Paths::validate).toList()));
            return this;
        }

        /**
         * Builds the gate; a provenance gate that requires nothing would pass everything, so that is
         * an error, and so is a region with grounding off or on a field the gate doesn't check.
         *
         * @return the gate
         */
        public ProvenanceGate build() {
            if (paths.isEmpty()) {
                throw new IllegalStateException("ProvenanceGate needs at least one required field");
            }
            if (!scopes.isEmpty() && !grounded) {
                throw new IllegalStateException("within needs grounding: call grounded() or groundedIgnoringWhitespace()");
            }
            for (Scope scope : scopes) {
                for (String path : scope.paths()) {
                    if (!paths.contains(path) && !optionalPaths.contains(path)) {
                        throw new IllegalStateException("within names " + path + ", which is not a required or optional field");
                    }
                }
            }
            return new ProvenanceGate(this);
        }
    }
}
