package io.github.ravismash.outputgate.example;

import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.LlmOutput;
import io.github.ravismash.outputgate.SourceDocument;

/** Asks a model to extract an invoice, with evidence for every value. The contract judges the answer. */
public final class InvoiceExtractor {

    static final String INSTRUCTIONS = """
            Extract the invoice below as one JSON object and nothing else: no prose, no code fences.
            Fields: invoice_number, invoice_date (yyyy-MM-dd), vendor, currency (ISO 4217),
            line_items [{description, quantity, unit_price, amount}], subtotal, tax, total.
            For invoice_number, invoice_date, vendor, subtotal, tax, total and every line amount, write
            {"value": <the value>, "source_span": {"page": <page number>, "text": "<exact text copied from that page>"}}.
            Copy source_span.text character for character from the document. If a value is not in the
            document, leave the field out rather than guess.
            """;

    private final LlmClient client;

    /** @param client the model to ask; {@code FakeLlmClient} for offline tests */
    public InvoiceExtractor(LlmClient client) {
        this.client = client;
    }

    /**
     * @param document the invoice, page by page
     * @return the model's raw answer and usage, to be checked by the contract
     */
    public LlmOutput extract(SourceDocument document) {
        StringBuilder prompt = new StringBuilder(INSTRUCTIONS).append('\n');
        for (int i = 0; i < document.pages().size(); i++) {
            prompt.append("--- page ").append(i + 1).append(" ---\n").append(document.pages().get(i)).append('\n');
        }
        return client.complete(prompt.toString());
    }
}
