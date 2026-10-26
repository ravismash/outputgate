# outputgate benchmark: balance-sheet

Model: qwen2.5:14b (local Ollama, temperature 0, seed 42, num_ctx 16384, format: balance-sheet output schema) · 60 documents, 60 with an answer, 50 corrupted outputs · 2026-10-26

## Results

|  | Naive (parse only) | Schema only | Full contract |
|---|---|---|---|
| Recorded answers accepted | 60/60 (100%) | 60/60 (100%) | 56/60 (93%) |
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

The dataset's own values for 55 documents (total_assets, total_current_assets, total_current_liabilities, total_liabilities_and_equity). An accepted answer that differs is one a policy let through wrong.

|  | Naive (parse only) | Schema only | Full contract |
|---|---|---|---|
| Accepted, every compared field right | 54 | 54 | 51 |
| Accepted, some field wrong | 1 | 1 | 1 |

### Accepted by the full contract but wrong (1)

- CTVA-2026-06-30.txt: total_current_liabilities: answer 10795, ground truth 10789

## Recorded answers the full contract rejected (4)

Each is either a model mistake the contract caught or a contract that is too strict. Read them by hand before quoting the numbers above.

- ADP-2026-03-31.txt: [math] total_current_assets: sum of current_assets[*].amount is 7821.0 but total_current_assets is 54234.1 (allowed for rounding 1); [math] total_current_liabilities: sum of current_liabilities[*].amount is 5245.2 but total_current_liabilities is 52019.7 (allowed for rounding 1); [provenance] total_current_assets: value 54234.1 is not a number its quote prints (it prints 43,252.2); and 11 more
- EXC-2026-06-30.txt: [math] total_current_assets: sum of current_assets[*].amount is 8507 but total_current_assets is 9797 (allowed for rounding 3.0); [math] total_current_liabilities: sum of current_liabilities[*].amount is 5524 but total_current_liabilities is 9008 (allowed for rounding 4.5); [provenance] current_assets[4].amount: source_span.text "1,827" not found in the source document (whitespace and currency signs ignored)
- MCHP-2026-06-30.txt: [math] total_current_liabilities: sum of current_liabilities[*].amount is 16996.2 but total_current_liabilities is 1294.4 (allowed for rounding 1)
- PSX-2026-06-30.txt: [math] total_current_assets: sum of current_assets[*].amount is 36725 but total_current_assets is 24221 (allowed for rounding 2.5); [math] total_current_liabilities: sum of current_liabilities[*].amount is 24659 but total_current_liabilities is 18354 (allowed for rounding 3.0)

## How to read this

- The three columns are three ways to decide whether to trust the same model answers. Naive accepts anything a default JSON parser reads as an object (it ignores text after the object). Schema only runs the task's JSON Schema. Full contract runs every gate.
- "Accepted" is not "correct": the ground-truth table shows how many accepted answers were right on the fields the dataset labels. Fields it does not label are not checked there.
- Corrupted outputs are copies of recorded answers the full contract accepted, each with exactly one defect added. The kinds of defect were written knowing what the gates check, so the caught rate measures coverage of these kinds of mistake, not of every mistake a model makes.

