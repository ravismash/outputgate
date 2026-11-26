# SEC balance sheets: schema-valid is not correct

An [Inspect](https://inspect.aisi.org.uk/) eval built from the Outputgate benchmark. The model
reads the balance-sheet statement of a company's latest SEC 10-Q, as EDGAR's XBRL viewer prints
it, and extracts the period date, the current-asset and current-liability lines and four totals as
JSON. The four totals are checked against the filing's own XBRL values.

The question it answers: **how often is a schema-valid answer still wrong, and how many of those
wrong answers can checks without ground truth catch?**

## Data

- `tuning`: 60 filings, 58 with XBRL values for all four totals. The Outputgate contract was tuned
  on this set.
- `holdout`: 60 other filings, 55 with all four totals, run with the contract frozen.
- `all`: both, 113 samples.

The statements and ground truth live in this repository
([`outputgate-eval/datasets`](../outputgate-eval/datasets)), so a pinned commit pins the data.
They were fetched with
[`fetch_sec_balance_sheets.py`](../outputgate-eval/scripts/fetch_sec_balance_sheets.py); every
filing's EDGAR URL is in `documents/sources.tsv`.

## Scores

| Score | Meaning |
|---|---|
| `correct` | All four totals equal the XBRL values |
| `schema_valid` | The answer parses and matches [`balance-sheet.schema.json`](../outputgate-eval/src/main/resources/schemas/balance-sheet.schema.json). One surrounding code fence is tolerated |
| `consistent` | Passes checks that need no ground truth: lines add up to their totals within rounding (half a unit of the last printed digit per figure), total assets equal total liabilities and equity, current totals are within the overall totals, ISO date |
| `valid_but_wrong` | Schema-valid but not correct |
| `valid_but_wrong_caught` | Of those, rejected by the consistency checks |
| `valid_but_wrong_missed` | Of those, accepted by the consistency checks, so only ground truth finds them |

`valid_but_wrong_missed` is the failure a schema plus arithmetic cannot see. The best-known
example is an answer with every amount scaled by 1,000: the sums still hold. The Outputgate
contract catches that with a quote check (every number must equal the number its quote prints),
which this eval leaves out so that it can run against any model's plain JSON.

## Running it

```bash
git clone https://github.com/ravismash/outputgate
cd outputgate
uv sync
uv run inspect eval evals/sec_balance_sheets/sec_balance_sheets.py --model openai/gpt-4o-mini
uv run inspect eval evals/sec_balance_sheets/sec_balance_sheets.py -T split=holdout --model ollama/qwen2.5:14b
```

Tests: `uv run --extra test pytest`.
