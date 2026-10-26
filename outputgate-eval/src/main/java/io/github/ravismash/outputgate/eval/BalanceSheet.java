package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.RegexGate;
import io.github.ravismash.outputgate.SchemaGate;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.TypeGate;
import java.math.BigDecimal;
import java.util.List;

/**
 * The contract and extraction prompt for a company balance sheet: the statement page of a SEC
 * 10-Q as EDGAR's XBRL viewer prints it (see {@code scripts/fetch_sec_balance_sheets.py}).
 * The statement has two columns, the latest period and a comparison period; the extraction
 * reads the latest. Amounts are taken as printed (in the statement's own units, usually
 * millions), so every one can be quoted.
 */
public final class BalanceSheet {

    /**
     * Largest difference still counted as equal. Statements print each line rounded to the unit,
     * so a printed total can differ from the sum of its printed lines by 1.
     */
    static final BigDecimal TOLERANCE = BigDecimal.ONE;

    static final String INSTRUCTIONS = """
            Extract this balance sheet as one JSON object and nothing else: no prose, no code fences.
            The statement has a column per period. Use only the FIRST amount column (the latest period).
            Fields:
              period_end: the latest period's date as yyyy-MM-dd (the first date in the heading).
              current_assets: the lines that add up to the total current assets, as a list of
                {"label": <text>, "amount": <evidence>}. When a group of lines has its own subtotal inside
                current assets (for example "Total inventories"), list the subtotal and not the lines in it,
                so the amounts add up to the total exactly once.
              total_current_assets, total_assets.
              current_liabilities: the lines that add up to the total current liabilities, same rules.
              total_current_liabilities.
              total_liabilities_and_equity: the last line, total liabilities and (stockholders' or shareholders') equity.
            Evidence: period_end and every amount (inside each line too) is an object
            {"value": <the value>, "source_span": {"page": <page number>, "text": "<quote>"}}, never a bare value.
            A line's label is plain text, copied from the statement: put source_span only on its amount.
            source_span.page is the number in the "--- page N ---" line above the quote.
            source_span.text is the amount copied character for character from the statement, with any "$",
            spaces, commas and parentheses exactly as printed (for example "$ 39,544", "22,855" or "(1,203)").
            Quote only the amount itself, never its label or the other column. For period_end, quote the date as
            printed in the heading (for example "Jun. 27, 2026").
            Amounts are plain JSON numbers in the statement's own units, not scaled: "$ 39,544" is 39544.
            An amount in parentheses is negative: "(1,203)" is -1203.
            Do not compute or guess a value that is not printed.
            Example of the shape (made-up values):
            {"period_end": {"value": "2026-06-30", "source_span": {"page": 1, "text": "Jun. 30, 2026"}},
             "current_assets": [
               {"label": "Cash and cash equivalents", "amount": {"value": 1200, "source_span": {"page": 1, "text": "$ 1,200"}}},
               {"label": "Inventories", "amount": {"value": 800, "source_span": {"page": 1, "text": "800"}}}],
             "total_current_assets": {"value": 2000, "source_span": {"page": 1, "text": "2,000"}},
             "total_assets": {"value": 5000, "source_span": {"page": 1, "text": "5,000"}},
             "current_liabilities": [
               {"label": "Accounts payable", "amount": {"value": 900, "source_span": {"page": 1, "text": "900"}}}],
             "total_current_liabilities": {"value": 900, "source_span": {"page": 1, "text": "900"}},
             "total_liabilities_and_equity": {"value": 5000, "source_span": {"page": 1, "text": "$ 5,000"}}}
            """;

    /** Prose, apologies and placeholders that are never a line's label. */
    static final String NOT_A_LABEL = "(?i)\\b(sorry|unknown|n/a|not (found|printed|available)|as an ai)\\b";

    private BalanceSheet() {}

    /** The typed shape of an extraction. */
    record Extraction(
            @JsonProperty("period_end") String periodEnd,
            @JsonProperty("current_assets") List<Line> currentAssets,
            @JsonProperty("total_current_assets") BigDecimal totalCurrentAssets,
            @JsonProperty("total_assets") BigDecimal totalAssets,
            @JsonProperty("current_liabilities") List<Line> currentLiabilities,
            @JsonProperty("total_current_liabilities") BigDecimal totalCurrentLiabilities,
            @JsonProperty("total_liabilities_and_equity") BigDecimal totalLiabilitiesAndEquity) {

        record Line(@JsonProperty("label") String label, @JsonProperty("amount") BigDecimal amount) {}
    }

    /**
     * @return the contract: schema, type, math (lines add up to their totals, assets equal
     *     liabilities and equity, current within total), grounded provenance with every amount
     *     equal to the number its quote prints, ISO date, labels
     */
    public static Contract contract() {
        return Contract.builder()
                .gate(SchemaGate.fromClasspath("schemas/balance-sheet.schema.json"))
                .gate(TypeGate.of(Extraction.class))
                .gate(MathGate.builder()
                        .sumEquals("current_assets[*].amount", "total_current_assets")
                        .sumEquals("current_liabilities[*].amount", "total_current_liabilities")
                        .custom("total_assets = total_liabilities_and_equity", n ->
                                n.get("total_assets").compareTo(n.get("total_liabilities_and_equity")) == 0)
                        .custom("total_current_assets <= total_assets", n ->
                                n.get("total_current_assets").compareTo(n.get("total_assets")) <= 0)
                        .custom("total_current_liabilities <= total_liabilities_and_equity", n ->
                                n.get("total_current_liabilities").compareTo(n.get("total_liabilities_and_equity")) <= 0)
                        .tolerance(TOLERANCE)
                        .build())
                .gate(ProvenanceGate.builder()
                        .require("period_end", "current_assets[*].amount", "total_current_assets", "total_assets",
                                "current_liabilities[*].amount", "total_current_liabilities", "total_liabilities_and_equity")
                        .groundedIgnoringWhitespace()
                        .numbersMatchQuotes()
                        .build())
                .gate(RegexGate.builder()
                        .isoDate("period_end")
                        .forbid("current_assets[*].label", NOT_A_LABEL)
                        .forbid("current_liabilities[*].label", NOT_A_LABEL)
                        .build())
                .build();
    }

    /**
     * The answer's shape as a JSON Schema, for a client that can constrain output. It only
     * steers the model; {@link #contract()} still judges every answer.
     *
     * @return the schema as JSON text
     */
    public static String outputSchema() {
        ObjectMapper json = new ObjectMapper();
        ObjectNode root = json.createObjectNode().put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.set("period_end", Evidence.schema(json, "string"));
        props.set("current_assets", lines(json));
        props.set("total_current_assets", Evidence.schema(json, "number"));
        props.set("total_assets", Evidence.schema(json, "number"));
        props.set("current_liabilities", lines(json));
        props.set("total_current_liabilities", Evidence.schema(json, "number"));
        props.set("total_liabilities_and_equity", Evidence.schema(json, "number"));
        ArrayNode required = root.putArray("required");
        props.fieldNames().forEachRemaining(required::add);
        return root.toString();
    }

    private static ObjectNode lines(ObjectMapper json) {
        ObjectNode line = json.createObjectNode().put("type", "object");
        ObjectNode p = line.putObject("properties");
        p.putObject("label").put("type", "string");
        p.set("amount", Evidence.schema(json, "number"));
        line.putArray("required").add("label").add("amount");
        ObjectNode list = json.createObjectNode().put("type", "array");
        list.set("items", line);
        return list;
    }

    /** @param document the statement's pages @return the full extraction prompt */
    public static String prompt(SourceDocument document) {
        return Evidence.withPages(INSTRUCTIONS, document);
    }
}
