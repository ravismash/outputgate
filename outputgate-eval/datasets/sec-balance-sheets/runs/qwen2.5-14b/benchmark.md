# outputgate benchmark: balance-sheet

Model: qwen2.5:14b (local Ollama, temperature 0, seed 42, num_ctx 16384, format: balance-sheet output schema) · 60 documents, 60 with an answer, 50 corrupted outputs · 2026-10-26

## Results

|  | Naive (parse only) | Schema only | Full contract |
|---|---|---|---|
| Recorded answers accepted | 60/60 (100%) | 60/60 (100%) | 54/60 (90%) |
| Corrupted outputs caught | 4/50 (8%) | 20/50 (40%) | 50/50 (100%) |

## Caught, by kind of corruption

| Kind | What changed | n | Naive (parse only) | Schema only | Full contract |
|---|---|---|---|---|---|
| dropped-item | one element of a list left out (a line item, a deduction), totals kept | 5 | 0/5 | 0/5 | 5/5 |
| number-typo | two adjacent digits of a quoted amount swapped, quote kept | 5 | 0/5 | 0/5 | 5/5 |
| fabricated-quote | a text field quoted with words that are not in the document | 5 | 0/5 | 0/5 | 5/5 |
| wrong-page | a quote attributed to a page it is not on | 5 | 0/5 | 0/5 | 5/5 |
| bare-value | a field given as a bare value, without its quote | 5 | 0/5 | 0/5 | 5/5 |
| non-iso-date | a date left as printed (MM/dd/yy) instead of ISO | 5 | 0/5 | 0/5 | 5/5 |
| amount-as-text | an amount written as text ("$2,319.00") instead of a number | 4 | 0/4 | 4/4 | 4/4 |
| missing-field | a required field left out | 4 | 0/4 | 4/4 | 4/4 |
| extra-field | an unrequested field with the model's commentary | 4 | 0/4 | 4/4 | 4/4 |
| code-fence | the JSON wrapped in a sentence and a ```json fence | 4 | 4/4 | 4/4 | 4/4 |
| trailing-note | a note after the JSON object | 4 | 0/4 | 4/4 | 4/4 |

## Corrupted outputs the full contract let through (0)

- None.

## Recorded answers against the ground truth

The dataset's own values for 58 documents (total_assets, total_current_assets, total_current_liabilities, total_liabilities_and_equity). An accepted answer that differs is one a policy let through wrong.

|  | Naive (parse only) | Schema only | Full contract |
|---|---|---|---|
| Accepted, every compared field right | 57 | 57 | 52 |
| Accepted, some field wrong | 1 | 1 | 0 |

### Accepted by the full contract but wrong (0)

- None.

## Recorded answers the full contract rejected (6)

Each is either a model mistake the contract caught or a contract that is too strict. Read them by hand before quoting the numbers above.

- BBY-2026-08-01.txt: [math] total_current_liabilities: sum of current_liabilities[*].amount is 7808 but total_current_liabilities is 8936 (allowed for rounding 3.0)
- DRI-2026-08-30.txt: [provenance] current_assets[0].amount: value 220500 is not a number its quote prints (it prints 220.5); [provenance] current_assets[1].amount: value 118000 is not a number its quote prints (it prints 118.0); [provenance] current_assets[2].amount: value 321000 is not a number its quote prints (it prints 321.0); and 12 more
- GOOGL-2026-06-30.txt: [math] total_current_assets: sum of current_assets[*].amount is 585998 but total_current_assets is 343524 (allowed for rounding 3.5)
- MO-2026-06-30.txt: [provenance] current_liabilities[2].amount: source_span.text "3,625" not found in the source document (whitespace and currency signs ignored)
- TSLA-2026-09-30.txt: [math] total_current_assets: sum of current_assets[*].amount is 78902 but total_current_assets is 70402 (allowed for rounding 4.0)
- TXN-2026-09-30.txt: [math] total_current_assets: sum of current_assets[*].amount is 21552 but total_current_assets is 16880 (allowed for rounding 4.5)

## How to read this

- The three columns are three ways to decide whether to trust the same model answers. Naive accepts anything a default JSON parser reads as an object (it ignores text after the object). Schema only runs the task's JSON Schema. Full contract runs every gate.
- "Accepted" is not "correct": the ground-truth table shows how many accepted answers were right on the fields the dataset labels. Fields it does not label are not checked there.
- Corrupted outputs are copies of recorded answers the full contract accepted, each with exactly one defect added. The kinds of defect were written knowing what the gates check, so the caught rate measures coverage of these kinds of mistake, not of every mistake a model makes.

