# Functional Requirements: outputgate-py

**Project:** `outputgate`, deterministic checks for structured LLM output, ported to Python and Pydantic v2.
**Target:** Python 3.10+, Pydantic v2, and anything that validates through Pydantic (Instructor, FastAPI, agent pipelines).
**Implementation:** [`python/outputgate.py`](python/outputgate.py), with an example in
[`python/examples/balance_sheet.py`](python/examples/balance_sheet.py) and tests in [`python/tests`](python/tests).

## 1. Objective

An LLM's structured answer can match its schema and still be wrong: a value retyped, scaled or
invented, a line left out, a total that no longer adds up. Retry loops make one failure worse. Told
that its numbers do not add up, a model can pass the check by rewriting the printed total to equal
its own wrong sum instead of fixing the items. The eval runs saw this three times in twelve retries
(TSLA, ADP, EXC); a dropped digit (KDP) and a uniform ×1,000 scale (DRI) are the other numeric errors
models made.

outputgate-py checks a structured answer before it reaches application state, with no model call:
each value must be quoted from the source document, each number must be one its quote prints, and
cross-field arithmetic must hold within the rounding the source itself used. It does the same job as
the Java library's `ProvenanceGate` and `MathGate`, as Pydantic types and validators.

Nothing in the library is specific to one kind of document. Financial statements (the SEC 10-Q
balance sheets in `outputgate-eval`) are the eval dataset and the example. Any answer that cites a
source and has parts that add up to totals is checked the same way.

## 2. Scope

**In scope (v1)**

- Pydantic v2 evidence types: `GroundedNumber`, `GroundedText`, and a `Grounded` base for others.
- Grounding of every quote against a source document passed through Pydantic's validation context.
- A check that each number is one its own quote prints.
- Cross-field rules with rounding tolerance (`sum_equals_rounded`) and plain ones (`equals`, `at_most`,
  `matches`, `forbids`), on a `GroundedModel` that reports every violation at once.
- Error messages written to be quoted in a retry prompt.

**Out of scope (v1)**

- Layout-aware PDF extraction (bounding boxes); the source is text, split into pages.
- Section scoping: checking that all quotes come from the same table or section. Grounding proves a
  value is in the source, not that it comes from the right part of it. This is the one wrong answer
  the Java contract accepted on the held-out set (CTVA, where two near-identical statements are
  printed one after the other).
- Packaging and publishing to PyPI; v1 is a single module.

## 3. Functional requirements

### FR-1: Grounded values

- **FR-1.1 Evidence.** A grounded value is an object `{"value": ..., "source_span": {"page": n, "text":
  "<quote>"}}`, the same shape as the Java library's evidence objects. `page` is a whole number of 1
  or more and `text` is non-empty. A bare value, a missing quote and unknown keys are rejected.
- **FR-1.2 Grounding.** The quote must occur in the source document, on the page it names. By
  default all whitespace is ignored and every kind of hyphen or dash reads as `-`, in both the quote
  and the page; everything else (digits, separators, punctuation, case) must match exactly. A
  `Source` option also ignores currency signs, so `"$ 37,794"` grounds against a page that prints
  `37,794`. A quote on another page is rejected, and the message names the page it is on.
- **FR-1.3 Numbers in a quote.** Numbers are read from the quote with `,` as grouping and `.` as the
  decimal point. The sign is not compared, since documents print negatives as `(1,234.5)`,
  `-1,234.5` or `1,234.5 CR`; the sign is left to other checks.
- **FR-1.4 Value matches quote.** A `GroundedNumber`'s value must equal one of the numbers its quote
  prints. The comparison is exact, in decimal, not a floating-point tolerance: `8,761,900,000` against a
  quote of `87,619,000,000` differs by one digit and must fail however small the relative error.
  This is what stops a retry from rewriting a total (TSLA) and catches scale errors (DRI, KDP). It is
  on by default and can be turned off per `Source`.
- **FR-1.5 Fail closed.** Validating a grounded value with no source document in the context is a
  failure, never a pass. So are non-numeric values for a number (`"123"`, `true`, `NaN`).

### FR-2: Cross-field arithmetic with rounding tolerance

- **FR-2.1 Sum.** `sum_equals_rounded(items, total, ...)` adds the items and compares the sum with
  the total.
- **FR-2.2 Rounding tolerance.** When each figure was rounded where it was printed, the items may miss
  the total by half a unit of the last printed digit per figure, the total included:

  `allowed = 0.5 × unit × (N_items + 1)`

  where `unit` is one in the last decimal place any of the figures has (1 for whole numbers, 0.1 for
  one decimal place), or a fixed `tolerance` if that is larger. IBM's and TMO's statements miss their
  totals by 2 for this reason, and a fixed tolerance of 1 rejected correct answers from every model.
- **FR-2.3 Retry guidance.** A failed sum says what to check and what to leave alone: *"sum of
  current_assets[*].amount is 78902 but total_current_assets is 70402 (allowed for rounding 4.0).
  Check the items against the source: one may be missing, extra, a subtotal or from another column.
  Keep total_current_assets as the source prints it."* A failed quote match says to use the number the
  quote prints and not to change a value to make another check pass.
- **FR-2.4 Other rules.** `equals` (within a tolerance), `at_most` (a part never exceeds its whole),
  `matches` (a whole value matches a pattern) and `forbids` (no prose or placeholders in a value).
  `assert_sum_equals_rounded` raises `ValueError` for use in a plain Pydantic validator.

### FR-3: Every violation at once

- A `GroundedModel` declares its rules in `checks()`. Validation reports every failure in one
  `ValidationError`: field and shape errors, grounding, quote values and the cross-field rules, each
  with its path. The rules run even when a field fails grounding, as long as the answer has the right
  shape, so a retry prompt sees the quote problems and the arithmetic together (at KDP: two quote
  mismatches and two `at_most` failures).
- `describe(error)` renders the violations as `[kind] path: message` lines for a retry prompt.

### FR-4: Context passthrough

- The source document travels in Pydantic's validation context:
  `Model.model_validate_json(answer, context={"source_text": text})`, or
  `context={"source": Source(pages, ignore_currency_signs=True)}` for options. A `source_text` is
  split into pages on form feeds. No global state, so it works unchanged in Instructor's validation
  context and in a FastAPI handler.
- The models' JSON Schema describes `value` as a number and the span's fields in words, so a client
  that sends the schema to the model (Instructor, structured output) steers it toward the evidence shape.

## 4. Non-functional requirements

- **NFR-1 Performance.** No model or network call. Validating a 15-figure balance sheet with its
  source takes about 0.15 ms (median, Python 3.13); the target is under 5 ms for 20 fields.
- **NFR-2 Dependencies.** `pydantic>=2.1` only. 2.0 converts JSON floats to `Decimal` through binary
  floating point (`146.3` becomes `146.3000000000000113...`), which breaks exact quote matching; the
  tests pass on 2.1 through the latest 2.x.
- **NFR-3 Extensibility.** Subclass `GroundedModel` for a document type and `Grounded` for a new kind
  of evidence (override `_value_problem` for its value-against-quote check). The balance sheet in
  `python/examples` is written this way and nothing in the module refers to it.

## 5. Verification

The tests run on the answers the models actually gave in the Java eval runs, read from
`outputgate-eval/datasets/sec-balance-sheets{,-holdout}/runs/*/{results,repair}.jsonl`, against the
statements they were extracted from.

1. **TSLA (retry rewrites a total).** qwen2.5:14b's first answer added two non-current items, so the
   lines sum to 78,902 against a printed 70,402: rejected by arithmetic. Its retry changed the total
   to 78,902 and kept the quote `"70,402"`. The sum then holds, and the quote check rejects it.
2. **KDP (dropped digit).** gemini-3.8-flash gave 8,761,900,000 for the printed `87,619,000,000` in
   total assets and in total liabilities and equity: rejected by both quote checks and by both
   `at_most` rules, since current assets and current liabilities then exceed their totals.
3. **IBM and TMO (rounding).** Every model's answer, whose lines miss the printed total by 2, is
   accepted.
4. **Parity with the Java contract.** On all 480 recorded answers (60 documents × 4 models × 2
   datasets) the Python checks reject exactly the 12 the Java contract rejects (BBY twice, DRI,
   GOOGL, MO, TSLA, TXN, KDP, ADP, EXC, MCHP, PSX) and accept the rest. On the 12 retries they accept
   exactly the 4 the Java contract accepts.

Unit tests cover each rule on small made-up answers: grounding per page, whitespace, hyphen and
currency options, value-against-quote, evidence shape, fail-closed without a source, the rounding
allowance and the combined report.
