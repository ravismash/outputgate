# outputgate

[![CI](https://github.com/ravismash/outputgate/actions/workflows/ci.yml/badge.svg)](https://github.com/ravismash/outputgate/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ravismash/outputgate-core)](https://central.sonatype.com/artifact/io.github.ravismash/outputgate-core)

**Deterministic contract tests for LLM output on the JVM.**

outputgate lets a team write JUnit tests for LLM features using deterministic checks that fail
the build. A contract composes gates (JSON schema, strict type binding, cross-field arithmetic,
source-span provenance, regex, cost and latency ceilings). An output either passes every gate
or is rejected with every violation listed. **No gate calls a model.**

> **Status:** `0.1.0` on [Maven Central](https://central.sonatype.com/namespace/io.github.ravismash). Java 21. MIT.

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

Output can match its JSON Schema and still be wrong. Here a model extracted an invoice, dropped
a line item, kept the old subtotal and apologised inside a field. The schema is happy. The
contract is not:

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
| `ProvenanceGate` | Required fields carry evidence, `{value, source_span: {page, text}}`. With `grounded()`, each span's text must occur literally on the page it names; `optional(...)` fields may be null but are checked when present; `groundedIgnoringWhitespace()` ignores spaces and line breaks and reads any hyphen or dash as `-`, and still requires everything else to match exactly (for PDF or OCR text); `ignoringCurrencySigns()` also lets a quote add or drop a currency sign; `numbersMatchQuotes()` requires each numeric value to be a number its own quote prints, so a value scaled or retyped away from a real quote is caught | `.require("total", "line_items[*].amount").grounded()` |
| `RegexGate` | Text rules per field: `require` (whole value), `forbid` (any part), `isoDate`, `currencyCode` (ISO 4217) | `.isoDate("invoice_date").currencyCode("currency")` |
| `BudgetGate` | Ceilings on the call: cost, latency, output tokens (inclusive) | `.maxCost(0.01).maxLatency(Duration.ofSeconds(5))` |

Before any gate runs, output that is not exactly one JSON value is rejected as `json-parse`:
prose around a code block, trailing text, duplicate keys and empty output included.

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
| `outputgate-spring-ai` | `SpringAiLlmClient` | Over a Spring AI `ChatClient`. Built and tested on Spring AI 1.1.8; also smoke-tested against 2.0.1 |
| `outputgate-langchain4j` | `LangChain4jLlmClient` | Over a LangChain4j `ChatModel` (1.22.0) |
| `outputgate-ollama` | `OllamaLlmClient` | Ollama's HTTP API with the JDK `HttpClient`; no extra dependency |

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

## Example

[`outputgate-example`](outputgate-example) is a complete invoice extraction: a JSON Schema, a typed
`Invoice` record, a synthetic two-page invoice, a contract that uses the schema, type, math,
grounded provenance and regex gates, and 7 golden cases (clean, dropped line item, fabricated
quote, missing field, apology, non-ISO date, chatty prose). It runs fully offline with no API key.

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

No LLM-as-judge, semantic similarity, toxicity or relevance scoring. No runtime request
filtering module (you can still call `Contract.evaluate` at runtime). No web UI, hosted service
or telemetry. No prompt management, retries or re-asking the model.

## Releasing

Push a tag such as `v0.1.0`. The [Release workflow](.github/workflows/release.yml) builds and
tests at that version, signs the five library modules, publishes them to Maven Central and creates
the GitHub release. It needs the four repository secrets listed at the top of that file.

## Benchmark

Four models extracted 60 real company balance sheets from SEC 10-Q filings. Every answer the
contract accepted was then compared with the filing's own XBRL values:

| Model | Accepted on the first answer | Accepted after one retry with the gate errors | Accepted but wrong |
|---|---|---|---|
| gemini-3.1-pro-preview | 60/60 | 60/60 | 0 |
| gemini-3.5-flash | 60/60 | 60/60 | 0 |
| gemini-3.8-flash | 59/60 | 60/60 | 0 |
| qwen2.5:14b (local) | 53/60 | 55/60 | 0 |

No wrong answer was accepted. Every rejection was a real mistake: a missed line, a subtotal
counted twice, amounts scaled by 1,000, or a quote not on the page. One retry fixed 3 of the 8. Two
gates were added after this run exposed gaps: `numbersMatchQuotes()` and `sumEqualsRounded()`.
The dataset's [README](outputgate-eval/datasets/sec-balance-sheets) lists every rejection,
the retry results, those two changes, and a synthetic-corruption sanity check. The report is
[outputgate-balance-sheet-benchmark.pdf](https://github.com/ravismash/outputgate/releases/download/v0.1.0/outputgate-balance-sheet-benchmark.pdf).

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
