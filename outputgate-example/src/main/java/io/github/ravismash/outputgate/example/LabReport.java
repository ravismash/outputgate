package io.github.ravismash.outputgate.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * The typed shape a lab-report extraction must bind to ({@code TypeGate}): a record read off a
 * form, one entry per result row.
 *
 * @param laboratory the issuing laboratory
 * @param specimenId e.g. {@code NCL-2026-118204}
 * @param collectedDate ISO-8601 date
 * @param results one per test row
 */
public record LabReport(
        @JsonProperty("laboratory") String laboratory,
        @JsonProperty("specimen_id") String specimenId,
        @JsonProperty("collected_date") String collectedDate,
        @JsonProperty("results") List<Result> results) {

    /**
     * One result row.
     *
     * @param test the test's name as printed
     * @param result the result
     * @param unit the result's unit
     * @param flag {@code H}, {@code L}, or {@code N} when the row has no flag
     */
    public record Result(
            @JsonProperty("test") String test,
            @JsonProperty("result") BigDecimal result,
            @JsonProperty("unit") String unit,
            @JsonProperty("flag") String flag) {}
}
