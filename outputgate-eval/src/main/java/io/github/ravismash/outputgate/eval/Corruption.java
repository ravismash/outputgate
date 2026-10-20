package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.SourceDocument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One way to break a correct extraction, modelled on mistakes models make. Kinds work on any
 * output shape: they look for evidence objects ({@code {value, source_span: {page, text}}}),
 * arrays and required fields wherever they are, so the same kinds apply to invoices and
 * receipts alike. A kind applies only where the break is real: a corruption the source document
 * would still support (a "reformatted" quote that is also printed on the page) is not produced.
 */
public enum Corruption {

    DROPPED_ITEM("dropped-item", "one element of a list left out (a line item, a deduction), totals kept") {
        @Override
        Optional<String> mutate(Target t) {
            List<Element> items = new ArrayList<>();
            walk(t.out, "", (path, node) -> {
                if (node instanceof ArrayNode list) {
                    for (int i = 0; i < list.size(); i++) {
                        if (!allZero(list.get(i))) {
                            items.add(new Element(path, list, i));
                        }
                    }
                }
            });
            if (items.isEmpty()) {
                return Optional.empty();
            }
            Element e = items.get(t.rnd.nextInt(items.size()));
            e.list.remove(e.index);
            return Optional.of("removed " + e.path + "[" + e.index + "]");
        }
    },

    NUMBER_TYPO("number-typo", "two adjacent digits of a quoted amount swapped, quote kept") {
        @Override
        Optional<String> mutate(Target t) {
            List<Field> numbers = evidence(t.out, ev -> ev.get("value").isNumber() && swaps(plain(ev)).size() > 0);
            if (numbers.isEmpty()) {
                return Optional.empty();
            }
            Field f = numbers.get(t.rnd.nextInt(numbers.size()));
            String was = plain(f.ev);
            List<Integer> swaps = swaps(was);
            int i = swaps.get(t.rnd.nextInt(swaps.size()));
            String typo = was.substring(0, i) + was.charAt(i + 1) + was.charAt(i) + was.substring(i + 2);
            f.ev.put("value", new BigDecimal(typo));
            return Optional.of(f.path + " " + was + " -> " + typo);
        }
    },

    REFORMATTED_QUOTE("reformatted-quote", "an amount's quote re-typed with different money formatting") {
        @Override
        Optional<String> mutate(Target t) {
            List<Field> quotes = evidence(t.out, ev -> MONEY.matcher(spanText(ev)).find());
            while (!quotes.isEmpty()) {
                Field f = quotes.remove(t.rnd.nextInt(quotes.size()));
                String text = spanText(f.ev);
                Matcher m = MONEY.matcher(text);
                m.find();
                BigDecimal amount = new BigDecimal(m.group(2).replace(",", ""));
                for (String variant : moneyVariants(amount)) {
                    String changed = text.substring(0, m.start()) + variant + text.substring(m.end());
                    if (!variant.equals(m.group()) && !inDocument(t.doc, changed)) {
                        span(f.ev).put("text", changed);
                        return Optional.of(f.path + " quote \"" + text + "\" -> \"" + changed + "\"");
                    }
                }
            }
            return Optional.empty();
        }
    },

    FABRICATED_QUOTE("fabricated-quote", "a text field quoted with words that are not in the document") {
        @Override
        Optional<String> mutate(Target t) {
            List<Field> texts = evidence(t.out, ev -> ev.get("value").isTextual()
                    && !inDocument(t.doc, "Billed to: " + ev.get("value").asText()));
            if (texts.isEmpty()) {
                return Optional.empty();
            }
            Field f = texts.get(t.rnd.nextInt(texts.size()));
            String was = spanText(f.ev);
            String made = "Billed to: " + f.ev.get("value").asText();
            span(f.ev).put("text", made);
            return Optional.of(f.path + " quote \"" + was + "\" -> \"" + made + "\"");
        }
    },

    WRONG_PAGE("wrong-page", "a quote attributed to a page it is not on") {
        @Override
        Optional<String> mutate(Target t) {
            int pages = t.doc.pages().size();
            List<Field> spans = evidence(t.out, ev -> span(ev).path("page").canConvertToInt()
                    && !onPage(t.doc, other(span(ev).path("page").asInt(), pages), spanText(ev)));
            if (spans.isEmpty()) {
                return Optional.empty();
            }
            Field f = spans.get(t.rnd.nextInt(spans.size()));
            int page = span(f.ev).path("page").asInt();
            int target = other(page, pages);
            span(f.ev).put("page", target);
            return Optional.of(f.path + " page " + page + " -> " + target + " (of " + pages + ")");
        }
    },

    BARE_VALUE("bare-value", "a field given as a bare value, without its quote") {
        @Override
        Optional<String> mutate(Target t) {
            List<Field> fields = evidence(t.out, ev -> true);
            if (fields.isEmpty()) {
                return Optional.empty();
            }
            Field f = fields.get(t.rnd.nextInt(fields.size()));
            if (f.parent instanceof ObjectNode o) {
                o.set(f.key, f.ev.get("value"));
            } else {
                ((ArrayNode) f.parent).set(f.index, f.ev.get("value"));
            }
            return Optional.of(f.path + " evidence dropped");
        }
    },

    NON_ISO_DATE("non-iso-date", "a date left as printed (MM/dd/yy) instead of ISO") {
        @Override
        Optional<String> mutate(Target t) {
            List<Field> dates = evidence(t.out, ev -> ISO_DATE.matcher(ev.get("value").asText()).matches());
            if (dates.isEmpty()) {
                return Optional.empty();
            }
            Field f = dates.get(t.rnd.nextInt(dates.size()));
            Matcher m = ISO_DATE.matcher(f.ev.get("value").asText());
            m.matches();
            String printed = m.group(2) + "/" + m.group(3) + "/" + m.group(1).substring(2);
            f.ev.put("value", printed);
            return Optional.of(f.path + " " + m.group() + " -> " + printed);
        }
    },

    APOLOGY("apology", "the model apologises inside a text field") {
        @Override
        Optional<String> mutate(Target t) {
            List<Field> texts = evidence(t.out, ev -> ev.get("value").isTextual()
                    && !ISO_DATE.matcher(ev.get("value").asText()).matches());
            if (texts.isEmpty()) {
                return Optional.empty();
            }
            Field f = texts.get(t.rnd.nextInt(texts.size()));
            f.ev.put("value", "Sorry, I could not read this field");
            return Optional.of(f.path + " replaced with an apology");
        }
    },

    AMOUNT_AS_TEXT("amount-as-text", "an amount written as text (\"$2,319.00\") instead of a number") {
        @Override
        Optional<String> mutate(Target t) {
            List<Field> numbers = evidence(t.out, ev -> ev.get("value").isNumber());
            if (numbers.isEmpty()) {
                return Optional.empty();
            }
            Field f = numbers.get(t.rnd.nextInt(numbers.size()));
            String text = "$" + grouped(f.ev.get("value").decimalValue());
            f.ev.put("value", text);
            return Optional.of(f.path + " -> \"" + text + "\"");
        }
    },

    MISSING_FIELD("missing-field", "a required field left out") {
        @Override
        Optional<String> mutate(Target t) {
            List<String> present = t.required.stream().filter(t.out::has).toList();
            if (present.isEmpty()) {
                return Optional.empty();
            }
            String field = present.get(t.rnd.nextInt(present.size()));
            t.out.remove(field);
            return Optional.of(field + " removed");
        }
    },

    EXTRA_FIELD("extra-field", "an unrequested field with the model's commentary") {
        @Override
        Optional<String> mutate(Target t) {
            if (t.out.has("notes")) {
                return Optional.empty();
            }
            t.out.put("notes", "One amount was not clearly shown; assumed zero.");
            return Optional.of("added \"notes\"");
        }
    },

    CODE_FENCE("code-fence", "the JSON wrapped in a sentence and a ```json fence") {
        @Override
        Optional<String> text(String json) {
            return Optional.of("Here is the extracted data:\n```json\n" + json + "\n```");
        }
    },

    TRAILING_NOTE("trailing-note", "a note after the JSON object") {
        @Override
        Optional<String> text(String json) {
            return Optional.of(json + "\n\nNote: one amount was hard to read, so please double-check it.");
        }
    };

    /** A corrupted output and what was changed. */
    public record Result(String text, String detail) {}

    static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    /** An amount as printed, with an optional "$" or "$ " before it. */
    private static final Pattern MONEY = Pattern.compile("(\\$\\s?)?(\\d[\\d,]*\\.\\d{2})(?!\\d)");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    private final String id;
    private final String description;

    Corruption(String id, String description) {
        this.id = id;
        this.description = description;
    }

    /** @return the kind's short name, as used in file names and the report */
    public String id() {
        return id;
    }

    /** @return what the kind changes, in a few words */
    public String description() {
        return description;
    }

    /**
     * Breaks one correct extraction.
     *
     * @param cleanOutput an extraction the full contract accepts
     * @param doc the document it was extracted from
     * @param required the top-level fields the task's schema requires
     * @param rnd source of choices (which field, which digits)
     * @return the corrupted output, or empty when this kind does not apply to this extraction
     */
    public Optional<Result> apply(String cleanOutput, SourceDocument doc, List<String> required, Random rnd) {
        JsonNode parsed;
        try {
            parsed = MAPPER.readTree(cleanOutput);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (!(parsed instanceof ObjectNode out)) {
            return Optional.empty();
        }
        Optional<String> wrapped = text(out.toPrettyString());
        if (wrapped.isPresent()) {
            return Optional.of(new Result(wrapped.get(), description));
        }
        ObjectNode copy = out.deepCopy();
        return mutate(new Target(copy, doc, required, rnd)).map(detail -> new Result(copy.toPrettyString(), detail));
    }

    /** Changes the parsed output in place; empty when the kind does not apply. */
    Optional<String> mutate(Target target) {
        return Optional.empty();
    }

    /** Wraps the serialised output; empty for kinds that change fields instead. */
    Optional<String> text(String json) {
        return Optional.empty();
    }

    /** @param id a kind's short name @return the kind */
    public static Corruption byId(String id) {
        for (Corruption c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        throw new IllegalArgumentException("unknown corruption kind: " + id);
    }

    record Target(ObjectNode out, SourceDocument doc, List<String> required, Random rnd) {}

    /** An evidence object, where it sits and how to replace it. */
    private record Field(String path, JsonNode parent, String key, int index, ObjectNode ev) {}

    private record Element(String path, ArrayNode list, int index) {}

    private interface Visitor {
        void visit(String path, JsonNode node);
    }

    private static void walk(JsonNode node, String path, Visitor v) {
        v.visit(path, node);
        if (node instanceof ObjectNode o) {
            o.properties().forEach(e -> walk(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), v));
        } else if (node instanceof ArrayNode a) {
            for (int i = 0; i < a.size(); i++) {
                walk(a.get(i), path + "[" + i + "]", v);
            }
        }
    }

    /** Every evidence object in the output that {@code keep} accepts, in document order. */
    private static List<Field> evidence(ObjectNode out, Predicate<ObjectNode> keep) {
        List<Field> found = new ArrayList<>();
        walk(out, "", (path, node) -> {
            if (node instanceof ObjectNode o) {
                o.properties().forEach(e -> add(found, path.isEmpty() ? e.getKey() : path + "." + e.getKey(), o, e.getKey(), -1, e.getValue(), keep));
            } else if (node instanceof ArrayNode a) {
                for (int i = 0; i < a.size(); i++) {
                    add(found, path + "[" + i + "]", a, null, i, a.get(i), keep);
                }
            }
        });
        return found;
    }

    private static void add(List<Field> found, String path, JsonNode parent, String key, int index, JsonNode node,
            Predicate<ObjectNode> keep) {
        if (node instanceof ObjectNode ev && ev.has("value") && span(ev) != null && keep.test(ev)) {
            found.add(new Field(path, parent, key, index, ev));
        }
    }

    private static ObjectNode span(JsonNode evidence) {
        return evidence.get("source_span") instanceof ObjectNode s && s.path("text").isTextual() ? s : null;
    }

    private static String spanText(ObjectNode ev) {
        return span(ev).path("text").asText();
    }

    private static boolean allZero(JsonNode node) {
        List<BigDecimal> numbers = new ArrayList<>();
        walk(node, "", (path, n) -> {
            if (n.isNumber()) {
                numbers.add(n.decimalValue());
            }
        });
        return !numbers.isEmpty() && numbers.stream().allMatch(n -> n.signum() == 0);
    }

    private static String plain(ObjectNode ev) {
        return ev.get("value").decimalValue().toPlainString();
    }

    /** Positions i where digits i and i+1 differ; swapping them always changes the value by at least 0.09 of its last place. */
    private static List<Integer> swaps(String plain) {
        List<Integer> swaps = new ArrayList<>();
        for (int i = 0; i + 1 < plain.length(); i++) {
            char a = plain.charAt(i);
            char b = plain.charAt(i + 1);
            if (Character.isDigit(a) && Character.isDigit(b) && a != b) {
                swaps.add(i);
            }
        }
        return swaps;
    }

    private static int other(int page, int pages) {
        return pages == 1 ? 2 : page % pages + 1;
    }

    /** Whether any page prints {@code text}, counting any run of whitespace as one space, so no near-copy counts as a break. */
    private static boolean inDocument(SourceDocument doc, String text) {
        String t = squash(text);
        return doc.pages().stream().anyMatch(p -> squash(p).contains(t));
    }

    private static boolean onPage(SourceDocument doc, int page, String text) {
        return page >= 1 && page <= doc.pages().size() && squash(doc.pages().get(page - 1)).contains(squash(text));
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    private static String grouped(BigDecimal amount) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }

    /** The ways documents print an amount: "$2,319.00", "$ 2319.00", "$2319.00", "$ 2,319.00", "2,319.00", "2319.00". */
    private static List<String> moneyVariants(BigDecimal amount) {
        String plain = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String grouped = grouped(amount);
        return List.of("$" + grouped, "$ " + plain, "$" + plain, "$ " + grouped, grouped, plain);
    }
}
