# outputgate

[![CI](https://github.com/ravismash/outputgate/actions/workflows/ci.yml/badge.svg)](https://github.com/ravismash/outputgate/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ravismash/outputgate-core)](https://central.sonatype.com/artifact/io.github.ravismash/outputgate-core)

**Deterministic contract tests for structured LLM output on the JVM.**

outputgate checks the JSON a model returns: records extracted from documents, form fields,
financial statements, tool-call arguments, anything with a shape and rules. A contract composes
gates (JSON schema, strict type binding, cross-field arithmetic, source-span provenance, regex,
cost and latency ceilings). An output either passes every gate or is rejected with every violation
listed, so the same contract fails a JUnit build, scores a golden set, or decides at runtime whether
to accept an answer, retry it or send it to a person. **No gate calls a model.**

**Does it work?** On 120 real SEC balance sheets and four models, the full contract caught 50 of 50
seeded defects per run where a JSON Schema caught 20. A JSON Schema accepted 3 real answers that
were wrong against the filings' own figures; the contract accepted 1, on the held-out set, and
rejects it since a fix made after seeing it. When a retry loop tried to pass by editing totals to
match its own wrong sums, the quote check caught all 3 attempts. Details, and what the numbers
don't show, are under [Benchmark](#benchmark); every failure is written up in
[How LLMs fail at SEC 10-Q balance sheet extraction](https://ravismash.github.io/outputgate/sec-10q-llm-extraction-failures.html).

> **Status:** `0.1.0` on [Maven Central](https://central.sonatype.com/namespace/io.github.ravismash). Java 21. MIT.
>
> **Python:** grounding and arithmetic checks for Pydantic v2 are in [`python/`](python) (not yet on PyPI); see [`FRD.md`](FRD.md).

## Install

The modules are on Maven Central under `io.github.ravismash`. Gradle:

```kotlin
dependencies {
    implementation("io.github.ravismash:outputgate-core:0.1.0")
    testImplementation("io.github.ravismash:outputgate-junit:0.1.0")
    // One adapter, if you call a model through it:
    // implementation("io.github.ravismash:outputgate-spring-ai:0.1.0")
    // implementation("io.github.ravismash:outputgate-langchain4j:0.1.0")
    // implementation("io.github.ravismash:outputgate-ollama:0.1.0")
}
```

Maven:

```xml
<dependency>
    <groupId>io.github.ravismash</groupId>
    <artifactId>outputgate-core</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.ravismash</groupId>
    <artifactId>outputgate-junit</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

`outputgate-example` and `outputgate-eval` (the harness that reads real PDF invoices and runs
models over them) stay in this repository and are not published.

## Why

Output can match its JSON Schema and still be wrong. Take any record with parts that must add up
and text that must come from the source; here it is an invoice. The model dropped a line item,
kept the old subtotal and apologised inside a field. The schema is happy. The contract is not:

```java
Contract contract = Contract.builder()
        .gate(SchemaGate.fromString("""
                {"type": "object", "required": ["vendor", "subtotal", "line_items"]}"""))
        .gate(MathGate.builder()
                .sumEquals("line_items[*].amount", "subtotal")
                .tolerance(0.01)
                .build())
        .gate(RegexGate.builder()
                .forbid("line_items[*].description", "(?i)\\b(sorry|as an ai)\\b")
                .build())
        .build();

Verdict verdict = contract.evaluate("""
        {"vendor": "Acme", "subtotal": 100.00,
         "line_items": [{"description": "Widgets", "amount": 40.00},
                        {"description": "Sorry, I could not read this line", "amount": 34.50}]}""");
```

```
REJECTED
[math] subtotal: sum of line_items[*].amount is 74.50 but subtotal is 100.00 (tolerance 0.01)
[regex] line_items[1].description: forbidden pattern /(?i)\b(sorry|as an ai)\b/ matched "Sorry"
```

The code and that output are run as a test on every build
([`ReadmeExamplesTest`](outputgate-example/src/test/java/io/github/ravismash/outputgate/example/ReadmeExamplesTest.java)).

## Gates

| Gate | Checks | Example |
|---|---|---|
| `SchemaGate` | JSON Schema 2020-12, from a string or the classpath. The schema itself is checked against the 2020-12 meta-schema when the gate is built | `SchemaGate.fromClasspath("schemas/invoice.schema.json")` |
| `TypeGate<T>` | Strict binding to a Java type: unknown properties, null primitives, missing record/constructor properties, float-for-int and text-for-number all fail, with Jackson's message | `TypeGate.of(Invoice.class)` |
| `MathGate` | Cross-field arithmetic with an absolute tolerance: `sumEquals`, `equalsSum`, `productEquals`, `range` and custom rules; `sumEqualsRounded` also allows half a unit of the last printed digit per figure, for figures rounded where they were printed | `.productEquals("line_items[*].amount", "line_items[*].quantity", "line_items[*].unit_price")` |
| `ProvenanceGate` | Required fields carry evidence, `{value, source_span: {page, text}}`. With `grounded()`, each span's text must occur literally on the page it names; `optional(...)` fields may be null but are checked when present; `groundedIgnoringWhitespace()` ignores spaces and line breaks and reads any hyphen or dash as `-`, and still requires everything else to match exactly (for PDF or OCR text); `ignoringCurrencySigns()` also lets a quote add or drop a currency sign; `ignoringTypography()`, `joiningHyphenatedLines()` and `ignoringDigitGrouping()` fold ligatures, words broken across lines and thousands separators (see [PDF and OCR text](#pdf-and-ocr-text)); `numbersMatchQuotes()` requires each numeric value to be a number its own quote prints, so a value scaled or retyped away from a real quote is caught; `within(region, fields...)` requires those fields' quotes to lie inside a `Region` of the document (from a line matching one pattern through or up to a line matching another), so a value copied from a neighbouring table or section that prints the same labels is caught | `.require("total", "line_items[*].amount").grounded()`, `.within(Region.from("^Line items").before("^Notes"), "total")` |
| `RegexGate` | Text rules per field: `require` (whole value), `forbid` (any part), `isoDate`, `currencyCode` (ISO 4217) | `.isoDate("invoice_date").currencyCode("currency")` |
| `BudgetGate` | Ceilings on the call: cost, latency, output tokens (inclusive) | `.maxCost(0.01).maxLatency(Duration.ofSeconds(5))` |

Before any gate runs, output that is not exactly one JSON value is rejected as `json-parse`:
prose around a code block, trailing text, duplicate keys and empty output included.

### PDF and OCR text

Grounding compares a quote with the document's extracted text, and text from a PDF or OCR differs
from what a model types in ways that carry no content. Each option below folds one named kind of
difference, in the quote and the document alike. None makes matching fuzzy, and nothing ever
matches a different letter or digit:

| Option | Folds | Still has to match |
|---|---|---|
| `groundedIgnoringWhitespace()` | spaces, tabs, line breaks, no-break spaces; any hyphen, dash or minus as `-` | everything else |
| `ignoringCurrencySigns()` | `$`, `€`, `£`, `₹` and the rest, present or absent | the digits |
| `ignoringTypography()` | ligatures (`ﬁ` → `fi`), full-width forms (`１２３`), digits of other scripts (`٣` → `3`), curly quotes, `…`, odd spaces, soft hyphens and zero-width characters | case, superscripts, and OCR look-alikes: `0` and `O`, `1` and `l` are different characters |
| `joiningHyphenatedLines()` | a word the page breaks across lines (`inter-` / `national`); the page as printed still matches too | numbers, codes and capitalised words are never joined |
| `ignoringDigitGrouping()` | `,`, `'` and thin or narrow no-break spaces between a digit and exactly three more (`1,234,567` = `1'234'567` = `1234567`); also for `numbersMatchQuotes()` | `.` is never folded (it is a decimal point too often), nor is a plain space (it separates table columns) |

A quote that is still wrong after folding (a misread digit, a letter for a zero) is rejected, and
the message says which folds were applied. When OCR is poor enough that faithful quotes still
fail, fix the text extraction rather than loosen the contract: a rejection costs a retry or a
person's look, an accepted wrong value costs more.

Paths use `field.sub` and `array[*]`. Apart from `ProvenanceGate`, which requires them, every gate
reads `{"value": ...}` evidence objects and bare values the same way.

**Fail-closed everywhere.** A missing or `null` value, a gate that throws, a gate that returns
nothing, unknown usage with a ceiling set, grounding without a source document, and a gate
configured with no rules are all failures, never passes. Every gate always runs, so a verdict
lists every violation.

## In JUnit

`outputgate-junit` adds assertions, golden sets and an HTML report.

```java
LlmAssert.assertThat(output).passes(contract);
LlmAssert.assertThat(output).isRejectedBy(contract).byGate("math");
```

**Golden sets** are JSONL files, one recorded model output per line (outputs shortened here):

```json
{"name": "clean extraction", "output": {...}, "expected": {"subtotal": 74.50}}
{"name": "dropped line item", "output": {...}, "expect_reject": "math"}
```

`expected` lists fields the output must contain (numbers compare by value). `expect_reject` names
the gate that must reject it, or `true` for any gate. Unknown keys, duplicate names and invalid
lines fail with the file and line number.

```java
@ParameterizedTest
@GoldenSetSource("golden/invoices.jsonl")      // one named test per line
void invoice(GoldenCase c) {
    LlmAssert.assertThat(c).withContext(context).matches(contract);
}

@Test
void passRate() {
    GoldenRunner.of(contract).withContext(context)
            .run("golden/invoices.jsonl")      // also writes build/outputgate/invoices.html
            .assertPassRateAtLeast(1.0);
}
```

The report is a single self-contained HTML file with one column per gate, ✓/✗ per case and every
violation.

## Calling a model

Gates never call a model; an `LlmClient` does, and returns the text plus its `Usage` (cost,
latency, output tokens) so `BudgetGate` can check it.

| Module | Client | Notes |
|---|---|---|
| `outputgate-core` | `FakeLlmClient` | Canned replies for offline tests; an unknown prompt is an error |
| `outputgate-spring-ai` | `SpringAiLlmClient` | Over a Spring AI `ChatClient` |
| `outputgate-langchain4j` | `LangChain4jLlmClient` | Over a LangChain4j `ChatModel` |
| `outputgate-ollama` | `OllamaLlmClient` | Ollama's HTTP API with the JDK `HttpClient`; no extra dependency |

The Spring AI and LangChain4j adapters are one class each, under 60 lines, over one framework
interface. On every pull request CI runs each adapter's tests, including a real recorded model
answer replayed through the framework's own Ollama client, on every version below:

| Adapter | Published against | Also tested in CI on |
|---|---|---|
| `outputgate-spring-ai` | Spring AI 1.1.8 | 1.0.9, 2.0.2 |
| `outputgate-langchain4j` | LangChain4j 1.22.0 | 1.11.11, 1.23.0 |

With Maven, the framework version you declare wins over the adapter's; Gradle picks the higher of
the two unless you pin yours (`version { strictly("1.0.9") }`). To check another
release, run `./gradlew :outputgate-spring-ai:test -PspringAiVersion=<version>` (or
`:outputgate-langchain4j:test -Plangchain4jVersion=<version>`), and add it to the matrix in
[`ci.yml`](.github/workflows/ci.yml).

Providers report tokens, not money, so cost comes from `TokenPricing.perMillionTokens(input, output)`
(`TokenPricing.FREE` for a local model). A missing token count means unknown cost, never zero.

```java
LlmClient client = OllamaLlmClient.builder()
        .model("qwen2.5:14b")
        .temperature(0.0)
        .pricing(TokenPricing.FREE)
        .build();

LlmOutput output = client.complete(prompt);
contract.evaluate(output, EvalContext.empty().withSource(document));
```

## Examples

[`outputgate-example`](outputgate-example) has three complete extractions, each a typed record, a
source document, a contract and a golden set. They run fully offline with no API key.

| Example | Document | What it shows | Golden cases |
|---|---|---|---|
| [`BalanceSheetExample`](outputgate-example/src/main/java/io/github/ravismash/outputgate/example/BalanceSheetExample.java) | A real SEC 10-Q balance sheet (Corteva, June 2026) that prints two near-identical statements | Rounding-aware sums, the statement balances, every amount is the number its own quote prints, totals quoted from the right statement (`within`) | 6: two recorded model answers (one correct; one that mixes the two statements and passes every check but the region), a retry-trap edit, amounts scaled by 1,000, a prior-year total, a non-ISO date |
| [`LabReportExample`](outputgate-example/src/main/java/io/github/ravismash/outputgate/example/LabReportExample.java) | A synthetic lab report laid out as OCR text: a ligature, a hyphenated line break, a narrow-space thousands separator | A form-like record grounded with the [PDF and OCR folds](#pdf-and-ocr-text); without them the faithful answer fails three times | 7: clean, lost decimal point, a test the report doesn't have, letter O for zero, non-ISO date, wrong unit, missing field |
| [`InvoiceExample`](outputgate-example/src/main/java/io/github/ravismash/outputgate/example/InvoiceExample.java) | A synthetic two-page invoice | Schema, type, products and sums, grounded provenance, regex | 7: clean, dropped line item, fabricated quote, missing field, apology, non-ISO date, chatty prose |

Each adapter also has a test that replays a real `qwen2.5:14b` answer, recorded from a local
Ollama, through that framework's own client. That answer passes the whole invoice contract.

## Dependencies

`outputgate-core` depends only on Jackson (`jackson-databind`) and `slf4j-api` at runtime. The
JSON Schema validator (networknt 1.5.9) is shaded and relocated inside the jar, so it cannot
clash with the networknt version Spring AI brings (2.x or 3.x). Adapters are separate modules,
so core stays light.

## Build and test

```bash
./gradlew build
```

This runs every test offline. One test calls a real local Ollama and runs only when asked:

```bash
OUTPUTGATE_LIVE_OLLAMA=1 ./gradlew :outputgate-ollama:test
```

## Not in scope for 0.1

No LLM-as-judge, semantic similarity, toxicity or relevance scoring: outputgate checks what can be
written as an invariant (shape, arithmetic, provenance, patterns, budgets), and is meant to sit
beside a judge for tone or relevance, not replace one. No runtime request filtering module (you
can still call `Contract.evaluate` at runtime). No web UI, hosted service or telemetry. No prompt
management, retries or re-asking the model.

## Releasing

Push a tag such as `v0.1.0`. The [Release workflow](.github/workflows/release.yml) builds and
tests at that version, signs the five library modules, publishes them to Maven Central and creates
the GitHub release. It needs the four repository secrets listed at the top of that file.

## Benchmark

### What the contract adds over a schema

The same recorded answers, scored three ways. Each run also gets 50 corrupted copies of answers
the contract accepted, one seeded defect each (a dropped line, swapped digits, a quote not in the
document, a wrong page, a bare value, a non-ISO date, an amount as text, a missing or extra field,
a code fence, a trailing note):

| Dataset | Naive parse | JSON Schema only | Full contract |
|---|---|---|---|
| SEC balance sheets, 4 models × 2 sets: corruptions caught per run | 4/50 | 20/50 | **50/50** in all 8 runs |
| SEC balance sheets, 4 models × 2 sets: real answers accepted but wrong against the filing's XBRL values (of 452 with ground truth) | 3 | 3 (DRI, KDP, CTVA) | 0 in sample (tuned there); 1 held out (CTVA), 0 after the fix below |
| Scanned FCC political-ad invoices, qwen2.5:14b: corruptions caught | 3/50 | 18/50 | **50/50** |

The defect kinds were written knowing what the gates check, so the corruption rows show that each
gate fires on the error it targets, not how often models make it. The real-answer rows do. The FCC
invoices (40 public records, CC BY-NC, kept out of this repository) have no ground truth, and many
of their real answers were rejected for PDF-text quoting, the gap the
[PDF and OCR folds](#pdf-and-ocr-text) narrow.

### The retry trap

**A retry loop will game a validator that only checks the output against itself.** A common
pattern for structured LLM output is to validate, send the errors back and ask again. In this
benchmark a model's line items added up to 78,902 while the document printed a total of 70,402.
Told that the sum didn't match, the retry left the lines alone and changed the total to 78,902.
Schema and arithmetic now pass. outputgate rejected it because every value carries a quote from
the source, and 78,902 is not the number its quote prints. That happened in 3 of the 12 retries
here, and the quote check caught all 3. A retry loop is only safe when the checks are tied to the
source document.

### Balance sheets against ground truth

Four models extracted 60 real company balance sheets from SEC 10-Q filings. The contract was then
frozen and run on 60 other companies. Every accepted answer was compared with the filing's own
XBRL values:

| Model | In-sample: first answer / after one retry / accepted but wrong | Held out: first answer / after one retry / accepted but wrong |
|---|---|---|
| gemini-3.1-pro-preview | 60/60 · 60/60 · 0 | 60/60 · 60/60 · 0 |
| gemini-3.5-flash | 60/60 · 60/60 · 0 | 60/60 · 60/60 · 0 |
| gemini-3.8-flash | 59/60 · 60/60 · 0 | 59/60 · 60/60 · 0 |
| qwen2.5:14b (local) | 54/60 · 55/60 · 0 | 56/60 · 57/60 · 1 |

Every rejection was a real mistake, and a plain JSON Schema would have accepted all of them. The
one wrong answer that got through (CTVA, held out) is a page that prints two balance sheets, the
parent's and a subsidiary's: qwen took one block from each. Every quote was on the page and
everything added up, so the frozen contract couldn't tell. The table above is that frozen
contract's result. Afterwards `ProvenanceGate.within(Region, ...)` was added, which requires the
date and totals to be quoted from the first statement on the page. On the recorded answers it
rejects CTVA and changes no other verdict, but since it was written after seeing CTVA, that is a
fix, not a held-out result. Three gates were added after the in-sample run exposed gaps:
`numbersMatchQuotes()`, `sumEqualsRounded()` and `ignoringCurrencySigns()`. The READMEs for the
[in-sample](outputgate-eval/datasets/sec-balance-sheets) and
[held-out](outputgate-eval/datasets/sec-balance-sheets-holdout) sets list every rejection and
retry. Every failure, with
the retry-trap, ×1,000 and wrong-statement cases, is on
[this page](https://ravismash.github.io/outputgate/sec-10q-llm-extraction-failures.html). The report is
[outputgate-balance-sheet-benchmark.pdf](https://github.com/ravismash/outputgate/releases/download/v0.1.0/outputgate-balance-sheet-benchmark.pdf).

### Run it on your own data

The harness is in [`outputgate-eval`](outputgate-eval). It scores the same model answers three
ways (naive parse-only, schema only, the full contract) on a folder of documents, plus a
corrupted set made from them. Another dataset needs only a new contract. For example, on a folder
of invoice PDFs:

```bash
D=/abs/path/to/invoices; R=/abs/path/to/run
./gradlew :outputgate-eval:runFccOllama -Ppdfs=$D -Pout=$R           # the only step that calls a model (local Ollama)
./gradlew :outputgate-eval:run --args="corrupt $D $R --count 50 --seed 42"   # offline
./gradlew :outputgate-eval:run --args="report  $D $R"                        # offline: $R/benchmark.md and .html
```

`corrupt` copies answers the full contract accepts and adds exactly one defect to each, spread
over 13 kinds: a dropped line item, swapped digits, a re-formatted or invented quote, a wrong
page, a bare value, a non-ISO date, an apology, an amount as text, a missing or extra field, a
code fence and a trailing note. The same answers, count and seed give the same files. The report
lists every answer the contract rejected and every corruption it let through.

## License

[MIT](LICENSE)
