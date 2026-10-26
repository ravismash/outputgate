package io.github.ravismash.outputgate.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.junit.LlmAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A made-up statement in the layout fetch_sec_balance_sheets.py writes. */
class BalanceSheetTest {

    static final SourceDocument DOC = SourceDocument.ofPages("""
            CONDENSED CONSOLIDATED BALANCE SHEETS - USD ($) $ in Millions   Jun. 30, 2026   Dec. 31, 2025
            Current assets:
            Cash and cash equivalents   $ 1,200   $ 1,000
            Inventories:
            Finished products   500   450
            Materials   300   250
            Total inventories   800   700
            Other current assets   (1)   3
            Total current assets   1,999   1,703
            Property, plant and equipment, net   3,001   2,900
            Total assets   5,000   4,603
            Current liabilities:
            Accounts payable   900   850
            Total current liabilities   900   850
            Long-term debt   2,100   2,053
            Total stockholders' equity   2,000   1,700
            Total liabilities and stockholders' equity   $ 5,000   $ 4,603
            """);

    static final EvalContext CTX = EvalContext.empty().withSource(DOC);

    static String line(String label, String value, String text) {
        return "{\"label\": \"" + label + "\", \"amount\": " + SyntheticFcc.ev(value, 1, text) + "}";
    }

    static String output(String currentAssets, String totalAssets, String totalAssetsSpan) {
        return "{\"period_end\": " + SyntheticFcc.ev("\"2026-06-30\"", 1, "Jun. 30, 2026")
                + ", \"current_assets\": [" + currentAssets + "]"
                + ", \"total_current_assets\": " + SyntheticFcc.ev("1999", 1, "1,999")
                + ", \"total_assets\": " + SyntheticFcc.ev(totalAssets, 1, totalAssetsSpan)
                + ", \"current_liabilities\": [" + line("Accounts payable", "900", "900") + "]"
                + ", \"total_current_liabilities\": " + SyntheticFcc.ev("900", 1, "900")
                + ", \"total_liabilities_and_equity\": " + SyntheticFcc.ev("5000", 1, "$ 5,000") + "}";
    }

    static final String SUBTOTAL = line("Cash and cash equivalents", "1200", "$ 1,200") + ","
            + line("Total inventories", "800", "800") + "," + line("Other current assets", "-1", "(1)");

    @Test
    @DisplayName("a faithful extraction, with a subtotal and a negative amount, passes")
    void faithfulPasses() {
        Verdict v = BalanceSheet.contract().evaluate(output(SUBTOTAL, "5000", "5,000"), CTX);
        assertTrue(v.passed(), v::toString);
    }

    @Test
    @DisplayName("listing a subtotal and the lines in it counts them twice, and math catches it")
    void doubleCountedSubtotalCaught() {
        String both = SUBTOTAL + "," + line("Finished products", "500", "500") + "," + line("Materials", "300", "300");
        LlmAssert.assertThat(output(both, "5000", "5,000")).withContext(CTX).isRejectedBy(BalanceSheet.contract()).byGate("math");
    }

    @Test
    @DisplayName("total assets that differ from liabilities and equity are caught by math")
    void unbalancedCaught() {
        LlmAssert.assertThat(output(SUBTOTAL, "4603", "4,603")).withContext(CTX).isRejectedBy(BalanceSheet.contract()).byGate("math");
    }

    @Test
    @DisplayName("an amount quoted as it is not printed is not grounded")
    void ungroundedQuoteRejected() {
        LlmAssert.assertThat(output(SUBTOTAL, "5000", "$5,000.00")).withContext(CTX)
                .isRejectedBy(BalanceSheet.contract()).byGate("provenance");
    }

    @Test
    @DisplayName("the output schema the model is steered with names every field the contract requires")
    void outputSchemaCoversContract() {
        String schema = BalanceSheet.outputSchema();
        for (String field : BenchmarkTask.BALANCE_SHEET.requiredFields()) {
            assertTrue(schema.contains("\"" + field + "\""), field);
        }
    }

    @Test
    @DisplayName("amounts scaled by 1,000 with the printed quotes kept are caught by provenance (a real qwen answer did this)")
    void scaledAmountsCaught() {
        String scaled = line("Cash and cash equivalents", "1200000", "$ 1,200") + ","
                + line("Total inventories", "800000", "800") + "," + line("Other current assets", "-1000", "(1)");
        String json = output(scaled, "5000000", "5,000")
                .replace("\"value\": 1999,", "\"value\": 1999000,").replace("\"value\": 900,", "\"value\": 900000,")
                .replace("\"value\": 5000,", "\"value\": 5000000,");
        LlmAssert.assertThat(json).withContext(CTX).isRejectedBy(BalanceSheet.contract()).byGate("provenance");
    }
}
