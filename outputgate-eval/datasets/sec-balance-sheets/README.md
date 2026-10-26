# Balance sheets from SEC 10-Q filings

A second benchmark dataset, unrelated to invoices: 60 real company balance sheets, each the
balance-sheet statement of the company's latest 10-Q on SEC EDGAR, as EDGAR's XBRL viewer
prints it. The same harness, corruption kinds and report as the FCC set; only the contract
([`BalanceSheet`](../../src/main/java/io/github/ravismash/outputgate/eval/BalanceSheet.java))
is new.

## Results (2026-10-26)

| Model | Real answers accepted | Accepted but wrong (vs XBRL) | Corruptions caught: naive / schema only / full contract |
|---|---|---|---|
| qwen2.5:14b (local Ollama) | 52/60 | 1 of 49 compared | 4/50 · 20/50 · **50/50** |
| gemini-3.5-flash | 58/60 | 0 of 56 | 4/50 · 20/50 · **50/50** |
| gemini-3.8-flash | 57/60 | 0 of 55 | 4/50 · 20/50 · **50/50** |
| gemini-3.1-pro-preview | 58/60 | 0 of 56 | 4/50 · 20/50 · **50/50** |

Each run's full report is `runs/<model>/benchmark.md` (and `.html`); the raw answers are in
`results.jsonl` and the corrupted set in `corrupted/`.

What the rejections were, read by hand:

- **IBM and TMO, every model.** The filings' own printed lines do not add up to their printed
  totals (by 2, from rounding each line to millions), which is more than the contract's
  tolerance of 1. These are false rejections: the contract is too strict for these two
  statements, not the models wrong.
- **BBY, qwen and gemini-3.8-flash.** The statement lists one current liability (contract
  liabilities) in a separate block below the main table; both models left it out, so the lines
  sum to 7,808, not 8,936. Pro found it. A correct rejection.
- **qwen only**: three more sums that do not match (GOOGL and TXN list a subtotal and the lines
  in it; TSLA adds two non-current items printed in blocks below the table) and two quotes that
  are not on the page (MO: a number that is not printed; NKE: "$ 37,794" where the page prints
  "37,794").

The one answer accepted but wrong: on DRI (amounts in millions with one decimal, "13,226.6"),
qwen wrote every amount scaled by 1,000 (13226600) while quoting the printed text. Every quote
grounds and every sum still holds, because all of them were scaled the same way. No gate checks
that a value is the number its own quote prints, so this kind of error gets through.

Ground truth covers 58 of the 60 filings; for PM and ZTS the download script could not read the
XBRL values, so they are not compared.

## How to reproduce

```bash
# Download (writes documents/, sources.tsv and ground-truth.json; standard library only)
python3 -I outputgate-eval/scripts/fetch_sec_balance_sheets.py outputgate-eval/datasets/sec-balance-sheets/documents --user-agent "your name you@example.com"

# Record a model's answers, then corrupt and report (corrupt and report never call a model)
./gradlew :outputgate-eval:run --args="record $PWD/outputgate-eval/datasets/sec-balance-sheets/documents $PWD/run --task balance-sheet --model qwen2.5:14b"
./gradlew :outputgate-eval:run --args="corrupt $PWD/outputgate-eval/datasets/sec-balance-sheets/documents $PWD/run --task balance-sheet --count 50 --seed 42"
./gradlew :outputgate-eval:run --args="report $PWD/outputgate-eval/datasets/sec-balance-sheets/documents $PWD/run --task balance-sheet"
```

`record` takes `--client ollama` (default), `--client openai` (any OpenAI-compatible server,
e.g. LM Studio) or `--client gemini` (key from `GEMINI_API_KEY` or `~/.gemini_api_key`), and
`--resume true` to continue a run that stopped. The filings are the latest 10-Q of each company
at download time, so a fresh download picks newer ones; `sources.tsv` lists the exact filings
used here.

Settings for these runs: temperature 0; Ollama with seed 42, `num_ctx` 16384 and the output
schema as `format`; Gemini through its OpenAI-compatible API with the output schema as
`response_format` (no seed; the API rejects one). The corrupted sets use 50 outputs, seed 42.
