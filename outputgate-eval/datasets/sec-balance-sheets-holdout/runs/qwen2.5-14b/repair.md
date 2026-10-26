# One retry with the gate errors: qwen2.5:14b (local Ollama, temperature 0, seed 42, num_ctx 16384, format: balance-sheet output schema)

4 recorded answers were rejected by the full contract; each got one retry with its violations in the prompt. 1 passed on the retry.

| document | first answer rejected by | retry | retry against ground truth |
|---|---|---|---|
| ADP-2026-03-31.txt | math, provenance | rejected: [provenance] value 7821.0 is not a number its quote prints (it prints 12,266.5) | – |
| EXC-2026-06-30.txt | math, provenance | rejected: [provenance] source_span.text "1,827" not found in the source document (whitespace and currency signs ignored); [provenance] value 8507 is not a number its quote prints (it prints 9,797) | wrong: total_current_assets: answer 8507, ground truth 9797; total_current_liabilities: answer 5524, ground truth 9008 |
| MCHP-2026-06-30.txt | math | passed | right on 4 fields |
| PSX-2026-06-30.txt | math | rejected: [math] sum of current_assets[*].amount is 36725 but total_current_assets is 24221 (allowed for rounding 2.5); [math] sum of current_liabilities[*].amount is 24659 but total_current_liabilities is 18354 (allowed for rounding 3.0) | right on 4 fields |
