package io.github.ravismash.outputgate.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.FakeLlmClient;
import io.github.ravismash.outputgate.LlmClient;
import io.github.ravismash.outputgate.MathGate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalRunTest {

    @TempDir
    Path tmp;

    private static final Contract CONTRACT = Contract.builder()
            .gate(MathGate.builder().equalsSum("total", "a", "b").build())
            .build();

    private Path pdf(Path dir, String name, String text) throws IOException {
        Path file = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream s = new PDPageContentStream(doc, page)) {
                if (!text.isEmpty()) {
                    s.beginText();
                    s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    s.newLineAtOffset(72, 720);
                    s.showText(text);
                    s.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private Path folder() throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("pdfs"));
        pdf(dir, "a-good.pdf", "Total 3");
        pdf(dir, "b-bad.pdf", "Total 4");
        pdf(dir, "c-scan.pdf", "");
        Files.writeString(dir.resolve("notes.md"), "not a .pdf or .txt document, ignored");
        return dir;
    }

    @Test
    @DisplayName("every PDF is logged: passed, rejected with its violations, or no text; with raw answers")
    void logsEveryCase() throws IOException {
        Path out = tmp.resolve("run");
        FakeLlmClient model = FakeLlmClient.inOrder("{\"a\": 1, \"b\": 2, \"total\": 3}", "{\"a\": 1, \"b\": 2, \"total\": 4}");
        List<EvalRun.Result> results = EvalRun.run(folder(), model, CONTRACT, doc -> "extract: " + doc.pages().get(0), out, "fake");

        assertEquals(List.of("a-good.pdf", "b-bad.pdf", "c-scan.pdf"), results.stream().map(EvalRun.Result::file).toList());
        assertEquals(List.of(EvalRun.Outcome.PASSED, EvalRun.Outcome.REJECTED, EvalRun.Outcome.NO_TEXT),
                results.stream().map(EvalRun.Result::outcome).toList());
        assertEquals("math", results.get(1).violations().get(0).gate());
        assertTrue(results.get(2).error().contains("no text layer"), results.get(2).error());
        assertEquals(2, model.prompts().size(), "no prompt for the scan");
        assertTrue(model.prompts().get(0).contains("Total 3"));

        List<String> lines = Files.readAllLines(out.resolve("results.jsonl"));
        assertEquals(3, lines.size());
        JsonNode bad = new ObjectMapper().readTree(lines.get(1));
        assertEquals("REJECTED", bad.path("outcome").asText());
        assertEquals("math", bad.path("violations").get(0).path("gate").asText());
        assertEquals("{\"a\": 1, \"b\": 2, \"total\": 4}", bad.path("output").asText());

        String summary = Files.readString(out.resolve("summary.md"));
        assertTrue(summary.contains("fake") && summary.contains("PASSED: 1") && summary.contains("REJECTED: 1")
                && summary.contains("NO_TEXT: 1"), summary);
        assertTrue(summary.contains("| math | 1 |"), "rejections counted by gate: " + summary);
        assertTrue(summary.contains("b-bad.pdf") && summary.contains("total is 4 but 1 + 2 is 3"), summary);
    }

    @Test
    @DisplayName("a model error is logged for that PDF and the run continues")
    void modelErrorLogged() throws IOException {
        Path out = tmp.resolve("run2");
        LlmClient failing = prompt -> {
            throw new IllegalStateException("model down");
        };
        List<EvalRun.Result> results = EvalRun.run(folder(), failing, CONTRACT, doc -> "p", out, "failing");
        assertEquals(List.of(EvalRun.Outcome.ERROR, EvalRun.Outcome.ERROR, EvalRun.Outcome.NO_TEXT),
                results.stream().map(EvalRun.Result::outcome).toList());
        assertTrue(results.get(0).error().contains("model down"));
        assertTrue(Files.readString(out.resolve("summary.md")).contains("ERROR: 2"));
    }

    @Test
    @DisplayName("a resumed run keeps what a stopped run recorded and only asks about the rest")
    void resumeKeepsEarlierResults() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("run3"));
        Path docs = folder();
        EvalRun.run(docs, FakeLlmClient.inOrder("{\"a\": 1, \"b\": 2, \"total\": 3}", "{\"a\": 1, \"b\": 2, \"total\": 4}"),
                CONTRACT, doc -> "p", out, "first");
        List<String> all = Files.readAllLines(out.resolve("results.jsonl"));
        Files.writeString(out.resolve("results.jsonl"), all.get(0) + "\n"); // stopped after a-good.pdf

        FakeLlmClient model = FakeLlmClient.inOrder("{\"a\": 1, \"b\": 2, \"total\": 4}");
        List<EvalRun.Result> results = EvalRun.run(docs, model, CONTRACT, doc -> "p", out, "resumed", true);
        assertEquals(1, model.prompts().size(), "only b-bad.pdf is asked again");
        assertEquals(List.of(EvalRun.Outcome.PASSED, EvalRun.Outcome.REJECTED, EvalRun.Outcome.NO_TEXT),
                results.stream().map(EvalRun.Result::outcome).toList());
        assertEquals(all, Files.readAllLines(out.resolve("results.jsonl")));
    }
}
