package io.github.ravismash.outputgate.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.junit.LlmAssert;
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
