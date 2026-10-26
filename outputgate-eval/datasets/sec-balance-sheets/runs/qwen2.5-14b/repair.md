# One retry with the gate errors: qwen2.5:14b (local Ollama, temperature 0, seed 42, num_ctx 16384, format: balance-sheet output schema)

6 recorded answers were rejected by the full contract; each got one retry with its violations in the prompt. 1 passed on the retry.

| document | first answer rejected by | retry | retry against ground truth |
|---|---|---|---|
| BBY-2026-08-01.txt | math | rejected: [math] sum of current_liabilities[*].amount is 7808 but total_current_liabilities is 8936 (allowed for rounding 3.0) | right on 4 fields |
| DRI-2026-08-30.txt | provenance | rejected: [provenance] value 220500 is not a number its quote prints (it prints 220.5); [provenance] value 118000 is not a number its quote prints (it prints 118.0) | wrong: total_assets: answer 13226600, ground truth 13226.6; total_current_assets: answer 953100, ground truth 953.1; total_current_liabilities: answer 3199000, ground truth 3199; total_liabilities_and_equity: answer 13226600, ground truth 13226.6 |
| GOOGL-2026-06-30.txt | math | passed | right on 4 fields |
| MO-2026-06-30.txt | provenance | rejected: [math] sum of current_liabilities[*].amount is 9629 but total_current_liabilities is 7770 (allowed for rounding 2.5); [provenance] source_span.text "5,484" not found in the source document (whitespace and currency signs ignored) | right on 4 fields |
| TSLA-2026-09-30.txt | math | rejected: [provenance] value 78902 is not a number its quote prints (it prints 70,402) | wrong: total_current_assets: answer 78902, ground truth 70402 |
| TXN-2026-09-30.txt | math | rejected: [math] sum of current_assets[*].amount is 21552 but total_current_assets is 16880 (allowed for rounding 4.5) | right on 4 fields |
