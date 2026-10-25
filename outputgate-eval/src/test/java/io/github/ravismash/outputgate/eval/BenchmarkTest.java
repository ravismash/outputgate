package io.github.ravismash.outputgate.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.FakeLlmClient;
import io.github.ravismash.outputgate.MathGate;
import io.github.ravismash.outputgate.ProvenanceGate;
import io.github.ravismash.outputgate.RegexGate;
import io.github.ravismash.outputgate.SchemaGate;
import io.github.ravismash.outputgate.eval.Benchmark.Arm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The whole benchmark on a folder made by the test: no model, no network. */
class BenchmarkTest {

    @TempDir
    Path dir;

    /** The documents EvalRun reads, and its output folder. */
    private Path docs() throws IOException {
        return Files.createDirectories(dir.resolve("docs"));
    }

    private Path run() {
        return dir.resolve("run");
    }

    /**
     * Seven invoices through {@link EvalRun} with canned answers: inv-5's answer leaves out the rep
     * commission, and the model call for inv-6 fails.
     */
    private BenchmarkFolder fccFolder() throws IOException {
        Map<String, String> replies = new HashMap<>();
        for (int i = 0; i <= 6; i++) {
            Path pdf = SyntheticFcc.pdf(docs(), i);
            if (i < 6) {
                replies.put(FccInvoice.prompt(PdfText.load(pdf)), SyntheticFcc.output(i, i == 5));
            }
        }
        EvalRun.run(docs(), FakeLlmClient.byPrompt(replies), FccInvoice.contract(), FccInvoice::prompt, run(), "qwen2.5:14b");
        return new BenchmarkFolder(docs(), run());
    }

    @Test
    @DisplayName("corrupt then report, from the command line, on a folder of PDFs")
    void endToEnd() throws IOException {
        fccFolder();
        String corrupt = BenchmarkCli.run("corrupt", docs().toString(), run().toString(), "--count", "26", "--seed", "42");
        assertTrue(corrupt.startsWith("wrote 26 corrupted outputs from 5 accepted answers"), corrupt);
        assertEquals(26, Files.readAllLines(run().resolve("corrupted/manifest.jsonl")).size());

        String report = BenchmarkCli.run("report", docs().toString(), run().toString());
        String md = Files.readString(run().resolve("benchmark.md"));
        assertTrue(Files.readString(run().resolve("benchmark.html")).startsWith("<!doctype html>"));
        assertTrue(report.contains(md.strip()), report);
        assertTrue(md.contains("Model: qwen2.5:14b · 7 documents, 6 with an answer, 26 corrupted outputs"), md);
        assertTrue(md.contains("| Recorded answers accepted | 6/6 (100%) | 6/6 (100%) | 5/6 (83%) |"), md);
        assertTrue(md.contains("inv-5.pdf: [math]"), md);
        assertTrue(md.contains("inv-6.pdf: ERROR"), md);

        Benchmark.Results r = Benchmark.run(new BenchmarkFolder(docs(), run()), BenchmarkTask.FCC);
        long caughtNaive = r.corrupted().stream().filter(c -> !c.scored().accepted(Arm.NAIVE)).count();
        long caughtSchema = r.corrupted().stream().filter(c -> !c.scored().accepted(Arm.SCHEMA)).count();
        long caughtFull = r.corrupted().stream().filter(c -> !c.scored().accepted(Arm.FULL)).count();
        assertTrue(caughtNaive < caughtSchema && caughtSchema < caughtFull, caughtNaive + " " + caughtSchema + " " + caughtFull);
        assertEquals(2, caughtNaive, "only the two code-fence outputs fail a plain parse");
        assertEquals(26, caughtFull, md);
        assertTrue(md.contains("## Corrupted outputs the full contract let through (0)"), md);
    }

    @Test
    @DisplayName("every kind is used, spread evenly, and the same seed writes the same files")
    void spreadAndDeterministic() throws IOException {
        BenchmarkFolder folder = fccFolder();
        Corruptor.Summary a = Corruptor.run(folder, BenchmarkTask.FCC, 26, 42);
        String first = Files.readString(folder.manifest());
        Corruptor.Summary b = Corruptor.run(folder, BenchmarkTask.FCC, 26, 42);
        assertEquals(first, Files.readString(folder.manifest()));
        assertEquals(a, b);
        for (Corruption kind : Corruption.values()) {
            assertEquals(2, a.entries().stream().filter(e -> e.kind().equals(kind.id())).count(), kind.id());
        }
        assertEquals(10, a.entries().stream().map(Corruptor.Entry::base).collect(Collectors.toSet()).size() * 2,
                "26 corruptions over 5 bases use every base");
    }

    @Test
    @DisplayName("a smaller re-run removes the files the earlier run wrote")
    void rerunClearsStaleFiles() throws IOException {
        BenchmarkFolder folder = fccFolder();
        Corruptor.run(folder, BenchmarkTask.FCC, 26, 42);
        Corruptor.run(folder, BenchmarkTask.FCC, 3, 1);
        try (var files = Files.list(folder.corrupted())) {
            assertEquals(4, files.count(), "3 outputs and the manifest");
        }
    }

    @Test
    @DisplayName("asking for more than the kinds can make writes what they can")
    void runsOutOfBases() throws IOException {
        BenchmarkFolder folder = fccFolder();
        Corruptor.Summary s = Corruptor.run(folder, BenchmarkTask.FCC, 500, 42);
        assertEquals(5 * Corruption.values().length, s.entries().size());
    }

    @Test
    @DisplayName("with no accepted answer there is nothing to corrupt")
    void nothingToCorrupt() throws IOException {
        Path pdf = SyntheticFcc.pdf(docs(), 1);
        EvalRun.run(docs(), FakeLlmClient.replying(SyntheticFcc.output(1, true)), FccInvoice.contract(), FccInvoice::prompt, run(), "m");
        assertThrows(IllegalStateException.class, () -> Corruptor.run(new BenchmarkFolder(docs(), run()), BenchmarkTask.FCC, 5, 1));
    }

    @Test
    @DisplayName("a run folder without EvalRun's results is refused")
    void needsResults() throws IOException {
        Files.createDirectories(run());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new BenchmarkFolder(docs(), run()))
                .getMessage().contains("no results.jsonl"));
    }

    static final BenchmarkTask RECEIPT = new BenchmarkTask("receipt", "schemas/test-receipt.schema.json",
            () -> Contract.builder()
                    .gate(SchemaGate.fromClasspath("schemas/test-receipt.schema.json"))
                    .gate(MathGate.builder().sumEquals("items[*].price", "total").tolerance(0.01).build())
                    .gate(ProvenanceGate.builder().require("company", "date", "items[*].price", "total").grounded().build())
                    .gate(RegexGate.builder().isoDate("date").forbid("company", "(?i)\\bsorry\\b").build())
                    .build(),
            doc -> "Extract this receipt.\n" + doc.pages().get(0));

    @Test
    @DisplayName("another dataset is another task: OCR'd receipts as .txt files, a different contract, a ground truth")
    void receiptsFromText() throws IOException {
        Map<String, String> replies = new HashMap<>();
        for (int i = 1; i <= 3; i++) {
            String a = "4." + i + "0";
            String b = "12.50";
            String total = new java.math.BigDecimal(a).add(new java.math.BigDecimal(b)).toPlainString();
            Path receipt = Files.writeString(docs().resolve("r" + i + ".txt"),
                    "KEDAI MAKAN " + i + "\nDATE: 0" + i + "/03/2018\nNASI LEMAK RM " + a + "\nTEH TARIK RM " + b + "\nTOTAL RM " + total + "\n");
            replies.put(RECEIPT.prompt().apply(BenchmarkFolder.load(receipt)), "{"
                    + "\"company\": " + SyntheticFcc.ev("\"KEDAI MAKAN " + i + "\"", 1, "KEDAI MAKAN " + i)
                    + ", \"date\": " + SyntheticFcc.ev("\"2018-03-0" + i + "\"", 1, "DATE: 0" + i + "/03/2018")
                    + ", \"items\": [{\"name\": \"NASI LEMAK\", \"price\": " + SyntheticFcc.ev(a, 1, "NASI LEMAK RM " + a) + "},"
                    + " {\"name\": \"TEH TARIK\", \"price\": " + SyntheticFcc.ev(b, 1, "TEH TARIK RM " + b) + "}]"
                    + ", \"total\": " + SyntheticFcc.ev(total, 1, "TOTAL RM " + total) + "}");
        }
        // r2's total is labelled differently in the ground truth: an accepted answer that is wrong.
        Files.writeString(docs().resolve("ground-truth.json"),
                "{\"r1.txt\": {\"total\": 16.60, \"company\": \"not a number, not compared\"}, \"r2.txt\": {\"total\": 16.80}}");
        EvalRun.run(docs(), FakeLlmClient.byPrompt(replies), RECEIPT.contract().get(), RECEIPT.prompt(), run(), "m");
        BenchmarkFolder folder = new BenchmarkFolder(docs(), run());
        Corruptor.Summary s = Corruptor.run(folder, RECEIPT, 13, 3);
        assertEquals(3, s.bases());
        assertEquals(13, s.entries().size(), s.entries().toString());

        Benchmark.Results r = Benchmark.run(folder, RECEIPT);
        assertEquals(3, r.clean().size());
        assertTrue(r.clean().stream().allMatch(c -> c.scored().accepted(Arm.FULL)));
        Set<String> missed = r.corrupted().stream().filter(c -> c.scored().accepted(Arm.FULL))
                .map(c -> c.entry().kind()).collect(Collectors.toSet());
        assertEquals(Set.of(), missed, BenchmarkReport.markdown(r));

        assertEquals(Set.of("r1.txt", "r2.txt"), r.truth().keySet());
        assertEquals(List.of("total"), r.truth().get("r1.txt").fields());
        assertEquals(List.of(), r.truth().get("r1.txt").wrong());
        assertEquals(List.of("total: answer 16.70, ground truth 16.8"), r.truth().get("r2.txt").wrong());
        assertTrue(BenchmarkReport.markdown(r).contains("Accepted by the full contract but wrong (1)"), BenchmarkReport.markdown(r));
    }

    @Test
    @DisplayName("bad command lines say how to use the tool")
    void usage() throws IOException {
        fccFolder();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> BenchmarkCli.run("report", docs().toString()))
                .getMessage().contains("usage:"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BenchmarkCli.run("report", docs().toString(), run().toString(), "--task", "cord"))
                .getMessage().contains("unknown task cord"));
        assertThrows(IllegalArgumentException.class, () -> BenchmarkCli.run("frobnicate", docs().toString(), run().toString()));
    }
}
