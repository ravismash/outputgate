package io.github.ravismash.outputgate.example;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.RegexGate;
import io.github.ravismash.outputgate.SchemaGate;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.TypeGate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * The invoice example: one contract that uses every content gate, and the synthetic
 * two-page source document it is grounded against. Pages in the document file are
 * separated by a form feed.
 */
public final class InvoiceExample {

    private static final String DOCUMENT = "documents/acme-invoice.txt";

    private InvoiceExample() {}

    /** @return the invoice contract: schema, type, math, provenance (grounded) and regex gates */
    public static Contract contract() {
        return Contract.builder()
                .gate(SchemaGate.fromClasspath("schemas/invoice.schema.json"))
                .gate(TypeGate.of(Invoice.class))
                .gate(MathGate.builder()
                        .sumEquals("line_items[*].amount", "subtotal")
                        .equalsSum("total", "subtotal", "tax")
                        .productEquals("line_items[*].amount", "line_items[*].quantity", "line_items[*].unit_price")
                        .tolerance(0.01)
                        .build())
                .gate(ProvenanceGate.builder()
                        .require("invoice_number", "invoice_date", "vendor", "subtotal", "tax", "total",
                                "line_items[*].amount")
                        .grounded()
                        .build())
                .gate(RegexGate.builder()
                        .require("invoice_number", "INV-\\d{4}")
                        .isoDate("invoice_date")
                        .currencyCode("currency")
                        .forbid("line_items[*].description", "(?i)\\b(sorry|apologi[sz]e|as an ai)\\b")
                        .build())
                .build();
    }

    /** @return the synthetic sample invoice, page by page */
    public static SourceDocument document() {
        ClassLoader loader = InvoiceExample.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(DOCUMENT)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + DOCUMENT);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> pages = Arrays.stream(text.split("\f")).map(String::strip).toList();
            return new SourceDocument(pages);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return a context carrying {@link #document()}, for grounding */
    public static EvalContext context() {
        return EvalContext.empty().withSource(document());
    }
}
