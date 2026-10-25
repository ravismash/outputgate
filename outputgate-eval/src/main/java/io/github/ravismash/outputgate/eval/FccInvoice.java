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
 * The real-invoice contract and extraction prompt for broadcast advertising invoices (the
 * FCC invoices in amazon/ConfBench). Net is gross minus every deduction printed on the
 * invoice (agency commission, rep commission, discounts), not just the agency commission:
 * on the real invoices a rep-firm commission is common, and the dataset's own ground truth
 * leaves it out.
 */
public final class FccInvoice {

    /** Largest difference still counted as equal in the money check. */
    static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    static final String INSTRUCTIONS = """
            Extract this broadcast advertising invoice as one JSON object and nothing else: no prose, no code fences.
            Fields:
              invoice_number: text, exactly as printed. Always a JSON string, even when it is all digits
                ("2112-1", "000451", "77123"), so nothing is lost.
              invoice_date (yyyy-MM-dd), advertiser,
              agency: the advertising agency that placed the order, usually printed just below or after the
                advertiser and product near the order number, or as the name in the billing address. If the invoice
                deducts an agency commission, an agency is printed: find it. It is never the advertiser itself, the station, its sales office, account
                executive or rep firm (names like "...Reps"). null only if no agency is printed at all.
              gross_total (the gross / airtime total before any deduction),
              deductions: a list of {"label": <text>, "amount": <evidence>}, one for every amount the invoice
                subtracts between the gross total and the net total (agency commission, rep or rep-firm commission,
                discounts). An empty list if nothing is subtracted.
              net_amount_due (the net total or balance due).
            Evidence: EVERY one of invoice_number, invoice_date, advertiser, agency (unless null), gross_total,
            net_amount_due and the amount inside every deduction is an object
            {"value": <the value>, "source_span": {"page": <page number>, "text": "<quote>"}}, never a bare string or
            number. Text fields need evidence too: "invoice_date": "2016-08-28" is wrong,
            "invoice_date": {"value": "2016-08-28", "source_span": {"page": 1, "text": "08/28/16"}} is right.
            A deduction's label is plain text: put source_span only on its amount, never on the deduction itself.
            source_span.page is the number in the "--- page N ---" line above the quote.
            source_span.text is a quote copied character for character from that page: one unbroken piece of the page
            text, with "$" signs, spaces and number formatting exactly as printed. Include the label only when the
            label sits directly before the value on the same line of the page text; when the label is in a table
            header or on another line, quote only the value itself (for example "2112-1" or "$15,750.00").
            Never quote a whole table row or several lines, and never write a label next to a value yourself.
            Never add or remove a space, colon or "$" sign: the example below shows the shape, not how any invoice prints.
            Money values are plain JSON numbers (2319.00, not "$2,319.00"). Only money values are numbers.
            Do not compute or guess a value that is not printed; leave the field out instead.
            Example of the shape (made-up values):
            {"invoice_number": {"value": "900123-1", "source_span": {"page": 1, "text": "900123-1"}},
             "invoice_date": {"value": "2012-10-21", "source_span": {"page": 1, "text": "10/21/12"}},
             "advertiser": {"value": "Example Committee", "source_span": {"page": 1, "text": "Example Committee"}},
             "agency": null,
             "gross_total": {"value": 2319.00, "source_span": {"page": 2, "text": "$2,319.00"}},
             "deductions": [{"label": "Agency Commission",
                             "amount": {"value": 347.85, "source_span": {"page": 2, "text": "347.85"}}}],
             "net_amount_due": {"value": 1971.15, "source_span": {"page": 2, "text": "$1,971.15"}}}
            """;

    /** Prose, apologies and placeholders that are never a real name or invoice number. */
    static final String NOT_A_VALUE = "(?i)\\b(sorry|unknown|n/a|not (found|printed|available)|as an ai)\\b";

    /** An invoice number: one token of letters, digits and . / # - with at least one digit; no spaces or prose. */
    static final String INVOICE_NUMBER = "(?=.*\\d)[A-Za-z0-9][A-Za-z0-9./#-]{0,39}";

    private FccInvoice() {}

    /** The typed shape of an extraction. */
    record Extraction(
            @JsonProperty("invoice_number") String invoiceNumber,
            @JsonProperty("invoice_date") String invoiceDate,
            @JsonProperty("advertiser") String advertiser,
            @JsonProperty("agency") String agency,
            @JsonProperty("gross_total") BigDecimal grossTotal,
            @JsonProperty("deductions") List<Deduction> deductions,
            @JsonProperty("net_amount_due") BigDecimal netAmountDue) {

        record Deduction(@JsonProperty("label") String label, @JsonProperty("amount") BigDecimal amount) {}
    }

    /** @return the contract: schema, type, math (gross − deductions = net), grounded provenance, regex, agency */
    public static Contract contract() {
        return Contract.builder()
                .gate(SchemaGate.fromClasspath("schemas/fcc-invoice.schema.json"))
                .gate(TypeGate.of(Extraction.class))
                .gate(MathGate.builder()
                        .custom("gross_total - sum(deductions) = net_amount_due (tolerance 0.01)", n -> {
                            BigDecimal deductions = n.all("deductions[*].amount").stream()
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal diff = n.get("gross_total").subtract(deductions).subtract(n.get("net_amount_due"));
                            return diff.abs().compareTo(TOLERANCE) <= 0;
                        })
                        .build())
                .gate(ProvenanceGate.builder()
                        .require("invoice_number", "invoice_date", "advertiser", "gross_total", "net_amount_due",
                                "deductions[*].amount")
                        .optional("agency")
                        .groundedIgnoringWhitespace()
                        .build())
                .gate(RegexGate.builder()
                        .isoDate("invoice_date")
                        .require("invoice_number", INVOICE_NUMBER)
                        .forbid("invoice_number", NOT_A_VALUE)
                        .forbid("advertiser", NOT_A_VALUE)
                        .forbid("agency", NOT_A_VALUE)
                        .build())
                .gate(new AgencyGate())
                .build();
    }

    /**
     * The answer's shape as a JSON Schema, for a client that can constrain output (Ollama's
     * {@code format}): every evidence field as {value, source_span}. It only steers the model;
     * {@link #contract()} still judges every answer, so nothing it allows is trusted.
     *
     * @return the schema as JSON text
     */
    public static String outputSchema() {
        ObjectMapper json = new ObjectMapper();
        ObjectNode root = json.createObjectNode().put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.set("invoice_number", Evidence.schema(json, "string"));
        props.set("invoice_date", Evidence.schema(json, "string"));
        props.set("advertiser", Evidence.schema(json, "string"));
        ObjectNode agency = props.putObject("agency");
        agency.putArray("anyOf").add(Evidence.schema(json, "string")).addObject().put("type", "null");
        props.set("gross_total", Evidence.schema(json, "number"));
        ObjectNode deduction = json.createObjectNode().put("type", "object");
        ObjectNode deductionProps = deduction.putObject("properties");
        deductionProps.putObject("label").put("type", "string");
        deductionProps.set("amount", Evidence.schema(json, "number"));
        deduction.putArray("required").add("label").add("amount");
        props.putObject("deductions").put("type", "array").set("items", deduction);
        props.set("net_amount_due", Evidence.schema(json, "number"));
        ArrayNode required = root.putArray("required");
        props.fieldNames().forEachRemaining(required::add);
        return root.toString();
    }

    /** @param document the invoice's pages @return the full extraction prompt */
    public static String prompt(SourceDocument document) {
        return Evidence.withPages(INSTRUCTIONS, document);
    }
}
