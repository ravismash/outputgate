# Balance sheets from SEC 10-Q filings: held-out set

60 more company balance sheets, from companies that are not in
[`../sec-balance-sheets`](../sec-balance-sheets), fetched the same way. The contract
([`BalanceSheet`](../../src/main/java/io/github/ravismash/outputgate/eval/BalanceSheet.java)) was
frozen before any model saw these documents, and nothing was changed after. This set measures how the
contract tuned on the first 60 generalises.

## Real answers against ground truth (2026-10-26)

Ground truth is the filing's own XBRL values for total current assets, total assets, total current
liabilities and total liabilities and equity. The download script could read them for 55 of the 60
filings.

| Model | Accepted on the first answer | Accepted after one retry | Accepted but wrong | Schema only: accepted but wrong |
|---|---|---|---|---|
| gemini-3.1-pro-preview | 60/60 | 60/60 | 0 | 0 |
| gemini-3.5-flash | 60/60 | 60/60 | 0 | 0 |
| gemini-3.8-flash | 59/60 | 60/60 | 0 | 1 (KDP) |
| qwen2.5:14b (local, 16 GB Mac) | 56/60 | 57/60 | **1 (CTVA)** | 1 (CTVA) |

### Every rejection, read by hand

| Document | Model | What was wrong | Gate | After one retry |
|---|---|---|---|---|
| KDP | gemini-3.8-flash | Dropped a digit from total assets (8,761,900,000 for "87,619,000,000"); a plain schema accepts it | quote value, arithmetic | Fixed |
| ADP | qwen | Left out "funds held for clients" (46,413.1) and quoted several figures from the prior-year column | arithmetic, quote value | Not fixed: the retry changed the totals to match its own sums (7,821.0), and the quote check rejected that |
| EXC | qwen | Left out lines (they sum to 8,507; the page prints 9,797) and quoted a number that is not on the page | arithmetic, grounding | Not fixed: the retry changed both totals to match its own sums, which is wrong against ground truth; the quote check rejected it |
| MCHP | qwen | Listed two printed totals as current-liability lines | arithmetic | Fixed |
| PSX | qwen | Listed each section's printed total as a line and left out other lines | arithmetic | Not fixed |

### The retry trap, twice more

At ADP and EXC the retry did what qwen did at TSLA in the first set: it left its lines alone and
rewrote the printed totals to equal their sum. Schema and arithmetic pass after that edit. The
quote-value check rejects it, because each total still carries a quote of the number the page
prints. Across both sets, 3 of the 12 retries tried to pass this way and all 3 were rejected.

### The wrong answer that got through

CTVA's statement prints two balance sheets one after the other: Corteva's, then its subsidiary
EIDP's, with almost the same lines. qwen took Corteva's current assets and EIDP's current
liabilities (total 10,795; Corteva's is 10,789). Every quote is on the page, every number matches
its quote, and every sum holds, so the contract accepted it. Grounding checks that a value is in
the source, not that it comes from the right part of it. A check that all quotes lie inside one
section would catch this. It was not added before this run, because changing the contract after
seeing this set would stop it being a held-out test; the tables above are the frozen contract's.

### Fixed afterwards (2026-10-28): quotes scoped to the first statement

`ProvenanceGate.within(Region, fields...)` now requires a field's quote to lie inside a region of the
page, not just on it. The contract scopes the date and the four totals to the first statement, from
the top of the page through its first "liabilities and equity" total. qwen's 10,795 is printed only
in EIDP's statement, below that line, so CTVA is rejected. Line items are not scoped: some filings
print a breakdown of a line after the statement (BBY's deferred revenue, in the first set), and
models quote it correctly.

Re-scoring the recorded answers of all four models in both sets with `report` changes exactly one
verdict: qwen on CTVA, now rejected (qwen held out: 55/60 accepted, 0 accepted but wrong). Every
other verdict is the same, and every run still catches 50/50 corrupted outputs. This was written
after seeing CTVA, so it is a fix, not a held-out result.

## Files

- `documents/`: the 60 statements as text, `sources.tsv` (the exact filings) and `ground-truth.json`.
- `runs/<model>/`: `results.jsonl` (raw answers), `benchmark.md` and `.html` (scored report),
  `corrupted/` (the synthetic set), and `repair.md` and `.jsonl` (the retry).

To reproduce, follow [`../sec-balance-sheets`](../sec-balance-sheets#how-to-reproduce) with this
folder's `documents/`.
