# outputgate

[![CI](https://github.com/ravismash/outputgate/actions/workflows/ci.yml/badge.svg)](https://github.com/ravismash/outputgate/actions/workflows/ci.yml)

**Deterministic contract tests for LLM output on the JVM.**

outputgate lets a team write JUnit tests for LLM features using deterministic checks that fail
the build. A contract composes gates (JSON schema, strict type binding, cross-field arithmetic,
source-span provenance, regex, cost and latency ceilings). An output either passes every gate
or is rejected with every violation listed. **No gate calls a model.**

> **Status:** `0.1.0-SNAPSHOT`, not yet on Maven Central. Java 21. MIT.

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
| `MathGate` | Cross-field arithmetic with an absolute tolerance: `sumEquals`, `equalsSum`, `productEquals`, `range` and custom rules | `.productEquals("line_items[*].amount", "line_items[*].quantity", "line_items[*].unit_price")` |
| `ProvenanceGate` | Required fields carry evidence, `{value, source_span: {page, text}}`. With `grounded()`, each span's text must occur literally in the source document | `.require("total", "line_items[*].amount").grounded()` |
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

## Benchmark

Not run yet. The plan: 200 public invoices plus 50 corrupted ones, comparing a naive prompt,
schema-only checks and the full contract.

## License

[MIT](LICENSE)
