package io.github.ravismash.outputgate.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.junit.LlmAssert;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A synthetic invoice in the same style as the real ones; no real invoice text is checked in. */
class FccInvoiceTest {

    static final SourceDocument DOC = SourceDocument.ofPages(
            "WXYZ-TV\nINVOICE\nInvoice # 900123-1   Invoice Date 10/21/12\nAdvertiser: Example Campaign Committee\nAgency: Example Media Group",
            "AIRTIME TOTAL : $ 2319.00\nAgency Commission : $ 347.85\nRep Commission : $ 256.25\nNET TOTAL: : $ 1714.90");

    static final EvalContext CTX = EvalContext.empty().withSource(DOC);

    static String ev(String value, int page, String text) {
        return "{\"value\": " + value + ", \"source_span\": {\"page\": " + page + ", \"text\": \"" + text + "\"}}";
    }

    static String output(String gross, String grossSpan, String deductions, String net, String date) {
        return "{\"invoice_number\": " + ev("\"900123-1\"", 1, "Invoice # 900123-1")
                + ", \"invoice_date\": " + ev("\"" + date + "\"", 1, "Invoice Date 10/21/12")
                + ", \"advertiser\": " + ev("\"Example Campaign Committee\"", 1, "Advertiser: Example Campaign Committee")
                + ", \"agency\": " + ev("\"Example Media Group\"", 1, "Agency: Example Media Group")
                + ", \"gross_total\": " + ev(gross, 2, grossSpan)
                + ", \"deductions\": [" + deductions + "]"
                + ", \"net_amount_due\": " + ev(net, 2, "NET TOTAL: : $ 1714.90") + "}";
    }

    static final String BOTH = "{\"label\": \"Agency Commission\", \"amount\": " + ev("347.85", 2, "Agency Commission : $ 347.85") + "},"
            + "{\"label\": \"Rep Commission\", \"amount\": " + ev("256.25", 2, "Rep Commission : $ 256.25") + "}";
    static final String AGENCY_ONLY = "{\"label\": \"Agency Commission\", \"amount\": " + ev("347.85", 2, "Agency Commission : $ 347.85") + "}";

    @Test
    @DisplayName("a faithful extraction with both deductions passes")
    void faithfulPasses() {
        Verdict v = FccInvoice.contract().evaluate(output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21"), CTX);
        assertTrue(v.passed(), v::toString);
    }

    @Test
    @DisplayName("dropping the rep commission (as the dataset's own ground truth does) is caught by math")
    void missingDeductionCaught() {
        LlmAssert.assertThat(output("2319.00", "AIRTIME TOTAL : $ 2319.00", AGENCY_ONLY, "1714.90", "2012-10-21"))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("math");
    }

    @Test
    @DisplayName("a quote reformatted from '$ 2319.00' to '$2,319.00' is not grounded")
    void reformattedQuoteRejected() {
        LlmAssert.assertThat(output("2319.00", "AIRTIME TOTAL : $2,319.00", BOTH, "1714.90", "2012-10-21"))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("provenance");
    }

    @Test
    @DisplayName("a faithful quote that the PDF text breaks across lines still grounds")
    void quoteAcrossLineBreakGrounds() {
        String json = output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21")
                .replace("\"text\": \"Advertiser: Example Campaign Committee\"",
                        "\"text\": \"Advertiser: Example Campaign Committee Agency: Example Media Group\"");
        assertTrue(json.contains("Committee Agency:"), "the advertiser quote now crosses the line break");
        Verdict v = FccInvoice.contract().evaluate(json, CTX);
        assertTrue(v.passed(), v::toString);
    }

    @Test
    @DisplayName("an invoice number written as a JSON number is rejected by the schema")
    void numericInvoiceNumberRejected() {
        String json = output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21")
                .replace("\"value\": \"900123-1\"", "\"value\": 900123");
        LlmAssert.assertThat(json).withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("schema");
    }

    @Test
    @DisplayName("the prompt says invoice_number is always a string and spans go on deduction amounts only")
    void promptRules() {
        String p = FccInvoice.prompt(DOC);
        assertTrue(p.contains("Always a JSON string"), p);
        assertTrue(p.contains("put source_span only on its amount"), p);
        assertTrue(p.contains("Never quote a whole") && p.contains("table row"), p);
    }

    @Test
    @DisplayName("a real quote cited on the wrong page is not grounded")
    void wrongPageRejected() {
        String json = output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21")
                .replace("{\"page\": 2, \"text\": \"AIRTIME TOTAL", "{\"page\": 1, \"text\": \"AIRTIME TOTAL");
        LlmAssert.assertThat(json).withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("provenance");
    }

    @Test
    @DisplayName("agency needs grounded evidence when present, and may be null when none is printed")
    void agencyGrounded() {
        String good = output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21");
        String agency = ev("\"Example Media Group\"", 1, "Agency: Example Media Group");
        LlmAssert.assertThat(good.replace(agency, "\"Example Media Group\""))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("provenance");
        LlmAssert.assertThat(good.replace(agency, ev("\"Example Media Group\"", 1, "Agency: Sample Media")))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("provenance");
        Verdict none = FccInvoice.contract().evaluate(
                good.replace(agency, "null").replace("\"label\": \"Agency Commission\"", "\"label\": \"Discount\""), CTX);
        assertTrue(none.passed(), none::toString);
    }

    @Test
    @DisplayName("a dropped agency is rejected when an agency commission is deducted")
    void droppedAgencyRejected() {
        String good = output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21");
        String agency = ev("\"Example Media Group\"", 1, "Agency: Example Media Group");
        LlmAssert.assertThat(good.replace(agency, "null"))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("agency");
        LlmAssert.assertThat(good.replace(", \"agency\": " + agency, ""))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("agency");
    }

    @Test
    @DisplayName("an agency that is the advertiser, or a rep firm, is rejected even though its span is real")
    void wrongPartyAsAgencyRejected() {
        SourceDocument doc = SourceDocument.ofPages(
                DOC.pages().get(0) + "\nAccount Executive: Example CoxReps", DOC.pages().get(1));
        EvalContext ctx = EvalContext.empty().withSource(doc);
        String good = output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21");
        String agency = ev("\"Example Media Group\"", 1, "Agency: Example Media Group");
        LlmAssert.assertThat(good.replace(agency, ev("\"Example Campaign Committee\"", 1, "Example Campaign Committee")))
                .withContext(ctx).isRejectedBy(FccInvoice.contract()).byGate("agency");
        LlmAssert.assertThat(good.replace(agency, ev("\"Example CoxReps\"", 1, "Example CoxReps")))
                .withContext(ctx).isRejectedBy(FccInvoice.contract()).byGate("agency");
        Verdict v = FccInvoice.contract().evaluate(good, ctx);
        assertTrue(v.passed(), v::toString);
    }

    @Test
    @DisplayName("prose or an apology in invoice_number is rejected by regex, even when the span is real")
    void proseInvoiceNumberRejected() {
        String good = output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "2012-10-21");
        for (String bad : List.of("Sorry, I could not find it", "unknown", "INVOICE")) {
            LlmAssert.assertThat(good.replace("\"value\": \"900123-1\"", "\"value\": \"" + bad + "\""))
                    .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("regex");
        }
    }

    @Test
    @DisplayName("the output schema asks for {value, source_span} on every evidence field, and a faithful answer fits it")
    void outputSchemaShape() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode schema = json.readTree(FccInvoice.outputSchema());
        for (String field : List.of("invoice_number", "invoice_date", "advertiser", "gross_total", "net_amount_due")) {
            JsonNode f = schema.path("properties").path(field);
            assertEquals("[\"value\",\"source_span\"]", f.path("required").toString(), field);
        }
        assertEquals("[\"value\",\"source_span\"]",
                schema.path("properties").path("deductions").path("items").path("properties").path("amount").path("required").toString());
        assertEquals(7, schema.path("required").size());
        assertTrue(FccInvoice.prompt(DOC).contains("never a bare string"));
    }

    @Test
    @DisplayName("a date left as printed (10/21/12) instead of ISO is caught by regex")
    void nonIsoDate() {
        LlmAssert.assertThat(output("2319.00", "AIRTIME TOTAL : $ 2319.00", BOTH, "1714.90", "10/21/12"))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("regex");
    }

    @Test
    @DisplayName("no deductions at all: net must equal gross")
    void noDeductions() {
        LlmAssert.assertThat(output("2319.00", "AIRTIME TOTAL : $ 2319.00", "", "1714.90", "2012-10-21"))
                .withContext(CTX).isRejectedBy(FccInvoice.contract()).byGate("math");
    }

    @Test
    @DisplayName("the prompt carries every page, numbered, and asks for exact quotes and every deduction")
    void promptContents() {
        String p = FccInvoice.prompt(DOC);
        assertTrue(p.contains("--- page 1 ---") && p.contains("--- page 2 ---"), p);
        assertTrue(p.contains("Rep Commission : $ 256.25"), "page text included verbatim");
        assertTrue(p.contains("deductions") && p.contains("character for character"), p);
    }
}
