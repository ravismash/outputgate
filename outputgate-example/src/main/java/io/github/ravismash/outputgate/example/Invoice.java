package io.github.ravismash.outputgate.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * The typed shape an invoice extraction must bind to ({@code TypeGate}).
 *
 * @param invoiceNumber e.g. {@code INV-2041}
 * @param invoiceDate ISO-8601 date
 * @param vendor seller name
 * @param currency ISO 4217 code
 * @param lineItems the lines
 * @param subtotal sum of line amounts
 * @param tax tax amount
 * @param total subtotal + tax
 */
public record Invoice(
        @JsonProperty("invoice_number") String invoiceNumber,
        @JsonProperty("invoice_date") String invoiceDate,
        @JsonProperty("vendor") String vendor,
        @JsonProperty("currency") String currency,
        @JsonProperty("line_items") List<LineItem> lineItems,
        @JsonProperty("subtotal") BigDecimal subtotal,
        @JsonProperty("tax") BigDecimal tax,
        @JsonProperty("total") BigDecimal total) {

    /**
     * One invoice line.
     *
     * @param description what was sold
     * @param quantity how many
     * @param unitPrice price per unit
     * @param amount quantity × unit price
     */
    public record LineItem(
            @JsonProperty("description") String description,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice,
            @JsonProperty("amount") BigDecimal amount) {}
}
