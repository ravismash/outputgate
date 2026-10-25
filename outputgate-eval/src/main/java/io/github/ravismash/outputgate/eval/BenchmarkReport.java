package io.github.ravismash.outputgate.eval;

import io.github.ravismash.outputgate.Violation;
import io.github.ravismash.outputgate.eval.Benchmark.Arm;
import io.github.ravismash.outputgate.eval.Benchmark.Clean;
import io.github.ravismash.outputgate.eval.Benchmark.Corrupt;
import io.github.ravismash.outputgate.eval.Benchmark.Results;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Writes the comparison as Markdown (to paste into the README) and as one self-contained HTML
 * page (no scripts, no links), from the same content.
 */
public final class BenchmarkReport {

    /** Most violations listed per rejected output. */
    private static final int MAX_VIOLATIONS = 3;

    private final StringBuilder md = new StringBuilder();
    private final StringBuilder html = new StringBuilder();

    private BenchmarkReport() {}

    /**
     * @param results the scored outputs
     * @param dir where benchmark.md and benchmark.html go
     * @return the Markdown file
     */
    public static Path write(Results results, Path dir) {
        BenchmarkReport r = new BenchmarkReport();
        r.render(results);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("benchmark.html"), r.page(results));
            return Files.writeString(dir.resolve("benchmark.md"), r.md);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return the Markdown text */
    static String markdown(Results results) {
        BenchmarkReport r = new BenchmarkReport();
        r.render(results);
        return r.md.toString();
    }

    private void render(Results res) {
        List<Clean> clean = res.clean();
        List<Corrupt> bad = res.corrupted();
        heading(1, "outputgate benchmark: " + res.task());
        para("Model: " + res.model().orElse("not recorded") + " · " + res.documents() + " documents, "
                + clean.size() + " with an answer, " + bad.size() + " corrupted outputs · " + LocalDate.now());

        heading(2, "Results");
        List<String> header = new ArrayList<>(List.of(""));
        for (Arm a : Arm.values()) {
            header.add(a.label());
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> accepted = new ArrayList<>(List.of("Recorded answers accepted"));
        List<String> caught = new ArrayList<>(List.of("Corrupted outputs caught"));
        for (Arm a : Arm.values()) {
            accepted.add(ratio(count(clean, c -> c.scored().accepted(a)), clean.size()));
            caught.add(ratio(count(bad, c -> !c.scored().accepted(a)), bad.size()));
        }
        rows.add(accepted);
        rows.add(caught);
        table(header, rows);

        if (!bad.isEmpty()) {
            heading(2, "Caught, by kind of corruption");
            List<String> h = new ArrayList<>(List.of("Kind", "What changed", "n"));
            for (Arm a : Arm.values()) {
                h.add(a.label());
            }
            List<List<String>> kindRows = new ArrayList<>();
            for (Corruption kind : Corruption.values()) {
                List<Corrupt> ofKind = bad.stream().filter(c -> c.entry().kind().equals(kind.id())).toList();
                if (ofKind.isEmpty()) {
                    continue;
                }
                List<String> row = new ArrayList<>(List.of(kind.id(), kind.description(), String.valueOf(ofKind.size())));
                for (Arm a : Arm.values()) {
                    row.add(count(ofKind, c -> !c.scored().accepted(a)) + "/" + ofKind.size());
                }
                kindRows.add(row);
            }
            table(h, kindRows);

            List<String> missed = bad.stream().filter(c -> c.scored().accepted(Arm.FULL))
                    .map(c -> c.entry().file() + ": " + c.entry().detail()).toList();
            heading(2, "Corrupted outputs the full contract let through (" + missed.size() + ")");
            list(missed.isEmpty() ? List.of("None.") : missed);
        }

        if (!res.truth().isEmpty()) {
            heading(2, "Recorded answers against the ground truth");
            para("The dataset's own values for " + res.truth().size() + " documents (" + truthFields(res)
                    + "). An accepted answer that differs is one a policy let through wrong.");
            List<String> th = new ArrayList<>(List.of(""));
            for (Arm a : Arm.values()) {
                th.add(a.label());
            }
            List<String> right = new ArrayList<>(List.of("Accepted, every compared field right"));
            List<String> wrongRow = new ArrayList<>(List.of("Accepted, some field wrong"));
            for (Arm a : Arm.values()) {
                right.add(String.valueOf(count(clean, c -> c.scored().accepted(a) && truthIs(res, c, true))));
                wrongRow.add(String.valueOf(count(clean, c -> c.scored().accepted(a) && truthIs(res, c, false))));
            }
            table(th, List.of(right, wrongRow));
            List<String> wrongAccepted = new ArrayList<>();
            for (Clean c : clean) {
                if (c.scored().accepted(Arm.FULL) && truthIs(res, c, false)) {
                    wrongAccepted.add(c.document() + ": " + String.join("; ", res.truth().get(c.document()).wrong()));
                }
            }
            heading(3, "Accepted by the full contract but wrong (" + wrongAccepted.size() + ")");
            list(wrongAccepted.isEmpty() ? List.of("None.") : wrongAccepted);
        }

        List<String> rejected = new ArrayList<>();
        for (Clean c : clean) {
            if (!c.scored().accepted(Arm.FULL)) {
                List<Violation> v = c.scored().full().violations();
                StringBuilder line = new StringBuilder(c.document()).append(": ");
                for (int i = 0; i < Math.min(MAX_VIOLATIONS, v.size()); i++) {
                    line.append(i == 0 ? "" : "; ").append('[').append(v.get(i).gate()).append("] ")
                            .append(v.get(i).path().isEmpty() ? "" : v.get(i).path() + ": ").append(v.get(i).message());
                }
                if (v.size() > MAX_VIOLATIONS) {
                    line.append("; and ").append(v.size() - MAX_VIOLATIONS).append(" more");
                }
                rejected.add(line.toString());
            }
        }
        heading(2, "Recorded answers the full contract rejected (" + rejected.size() + ")");
        para("Each is either a model mistake the contract caught or a contract that is too strict. Read them by hand before quoting the numbers above.");
        list(rejected.isEmpty() ? List.of("None.") : rejected);

        if (!res.skipped().isEmpty()) {
            heading(2, "Skipped (" + res.skipped().size() + ")");
            list(res.skipped());
        }

        heading(2, "How to read this");
        list(List.of(
                "The three columns are three ways to decide whether to trust the same model answers. Naive accepts anything a "
                        + "default JSON parser reads as an object (it ignores text after the object). Schema only runs the task's "
                        + "JSON Schema. Full contract runs every gate.",
                res.truth().isEmpty()
                        ? "There are no ground-truth labels here, so \"accepted\" is not \"correct\": the accepted share of recorded "
                                + "answers is an upper bound on how many each policy would let through, right or wrong."
                        : "\"Accepted\" is not \"correct\": the ground-truth table shows how many accepted answers were right on "
                                + "the fields the dataset labels. Fields it does not label are not checked there.",
                "Corrupted outputs are copies of recorded answers the full contract accepted, each with exactly one defect "
                        + "added. The kinds of defect were written knowing what the gates check, so the caught rate measures "
                        + "coverage of these kinds of mistake, not of every mistake a model makes."));
    }

    private void heading(int level, String text) {
        md.append("#".repeat(level)).append(' ').append(text).append("\n\n");
        html.append("<h").append(level).append('>').append(esc(text)).append("</h").append(level).append(">\n");
    }

    private void para(String text) {
        md.append(text).append("\n\n");
        html.append("<p>").append(esc(text)).append("</p>\n");
    }

    private void list(List<String> items) {
        html.append("<ul>");
        for (String item : items) {
            md.append("- ").append(item.replace("\n", " ")).append('\n');
            html.append("<li>").append(esc(item)).append("</li>");
        }
        md.append('\n');
        html.append("</ul>\n");
    }

    private void table(List<String> header, List<List<String>> rows) {
        md.append('|');
        html.append("<table><tr>");
        for (String h : header) {
            md.append(' ').append(cell(h)).append(" |");
            html.append("<th>").append(esc(h)).append("</th>");
        }
        md.append("\n|").append("---|".repeat(header.size())).append('\n');
        html.append("</tr>");
        for (List<String> row : rows) {
            md.append('|');
            html.append("<tr>");
            for (String c : row) {
                md.append(' ').append(cell(c)).append(" |");
                html.append("<td>").append(esc(c)).append("</td>");
            }
            md.append('\n');
            html.append("</tr>");
        }
        md.append('\n');
        html.append("</table>\n");
    }

    private String page(Results res) {
        return "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>outputgate benchmark: " + esc(res.task()) + "</title><style>"
                + "body{font:14px/1.45 -apple-system,Segoe UI,Helvetica,Arial,sans-serif;margin:24px;max-width:1100px;color:#1b1f24;background:#fff}"
                + "h1{font-size:20px}h2{font-size:16px;margin-top:28px}"
                + "table{border-collapse:collapse;width:100%}th,td{border-bottom:1px solid #d9dde3;padding:6px 8px;text-align:left;vertical-align:top}"
                + "th{font-size:12px;text-transform:uppercase;color:#5b6470}li{margin:2px 0;overflow-wrap:anywhere}"
                + "@media(prefers-color-scheme:dark){body{background:#0e1116;color:#e6e8eb}th{color:#9aa1ab}th,td{border-color:#2a3038}}"
                + "</style></head><body>\n" + html + "</body></html>\n";
    }

    /** Whether the answer was compared with the ground truth and every compared field was {@code right} (or not). */
    private static boolean truthIs(Results res, Clean c, boolean right) {
        Benchmark.Truth t = res.truth().get(c.document());
        return t != null && t.wrong().isEmpty() == right;
    }

    private static String truthFields(Results res) {
        Set<String> fields = new TreeSet<>();
        res.truth().values().forEach(t -> fields.addAll(t.fields()));
        return String.join(", ", fields);
    }

    private static <T> int count(List<T> items, Predicate<T> p) {
        return (int) items.stream().filter(p).count();
    }

    private static String ratio(int n, int of) {
        return of == 0 ? "–" : n + "/" + of + " (" + String.format(Locale.ROOT, "%.0f%%", 100.0 * n / of) + ")";
    }

    private static String cell(String s) {
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
