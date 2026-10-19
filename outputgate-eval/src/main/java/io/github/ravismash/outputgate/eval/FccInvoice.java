package io.github.ravismash.outputgate.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
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
              invoice_number, invoice_date (yyyy-MM-dd), advertiser, agency (null if none is printed),
              gross_total (the gross / airtime total before any deduction),
              deductions: a list of {label, amount}, one for every amount the invoice subtracts between the
                gross total and the net total (agency commission, rep or rep-firm commission, discounts).
                An empty list if nothing is subtracted.
              net_amount_due (the net total or balance due).
            For invoice_number, invoice_date, advertiser, agency, gross_total, net_amount_due and every deduction
            amount, write {"value": <the value>, "source_span": {"page": <page number>, "text": "<text>"}}.
            source_span.text must be copied character for character from that page, including spaces, "$" signs
            and number formatting exactly as printed. Values are plain numbers (2319.00, not "$2,319.00").
            Do not compute or guess a value that is not printed; leave the field out instead.
            """;

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

    /** @return the contract: schema, type, math (gross − deductions = net), grounded provenance, regex */
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
                        .grounded()
                        .build())
                .gate(RegexGate.builder()
                        .isoDate("invoice_date")
                        .forbid("advertiser", "(?i)\\b(sorry|unknown|n/a|as an ai)\\b")
                        .build())
                .build();
    }

    /** @param document the invoice's pages @return the full extraction prompt */
    public static String prompt(SourceDocument document) {
        StringBuilder p = new StringBuilder(INSTRUCTIONS).append('\n');
        for (int i = 0; i < document.pages().size(); i++) {
            p.append("--- page ").append(i + 1).append(" ---\n").append(document.pages().get(i)).append('\n');
        }
        return p.toString();
    }
}
