# outputgate check

Deterministic checks for structured LLM output in CI. Point it at the JSON your model produces
(or the golden files and fixtures your tests use) and a contract, and the job fails with every
violation listed, annotated on the file in the pull request. **No check calls a model**, so the
same input always gets the same verdict.

A contract composes:

- **JSON Schema 2020-12**: shape, required fields, types, enums.
- **Regex rules**: whole-value patterns, forbidden text, real ISO-8601 dates, ISO 4217 codes.
- **Cross-field math**: items sum to a total, totals equal the sum of parts, products, ranges,
  with a tolerance and a rounding-aware sum for figures printed in thousands or percentages.
- **Provenance**: fields carry a quote and page from the source, and the quote really is on that
  page (optionally ignoring whitespace, currency signs, typography and digit grouping).

It runs the [outputgate](https://github.com/ravismash/outputgate) Java library (on Maven Central)
through its command-line runner, so the checks in CI are the same ones a JVM service runs at
runtime.

## Usage

```yaml
- uses: actions/checkout@v7.0.1
- uses: ravismash/outputgate-action@v1
  with:
    contract: contracts/survey.yaml
    inputs: |
      llm-outputs/**/*.json
      eval/runs.jsonl
```

### Inputs

| Input | Default | |
|---|---|---|
| `contract` | (required) | Contract file, YAML or JSON. |
| `inputs` | (required) | Files, directories or globs, one per line. `.json` is one document, `.jsonl` one per line. |
| `sources` | | Directory of source text for grounding: `<name>.txt` for `<name>.json`, `<name>-<line>.txt` for a `.jsonl` line. Form feeds split pages. |
| `version` | `0.2.0` | outputgate release whose CLI to download (checksum verified). |
| `jar` | | A local CLI jar to use instead of a release. |
| `java-version` | `17` | Java to install; `none` uses the runner's. |
| `summary` | `true` | Add a table of violations to the job summary. |
| `quiet` | `false` | Log only rejected documents. |

The step fails when any document is rejected, and also when the contract is invalid or the
inputs match nothing, so a typo can't turn the check into a silent pass.

## Contract

```yaml
schema: survey.schema.json            # path relative to this file, or the schema inline
regex:
  - require: { path: survey_id, pattern: "SV-\\d{4}" }
  - forbid: { path: "options[*].label", pattern: "(?i)lorem ipsum" }
  - isoDate: closed_on
  - currencyCode: currency
math:
  tolerance: 0.01
  rules:
    - sumEquals: { items: "options[*].count", total: total_responses }
    - sumEqualsRounded: { items: "rows[*].value", total: total }
    - equalsSum: { total: total, parts: [subtotal, tax] }
    - productEquals: { result: "lines[*].amount", factors: ["lines[*].qty", "lines[*].price"] }
    - range: { path: "options[*].share", min: 0, max: 1 }
provenance:
  require: [survey_id, total_responses]
  optional: [notes]
  grounded: ignoring-whitespace       # true, false or ignoring-whitespace
  numbersMatchQuotes: true
  ignoringCurrencySigns: true
  ignoringTypography: true
  joiningHyphenatedLines: true
  ignoringDigitGrouping: true
```

Paths are dot-separated field names, and `name[*]` covers every element of an array. Unknown
keys are errors, never skipped rules. Fields with provenance use the evidence shape
`{"value": ..., "source_span": {"page": 1, "text": "..."}}`; every other gate reads such a field
as its `value`.

## Running it locally

The same runner is attached to each outputgate release:

```sh
java -jar outputgate-cli-0.2.0.jar check --contract contracts/survey.yaml "llm-outputs/**/*.json"
```

Exit status: 0 all passed, 1 some rejected, 2 contract or usage error, or no input.

## License

MIT
