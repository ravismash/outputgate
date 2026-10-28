package io.github.ravismash.outputgate.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * The typed shape a balance-sheet extraction must bind to ({@code TypeGate}). Amounts are in the
 * statement's own units, as printed.
 *
 * @param periodEnd the latest period's date, ISO-8601
 * @param currentAssets the lines that add up to total current assets
 * @param totalCurrentAssets as printed
 * @param totalAssets as printed
 * @param currentLiabilities the lines that add up to total current liabilities
 * @param totalCurrentLiabilities as printed
 * @param totalLiabilitiesAndEquity the statement's last line
 */
public record BalanceSheet(
        @JsonProperty("period_end") String periodEnd,
        @JsonProperty("current_assets") List<Line> currentAssets,
        @JsonProperty("total_current_assets") BigDecimal totalCurrentAssets,
        @JsonProperty("total_assets") BigDecimal totalAssets,
        @JsonProperty("current_liabilities") List<Line> currentLiabilities,
        @JsonProperty("total_current_liabilities") BigDecimal totalCurrentLiabilities,
        @JsonProperty("total_liabilities_and_equity") BigDecimal totalLiabilitiesAndEquity) {

    /**
     * One line of the statement.
     *
     * @param label as printed
     * @param amount as printed
     */
    public record Line(@JsonProperty("label") String label, @JsonProperty("amount") BigDecimal amount) {}
}
