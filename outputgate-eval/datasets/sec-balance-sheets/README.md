# Balance sheets from SEC 10-Q filings

60 real company balance sheets, each the balance-sheet statement of the company's latest 10-Q on
SEC EDGAR as EDGAR's XBRL viewer prints it, with the filing's own XBRL values as ground truth.
It is the second dataset for the eval harness, after invoices. The harness is the same; only the
contract ([`BalanceSheet`](../../src/main/java/io/github/ravismash/outputgate/eval/BalanceSheet.java))
is new.

## Real answers against ground truth (2026-10-26)

Each model extracted the period date, the current-asset and current-liability lines and four
totals from every statement. The contract decides whether to accept each answer. Accepted answers
are then compared with the XBRL values for total current assets, total assets, total current
liabilities and total liabilities and equity. Those values were available for 58 of the 60 filings.

| Model | Accepted on the first answer | Accepted after one retry | Accepted but wrong |
|---|---|---|---|
| gemini-3.1-pro-preview | 60/60 | 60/60 | 0 |
| gemini-3.5-flash | 60/60 | 60/60 | 0 |
| gemini-3.8-flash | 59/60 | 60/60 | 0 |
| qwen2.5:14b (local, 16 GB Mac) | 53/60 | 55/60 | 0 |

**Accepted but wrong is 0 for every model, retries included.** Every accepted answer that has
ground truth matches it on all four totals. **The cost is the answers the contract turns away:**
one for gemini-3.8-flash and seven for qwen on the first pass, five for qwen after a retry. Each
of those is a document a pipeline would route to a person instead.

### Every rejection, read by hand

| Document | Model | What was wrong | Gate | After one retry |
|---|---|---|---|---|
| BBY | qwen, gemini-3.8-flash | One current liability is printed in a separate block below the main table; the answer left it out, so the lines sum to 7,808, not 8,936 | arithmetic | 3.8-flash fixed it; qwen did not |
| DRI | qwen | Every amount multiplied by 1,000 (13,226,600 for "13,226.6"), with the printed text quoted | quote value | Not fixed: the retry scaled again |
| GOOGL | qwen | Listed a subtotal and the lines inside it | arithmetic | Fixed |
| TXN | qwen | Listed a subtotal and the lines inside it | arithmetic | Not fixed |
| TSLA | qwen | Added two non-current items printed in blocks below the table | arithmetic | Not fixed: the retry changed the total to match its own sum, and the quote check rejected that |
| MO | qwen | Quoted a number that is not on the page | grounding | Not fixed |
| NKE | qwen | Quoted "$ 37,794" where the page prints "37,794" | grounding | Fixed |

Every rejection is a real error: in each case the answer was wrong or not supported by the page.
After the changes below, no correct answer is rejected.

### Two changes made after seeing these results

Both changes were made to fix failures found in this run, so this dataset is not an independent
test of them:

1. **`numbersMatchQuotes()` (new).** On DRI, qwen's scaled answer passed the first contract: every
   quote was on the page, and the sums still held because every amount was scaled the same way.
   The provenance gate now also checks that a numeric value is a number its own quote prints.
   That turned DRI from the one wrong answer accepted into a rejection.
2. **`sumEqualsRounded()` (new).** IBM's and TMO's printed lines miss their printed totals by 2,
   because each figure is rounded to millions. The first contract allowed a difference of 1, so it
   rejected correct answers from every model. Sums now allow half a unit of the last printed digit
   for each figure. With the old rules, every model had those 2 extra rejections, and qwen's DRI
   answer was accepted wrong.

## The retry loop

`repair` gives every rejected answer one more call. The prompt contains the original document, the
rejected answer and every violation word for word, for example `sum of current_liabilities[*].amount
is 7808 but total_current_liabilities is 8936`. The retry is judged by the same contract.

3 of the 8 rejected answers were fixed, and all 3 match ground truth. In one case (TSLA) the
model tried to pass by changing a printed total to match its own wrong sum. The quote check caught
it, because the new total is not the number on the page. A retry loop is only safe if the checks
can't be satisfied by changing values to fit.

## Synthetic corruptions (sanity check)

For each model, 50 accepted answers were copied with one defect added. The defect kinds were written
knowing what the gates check, so this shows that each gate fires on the error it targets. It does not
measure the errors models make in practice; the tables above do.

| Defect | n | Plain JSON Schema | Full contract | What catches it |
|---|---|---|---|---|
| Line left out, totals kept | 5 | missed | caught | arithmetic (a hand-written validator could do this too) |
| Two digits of an amount swapped | 5 | missed | caught | arithmetic and the quote-value check |
| Quote not in the document | 5 | missed | caught | grounding against the source document |
| Quote attributed to the wrong page | 5 | missed | caught | grounding against the source document |
| Value with no quote | 5 | missed | caught | provenance shape check |
| Date not in ISO format | 5 | missed | caught | a regex here; `"format": "date"` in the schema would also catch it |
| Amount as text, missing field, extra field | 12 | caught | caught | schema |
| Code fence, trailing note | 8 | caught | caught | strict parsing |

Naive parsing caught 4 of the 50, plain schema 20 and the full contract 50, for every model. What
needs the source document is grounding and the quote-value check. The rest could be written by hand
with any validation library; outputgate packages it as one contract with readable messages that a
retry prompt can quote.

## Files

- `documents/`: the 60 statements as text, `sources.tsv` (the exact filings) and `ground-truth.json`
  (XBRL values; the download script could not read them for PM and ZTS).
- `runs/<model>/`: `results.jsonl` (raw answers), `benchmark.md` and `.html` (scored report),
  `corrupted/` (the synthetic set), and `repair.md` and `.jsonl` (the retry).

## How to reproduce

```bash
D=$PWD/outputgate-eval/datasets/sec-balance-sheets/documents; R=$PWD/run
python3 -I outputgate-eval/scripts/fetch_sec_balance_sheets.py $D --user-agent "your name you@example.com"  # optional: fresh download
./gradlew :outputgate-eval:run --args="record  $D $R --task balance-sheet --model qwen2.5:14b"   # calls the model
./gradlew :outputgate-eval:run --args="repair  $D $R --task balance-sheet --model qwen2.5:14b"   # one retry per rejection
./gradlew :outputgate-eval:run --args="corrupt $D $R --task balance-sheet --count 50 --seed 42"  # offline
./gradlew :outputgate-eval:run --args="report  $D $R --task balance-sheet"                       # offline
```

`record` and `repair` take `--client ollama` (default), `--client openai` (any OpenAI-compatible
server) or `--client gemini` (key from `GEMINI_API_KEY` or `~/.gemini_api_key`). `record --resume true`
continues a run that stopped. Settings used here: temperature 0, with the output shape given as a
JSON Schema. Ollama ran with seed 42 and `num_ctx` 16384. Gemini ran through its OpenAI-compatible
API, which rejects a seed, so none was sent. A fresh download picks newer filings, so use
`sources.tsv` to get the same ones.
