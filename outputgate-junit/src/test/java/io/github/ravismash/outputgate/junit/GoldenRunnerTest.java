package io.github.ravismash.outputgate.junit;

import static io.github.ravismash.outputgate.junit.Contracts.INVOICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoldenRunnerTest {

    @TempDir
    Path dir;

    private GoldenRun run(String resource) {
        return GoldenRunner.of(INVOICE).reportDir(dir).run(resource);
    }

    @Test
    @DisplayName("R12: a golden set where every case matches has pass rate 1.0")
    void allMatch() {
        GoldenRun run = run("golden/sample.jsonl");
        assertEquals("sample", run.name());
        assertEquals(4, run.cases().size());
        assertEquals(4, run.matched());
        assertEquals(1.0, run.passRate());
        run.assertPassRateAtLeast(1.0);
    }

    @Test
    @DisplayName("R12: assertPassRateAtLeast fails below the bar and names every case that didn't match")
    void belowBarFails() {
        GoldenRun run = run("golden/mixed.jsonl");
        assertEquals(0.75, run.passRate());
        run.assertPassRateAtLeast(0.75);
        AssertionError e = assertThrows(AssertionError.class, () -> run.assertPassRateAtLeast(0.8));
        String m = e.getMessage();
        assertTrue(m.contains("3/4") && m.contains("75.0%") && m.contains("80.0%"), m);
        assertTrue(m.contains("wrongly expected to pass"), m);
        assertTrue(m.contains("[math] subtotal: sum of line_items[*].amount is 74.50"), "the reason, not just the first line: " + m);
        assertFalse(m.contains("- clean invoice"), m);
        assertTrue(m.contains(run.report().toString()), "points at the report: " + m);
    }

    @Test
    @DisplayName("R12: each case records whether it matched and why not")
    void caseResults() {
        List<GoldenRun.CaseResult> cases = run("golden/mixed.jsonl").cases();
        assertEquals(List.of(true, true, true, false), cases.stream().map(GoldenRun.CaseResult::matched).toList());
        assertTrue(cases.get(3).problem().contains("expected to pass"), cases.get(3).problem());
        assertEquals(null, cases.get(0).problem());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.01, Double.NaN})
    @DisplayName("R12: a pass-rate bar outside 0..1 is an error")
    void badBar(double bar) {
        GoldenRun run = run("golden/sample.jsonl");
        assertThrows(IllegalArgumentException.class, () -> run.assertPassRateAtLeast(bar));
    }

    @Test
    @DisplayName("R12: an empty golden set never passes the bar (fail-closed)")
    void emptySetFails() {
        GoldenRun run = run("golden/empty.jsonl");
        assertEquals(0.0, run.passRate());
        AssertionError e = assertThrows(AssertionError.class, () -> run.assertPassRateAtLeast(0.0));
        assertTrue(e.getMessage().contains("no cases"), e.getMessage());
    }

    @Test
    @DisplayName("R14: each run writes <set>.html into the report directory")
    void writesReport() {
        GoldenRun run = run("golden/mixed.jsonl");
        assertEquals(dir.resolve("mixed.html"), run.report());
        assertTrue(Files.isRegularFile(run.report()));
    }

    @Test
    @DisplayName("R14: the report has per-case gate ticks and violations")
    void reportContent() throws IOException {
        String html = Files.readString(run("golden/mixed.jsonl").report());
        assertTrue(html.startsWith("<!doctype html>"), html.substring(0, 40));
        for (String name : List.of("clean invoice", "dropped line item", "apology in description", "wrongly expected to pass")) {
            assertTrue(html.contains(name), name);
        }
        assertTrue(html.contains("3/4") && html.contains("75.0%"), "summary");
        assertTrue(html.contains("<th>math</th>") && html.contains("<th>regex</th>"), "one column per gate");
        assertTrue(html.contains("✓") && html.contains("✗"), "ticks");
        assertTrue(html.contains("sum of line_items[*].amount is 74.50 but subtotal is 100.00"), "violation text");
    }

    @Test
    @DisplayName("R14: the report is self-contained: no external scripts, styles or links")
    void selfContained() throws IOException {
        String html = Files.readString(run("golden/sample.jsonl").report());
        assertFalse(html.contains("http://") || html.contains("https://") || html.contains("<script")
                || html.contains("<link"), "external resource in report");
    }

    @Test
    @DisplayName("R14: names and model text are HTML-escaped in the report")
    void escapes() throws IOException {
        String html = Files.readString(run("golden/escape.jsonl").report());
        assertFalse(html.contains("<script>alert") || html.contains("<b>bold</b>"), "unescaped content");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; co"), "escaped name");
    }

    @Test
    @DisplayName("R14: reports go to build/outputgate by default")
    void defaultDir() {
        assertEquals(Path.of("build", "outputgate"), GoldenRunner.of(INVOICE).reportDir());
    }
}
