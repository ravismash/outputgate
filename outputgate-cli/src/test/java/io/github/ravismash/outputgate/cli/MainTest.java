package io.github.ravismash.outputgate.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ravismash.outputgate.Violation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["id", "issued_on", "items", "total"],
              "properties": {
                "id": {"type": "string"},
                "issued_on": {"type": "string"},
                "items": {"type": "array", "items": {"type": "object", "required": ["quantity", "price", "amount"]}},
                "total": {"type": "number"}
              }
            }
            """;

    private static final String CONTRACT = """
            schema: record.schema.json
            regex:
              - require: { path: id, pattern: "R-\\\\d+" }
              - isoDate: issued_on
            math:
              tolerance: 0.01
              rules:
                - sumEquals: { items: "items[*].amount", total: total }
                - productEquals: { result: "items[*].amount", factors: ["items[*].quantity", "items[*].price"] }
                - range: { path: "items[*].quantity", min: 0, max: 1000 }
            """;

    private static final String GOOD = """
            {"id": "R-1", "issued_on": "2026-01-31", "items": [
              {"quantity": 2, "price": 5, "amount": 10}, {"quantity": 1, "price": 2.5, "amount": 2.5}],
             "total": 12.5}
            """;

    private static final String BAD = """
            {"id": "X1", "issued_on": "2026-02-30", "items": [
              {"quantity": 2, "price": 5, "amount": 11}], "total": 12.5}
            """;

    @BeforeEach
    void files() throws IOException {
        Files.writeString(dir.resolve("record.schema.json"), SCHEMA);
        Files.writeString(dir.resolve("contract.yaml"), CONTRACT);
        Files.createDirectories(dir.resolve("out/nested"));
        Files.writeString(dir.resolve("out/good.json"), GOOD);
    }

    private int run(String... args) {
        return new Main(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), dir).run(args);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void passingDocumentsExitZero() {
        assertEquals(Main.PASSED, run("check", "--contract", "contract.yaml", "out"));
        assertTrue(stdout().contains("PASS out/good.json"), stdout());
        assertTrue(stdout().contains("1 document: 1 passed, 0 rejected, 0 violations"), stdout());
    }

    @Test
    void everyViolationIsListedAndTheExitIsOne() throws IOException {
        Files.writeString(dir.resolve("out/nested/bad.json"), BAD);
        assertEquals(Main.REJECTED, run("check", "-c", "contract.yaml", "out"));
        String s = stdout();
        assertTrue(s.contains("FAIL out/nested/bad.json"), s);
        assertTrue(s.contains("[regex] id: \"X1\" does not match"), s);
        assertTrue(s.contains("[regex] issued_on:"), s);
        assertTrue(s.contains("[math]"), s);
        assertTrue(s.contains("2 documents: 1 passed, 1 rejected"), s);
    }

    @Test
    void jsonlChecksEachLineAndGithubFormatAnnotatesIt() throws IOException {
        Files.writeString(dir.resolve("out/runs.jsonl"),
                GOOD.replace("\n", " ") + "\n\n" + "not json\n");
        assertEquals(Main.REJECTED, run("check", "-c", "contract.yaml", "--format", "github", "out/runs.jsonl"));
        String s = stdout();
        assertTrue(s.contains("PASS out/runs.jsonl:1"), s);
        assertTrue(s.contains("FAIL out/runs.jsonl:3"), s);
        assertTrue(s.contains("::error file=out/runs.jsonl,line=3,title=outputgate json-parse::"), s);
    }

    @Test
    void globsMatchFromTheWorkingDirectory() throws IOException {
        Files.writeString(dir.resolve("out/nested/bad.json"), BAD);
        assertEquals(Main.REJECTED, run("check", "-c", "contract.yaml", "-q", "out/**/*.json"));
        String s = stdout();
        assertFalse(s.contains("PASS"), "quiet hides passing documents: " + s);
        assertTrue(s.contains("FAIL out/nested/bad.json"), s);
        // "**/" needs at least one directory, so out/good.json is matched by "out/*.json" only.
        assertTrue(s.contains("1 document:"), s);
    }

    @Test
    void summaryIsAppendedAsMarkdown() throws IOException {
        Files.writeString(dir.resolve("out/nested/bad.json"), BAD);
        run("check", "-c", "contract.yaml", "--summary", "summary.md", "out");
        String md = Files.readString(dir.resolve("summary.md"));
        assertTrue(md.startsWith("### outputgate: rejected"), md);
        assertTrue(md.contains("| Document | Gate | Field | Violation |"), md);
        assertTrue(md.contains("| `out/nested/bad.json` | regex | `id` |"), md);
    }

    @Test
    void groundingReadsSourcesByName() throws IOException {
        Files.writeString(dir.resolve("contract.yaml"), """
                provenance:
                  require: [id]
                  grounded: ignoring-whitespace
                """);
        Files.writeString(dir.resolve("out/good.json"), """
                {"id": {"value": "R-1", "source_span": {"page": 2, "text": "Ref R-1"}}}
                """);
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/good.txt"), "page one\fRef  R-1 on page two");
        assertEquals(Main.PASSED, run("check", "-c", "contract.yaml", "-s", "src", "out/good.json"), stdout());

        Files.writeString(dir.resolve("src/good.txt"), "page one\fnothing here");
        assertEquals(Main.REJECTED, run("check", "-c", "contract.yaml", "-s", "src", "out/good.json"), stdout());
    }

    @Test
    void contractErrorsExitTwo() throws IOException {
        Files.writeString(dir.resolve("typo.yaml"), """
                math:
                  rules:
                    - sumEqual: { items: "a[*]", total: b }
                """);
        assertEquals(Main.ERROR, run("check", "-c", "typo.yaml", "out"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown math rule 'sumEqual'"), err.toString());
    }

    @Test
    void unknownKeysAreErrorsNotSkippedRules() throws IOException {
        Files.writeString(dir.resolve("typo.yaml"), "schemas: record.schema.json\n");
        assertEquals(Main.ERROR, run("check", "-c", "typo.yaml", "out"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown key 'schemas'"), err.toString());
    }

    @Test
    void noMatchingInputExitsTwo() {
        assertEquals(Main.ERROR, run("check", "-c", "contract.yaml", "nothing/**/*.json"));
        assertEquals(Main.ERROR, run("check", "-c", "contract.yaml", "missing.json"));
        assertEquals(Main.ERROR, run("check", "out"));
    }

    @Test
    void annotationEscapesWorkflowCommandCharacters() {
        String a = Main.annotation("a,b:c.json", 0, new Violation("math", "x", "50% off\nnext"));
        assertEquals("::error file=a%2Cb%3Ac.json,title=outputgate math::x: 50%25 off%0Anext", a);
    }
}
