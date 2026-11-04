package io.github.ravismash.outputgate.cli;

import io.github.ravismash.outputgate.Contract;
import io.github.ravismash.outputgate.EvalContext;
import io.github.ravismash.outputgate.SourceDocument;
import io.github.ravismash.outputgate.Verdict;
import io.github.ravismash.outputgate.Violation;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code outputgate check --contract contract.yaml [options] <inputs...>}: checks every JSON
 * document in the inputs against the contract and lists every violation. An input is a
 * {@code .json} file (one document), a {@code .jsonl} file (one document per non-blank line), a
 * directory (every {@code .json} and {@code .jsonl} file under it) or a glob such as
 * {@code "outputs/**}{@code /*.json"}.
 *
 * <p>Exit status: 0 when every document passes, 1 when any is rejected, 2 on a usage or
 * contract error or when the inputs match no document.
 */
public final class Main {

    static final int PASSED = 0;
    static final int REJECTED = 1;
    static final int ERROR = 2;

    private static final String USAGE = """
            Usage: outputgate check --contract <file> [options] <input>...

            Checks JSON documents (e.g. structured LLM output, golden files, fixtures) against a
            contract and lists every violation. No gate calls a model.

            Inputs: .json files, .jsonl files (one document per line), directories (searched for
            .json and .jsonl files) or quoted globs such as "outputs/**/*.json".

            Options:
              -c, --contract <file>  contract in YAML or JSON (required)
              -s, --sources <dir>    source text for grounding: <dir>/<name>.txt for <name>.json,
                                     <dir>/<name>-<line>.txt for line <line> of <name>.jsonl;
                                     form feeds split pages
              -f, --format <fmt>     text (default) or github (workflow-command annotations)
              --summary <file>       append a Markdown summary, e.g. "$GITHUB_STEP_SUMMARY"
              -q, --quiet            print only rejected documents and the totals
              -h, --help             show this help
              -V, --version          show the version

            Exit status: 0 all passed, 1 some rejected, 2 usage or contract error, or no input.
            """;

    private final PrintStream out;
    private final PrintStream err;
    private final Path cwd;

    Main(PrintStream out, PrintStream err, Path cwd) {
        this.out = out;
        this.err = err;
        this.cwd = cwd;
    }

    /**
     * Runs the command line and exits with its status.
     *
     * @param args the command and its options
     */
    public static void main(String[] args) {
        System.exit(new Main(System.out, System.err, Path.of("").toAbsolutePath()).run(args));
    }

    /** One document to check. {@code line} is 0 for a whole .json file. */
    record Document(Path file, int line, String text) {
        String label(Path cwd) {
            String name = display(cwd, file);
            return line == 0 ? name : name + ":" + line;
        }
    }

    record Options(Path contract, Path sources, String format, Path summary, boolean quiet, List<String> inputs) {}

    int run(String[] args) {
        if (args.length == 0 || isHelp(args[0])) {
            out.print(USAGE);
            return args.length == 0 ? ERROR : PASSED;
        }
        if (args[0].equals("-V") || args[0].equals("--version")) {
            out.println("outputgate " + version());
            return PASSED;
        }
        if (!args[0].equals("check")) {
            err.println("outputgate: unknown command '" + args[0] + "'; expected 'check'");
            err.print(USAGE);
            return ERROR;
        }
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            err.println("outputgate: " + e.getMessage());
            return ERROR;
        }
        if (options == null) {
            out.print(USAGE);
            return PASSED;
        }
        try {
            return check(options);
        } catch (ContractFile.ContractException | IllegalArgumentException | UncheckedIOException e) {
            err.println("outputgate: " + e.getMessage());
            return ERROR;
        }
    }

    private Options parse(String[] args) {
        Path contract = null;
        Path sources = null;
        Path summary = null;
        String format = "text";
        boolean quiet = false;
        List<String> inputs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-c", "--contract" -> contract = cwd.resolve(value(args, ++i, a));
                case "-s", "--sources" -> sources = cwd.resolve(value(args, ++i, a));
                case "-f", "--format" -> format = value(args, ++i, a).toLowerCase(Locale.ROOT);
                case "--summary" -> summary = cwd.resolve(value(args, ++i, a));
                case "-q", "--quiet" -> quiet = true;
                case "-h", "--help" -> {
                    return null;
                }
                default -> {
                    if (a.startsWith("-") && a.length() > 1) {
                        throw new IllegalArgumentException("unknown option " + a);
                    }
                    inputs.add(a);
                }
            }
        }
        if (contract == null) {
            throw new IllegalArgumentException("--contract is required");
        }
        if (!format.equals("text") && !format.equals("github")) {
            throw new IllegalArgumentException("--format must be text or github, got " + format);
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("no input given; pass files, directories or globs");
        }
        if (sources != null && !Files.isDirectory(sources)) {
            throw new IllegalArgumentException("--sources is not a directory: " + sources);
        }
        return new Options(contract, sources, format, summary, quiet, inputs);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length || args[i].isEmpty()) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }

    private int check(Options o) {
        Contract contract = ContractFile.load(o.contract());
        List<Document> documents = documents(o.inputs());
        if (documents.isEmpty()) {
            err.println("outputgate: the inputs " + o.inputs() + " match no .json or .jsonl document");
            return ERROR;
        }
        boolean github = o.format().equals("github");
        int rejected = 0;
        int violations = 0;
        List<String> summaryRows = new ArrayList<>();
        for (Document doc : documents) {
            EvalContext context = source(o.sources(), doc).map(s -> EvalContext.empty().withSource(s))
                    .orElse(EvalContext.empty());
            Verdict verdict = contract.evaluate(doc.text(), context);
            String label = doc.label(cwd);
            if (verdict.passed()) {
                if (!o.quiet()) {
                    out.println("PASS " + label);
                }
                continue;
            }
            rejected++;
            List<Violation> found = verdict.violations();
            violations += found.size();
            out.println("FAIL " + label + " (" + found.size() + (found.size() == 1 ? " violation)" : " violations)"));
            for (Violation v : found) {
                String where = v.path().isEmpty() ? "" : v.path() + ": ";
                out.println("  [" + v.gate() + "] " + where + v.message());
                if (github) {
                    out.println(annotation(display(cwd, doc.file()), doc.line(), v));
                }
                summaryRows.add("| `" + md(label) + "` | " + md(v.gate()) + " | `" + md(v.path()) + "` | " + md(v.message()) + " |");
            }
        }
        int passed = documents.size() - rejected;
        String totals = documents.size() + (documents.size() == 1 ? " document" : " documents") + ": "
                + passed + " passed, " + rejected + " rejected, " + violations
                + (violations == 1 ? " violation" : " violations");
        out.println(totals);
        if (o.summary() != null) {
            writeSummary(o.summary(), totals, rejected == 0, summaryRows);
        }
        return rejected == 0 ? PASSED : REJECTED;
    }

    // --- inputs ----------------------------------------------------------------------------------

    List<Document> documents(List<String> inputs) {
        Set<Path> files = new LinkedHashSet<>();
        for (String input : inputs) {
            files.addAll(expand(input));
        }
        List<Document> out = new ArrayList<>();
        for (Path file : files) {
            String text = read(file);
            if (isJsonl(file)) {
                String[] lines = text.split("\\R", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (!lines[i].isBlank()) {
                        out.add(new Document(file, i + 1, lines[i]));
                    }
                }
            } else {
                out.add(new Document(file, 0, text));
            }
        }
        return out;
    }

    private List<Path> expand(String input) {
        if (isGlob(input)) {
            return glob(input);
        }
        Path path = cwd.resolve(input).normalize();
        if (Files.isDirectory(path)) {
            return walk(path, p -> isJson(p) || isJsonl(p));
        }
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        throw new IllegalArgumentException("input not found: " + input);
    }

    private List<Path> glob(String pattern) {
        String normalized = pattern.replace('\\', '/');
        // Walk from the longest leading part without a glob character.
        String[] parts = normalized.split("/");
        StringBuilder base = new StringBuilder();
        for (String part : parts) {
            if (isGlob(part)) {
                break;
            }
            base.append(part).append('/');
        }
        Path root = base.isEmpty() ? cwd : cwd.resolve(base.toString()).normalize();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        Path absolutePattern = Path.of(normalized).isAbsolute() ? Path.of(normalized) : cwd.resolve(normalized);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + absolutePattern.normalize());
        return walk(root, matcher::matches);
    }

    private static List<Path> walk(Path root, java.util.function.Predicate<Path> keep) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(keep).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + root + ": " + e.getMessage(), e);
        }
    }

    private static boolean isGlob(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0 || s.indexOf('[') >= 0 || s.indexOf('{') >= 0;
    }

    private static boolean isJson(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static boolean isJsonl(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jsonl") || name.endsWith(".ndjson");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** The source text for grounding, if {@code --sources} is set and the file exists. */
    static Optional<SourceDocument> source(Path dir, Document doc) {
        if (dir == null) {
            return Optional.empty();
        }
        String name = doc.file().getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        Path file = dir.resolve(doc.line() == 0 ? stem + ".txt" : stem + "-" + doc.line() + ".txt");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(SourceDocument.ofPages(read(file).split("\f", -1)));
    }

    // --- reporting -------------------------------------------------------------------------------

    /** A GitHub Actions workflow command that puts the violation on the file in the PR diff. */
    static String annotation(String file, int line, Violation v) {
        StringBuilder s = new StringBuilder("::error file=").append(property(file));
        if (line > 0) {
            s.append(",line=").append(line);
        }
        s.append(",title=").append(property("outputgate " + v.gate()));
        String where = v.path().isEmpty() ? "" : v.path() + ": ";
        return s.append("::").append(data(where + v.message())).toString();
    }

    private static String data(String s) {
        return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    private static String property(String s) {
        return data(s).replace(":", "%3A").replace(",", "%2C");
    }

    private static String md(String s) {
        return s.replace("|", "\\|").replace("`", "'").replace("\r", " ").replace("\n", " ");
    }

    private void writeSummary(Path file, String totals, boolean passed, List<String> rows) {
        StringBuilder s = new StringBuilder();
        s.append("### outputgate: ").append(passed ? "passed" : "rejected").append("\n\n").append(totals).append("\n\n");
        if (!rows.isEmpty()) {
            s.append("| Document | Gate | Field | Violation |\n|---|---|---|---|\n");
            int shown = Math.min(rows.size(), 200);
            rows.subList(0, shown).forEach(r -> s.append(r).append('\n'));
            if (rows.size() > shown) {
                s.append("\n").append(rows.size() - shown).append(" more violations are in the job log.\n");
            }
        }
        try {
            Files.writeString(file, s.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            err.println("outputgate: cannot write summary " + file + ": " + e.getMessage());
        }
    }

    private static String display(Path cwd, Path file) {
        Path abs = file.toAbsolutePath().normalize();
        String shown = abs.startsWith(cwd) ? cwd.relativize(abs).toString() : abs.toString();
        return shown.replace('\\', '/');
    }

    private static boolean isHelp(String a) {
        return a.equals("-h") || a.equals("--help") || a.equals("help");
    }

    private static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
