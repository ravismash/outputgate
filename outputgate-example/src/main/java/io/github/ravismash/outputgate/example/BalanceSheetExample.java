package io.github.ravismash.outputgate.example;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.Region;
import io.github.ravismash.outputgate.RegexGate;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.TypeGate;

/**
 * A real document: Corteva's balance sheet from its 10-Q for the quarter to 30 June 2026, as SEC
 * EDGAR's XBRL viewer prints it (one of the benchmark's held-out documents). The page prints two
 * near-identical statements, Corteva's and then its subsidiary EIDP's, so a quote can be on the page
 * and still come from the wrong statement. The contract checks that lines add up to their totals,
 * that the balance sheet balances, that every amount is the number its own quote prints, and that
 * the date and totals are quoted from the first statement.
 */
public final class BalanceSheetExample {

    private static final String DOCUMENT = "documents/ctva-balance-sheet-2026-06-30.txt";

    /** From the top of the page through the first statement's last line. */
    static final Region FIRST_STATEMENT = Region.fromDocumentStart().through("(?i)^liabilities and equity\\b");

    private BalanceSheetExample() {}

    /** @return the balance-sheet contract: type, math, provenance (grounded, scoped) and regex gates */
    public static Contract contract() {
        return Contract.builder()
                .gate(TypeGate.of(BalanceSheet.class))
                .gate(MathGate.builder()
                        // Every printed figure is rounded, so lines may miss their total by half a unit each.
                        .sumEqualsRounded("current_assets[*].amount", "total_current_assets")
                        .sumEqualsRounded("current_liabilities[*].amount", "total_current_liabilities")
                        .custom("total_assets = total_liabilities_and_equity", n ->
                                n.get("total_assets").compareTo(n.get("total_liabilities_and_equity")) == 0)
                        .tolerance(1)
                        .build())
                .gate(ProvenanceGate.builder()
                        .require("period_end", "current_assets[*].amount", "total_current_assets", "total_assets",
                                "current_liabilities[*].amount", "total_current_liabilities",
                                "total_liabilities_and_equity")
                        .groundedIgnoringWhitespace()
                        .ignoringCurrencySigns()
                        .numbersMatchQuotes()
                        .within(FIRST_STATEMENT, "period_end", "total_current_assets", "total_assets",
                                "total_current_liabilities", "total_liabilities_and_equity")
                        .build())
                .gate(RegexGate.builder()
                        .isoDate("period_end")
                        .forbid("current_assets[*].label", "(?i)\\b(sorry|unknown|n/a|as an ai)\\b")
                        .forbid("current_liabilities[*].label", "(?i)\\b(sorry|unknown|n/a|as an ai)\\b")
                        .build())
                .build();
    }

    /** @return the statement page */
    public static SourceDocument document() {
        return Resources.document(DOCUMENT);
    }

    /** @return a context carrying {@link #document()}, for grounding */
    public static EvalContext context() {
        return EvalContext.empty().withSource(document());
    }
}
