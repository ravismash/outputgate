package io.github.ravismash.outputgate.junit;

import io.github.ravismash.outputgate.Verdict;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The result of one golden run.
 *
 * @param name the golden set's name (its file name without {@code .jsonl})
 * @param cases one result per case, in file order
 * @param report the HTML report written for this run
 */
public record GoldenRun(String name, List<CaseResult> cases, Path report) {

    /**
     * One case's outcome.
     *
     * @param golden the case
     * @param verdict the contract's verdict on the case's output
     * @param matched true when the case behaved as its golden line says
     * @param problem why it didn't, or null when it matched
     */
    public record CaseResult(GoldenCase golden, Verdict verdict, boolean matched, String problem) {}

    /** Copies the case list. */
    public GoldenRun {
        cases = List.copyOf(cases);
    }

    /** @return the number of cases that matched */
    public long matched() {
        return cases.stream().filter(CaseResult::matched).count();
    }

    /** @return matched / total, or 0 for an empty set */
    public double passRate() {
        return cases.isEmpty() ? 0.0 : (double) matched() / cases.size();
    }

    /** @return e.g. {@code 3/4 (75.0%)} */
    public String summary() {
        return matched() + "/" + cases.size() + " (" + percent(passRate()) + ")";
    }

    /**
     * Fails the test when fewer than {@code min} of the cases matched, listing the ones that didn't.
     * An empty golden set always fails.
     *
     * @param min a fraction from 0 to 1, e.g. {@code 0.95}
     * @return this run
     */
    public GoldenRun assertPassRateAtLeast(double min) {
        if (!(min >= 0.0 && min <= 1.0)) {
            throw new IllegalArgumentException("pass-rate bar must be between 0 and 1, got " + min);
        }
        if (cases.isEmpty()) {
            throw new AssertionError("golden set '" + name + "' has no cases");
        }
        if (passRate() < min) {
            StringBuilder m = new StringBuilder("golden set '").append(name).append("': pass rate ")
                    .append(summary()).append(" is below the bar of ").append(percent(min))
                    .append(". Cases that didn't match:");
            for (CaseResult c : cases) {
                if (!c.matched()) {
                    m.append("\n  - ").append(c.golden().name()).append(": ")
                            .append(c.problem().replace("\n", "\n      "));
                }
            }
            m.append("\nreport: ").append(report);
            throw new AssertionError(m.toString());
        }
        return this;
    }

    static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100);
    }
}
