# Eval run: qwen2.5:14b (local Ollama, temperature 0, seed 42, num_ctx 16384, format: balance-sheet output schema)

- When: 2026-10-26T17:44:24.556354+05:30
- Documents: 60 from `/Users/raviprakameena/Desktop/outputgate/outputgate-eval/datasets/sec-balance-sheets-holdout/documents`
- Outcomes: PASSED: 56 · REJECTED: 4 · NO_TEXT: 0 · ERROR: 0
- Output tokens: 40581 · model time: 5476 s

## Rejections by gate (documents where the gate failed)

| gate | documents |
|---|---|
| math | 4 |
| provenance | 2 |

## Every document

| file | pages | outcome | output tokens | seconds | failing gates |
|---|---|---|---|---|---|
| ACN-2026-05-31.txt | 1 | PASSED | 786 | 114 |  |
| ADP-2026-03-31.txt | 1 | REJECTED | 802 | 100 | math, provenance |
| ADSK-2026-07-31.txt | 1 | PASSED | 700 | 89 |  |
| AMAT-2026-07-26.txt | 1 | PASSED | 559 | 66 |  |
| ANET-2026-06-30.txt | 1 | PASSED | 634 | 76 |  |
| APD-2026-06-30.txt | 1 | PASSED | 722 | 96 |  |
| AZO-2026-05-09.txt | 1 | PASSED | 615 | 83 |  |
| BKNG-2026-06-30.txt | 1 | PASSED | 581 | 73 |  |
| BSX-2026-06-30.txt | 1 | PASSED | 574 | 71 |  |
| CARR-2026-06-30.txt | 1 | PASSED | 591 | 69 |  |
| CDNS-2026-06-30.txt | 1 | PASSED | 526 | 63 |  |
| CI-2026-06-30.txt | 1 | PASSED | 646 | 81 |  |
| CMI-2026-06-30.txt | 1 | PASSED | 796 | 97 |  |
| CPRT-2026-04-30.txt | 1 | PASSED | 772 | 88 |  |
| CTAS-2026-08-31.txt | 1 | PASSED | 765 | 91 |  |
| CTVA-2026-06-30.txt | 1 | PASSED | 631 | 90 |  |
| CVS-2026-06-30.txt | 1 | PASSED | 757 | 103 |  |
| DOW-2026-09-30.txt | 1 | PASSED | 718 | 109 |  |
| DUK-2026-06-30.txt | 1 | PASSED | 850 | 117 |  |
| ECL-2026-06-30.txt | 1 | PASSED | 624 | 82 |  |
| EL-2026-03-31.txt | 1 | PASSED | 536 | 78 |  |
| EMR-2026-06-30.txt | 1 | PASSED | 526 | 64 |  |
| ETN-2026-06-30.txt | 1 | PASSED | 630 | 87 |  |
| EW-2026-06-30.txt | 1 | PASSED | 691 | 94 |  |
| EXC-2026-06-30.txt | 1 | REJECTED | 761 | 109 | math, provenance |
| FAST-2026-09-30.txt | 1 | PASSED | 678 | 88 |  |
| FTNT-2026-06-30.txt | 1 | PASSED | 665 | 86 |  |
| GLW-2026-06-30.txt | 1 | PASSED | 537 | 76 |  |
| GWW-2026-06-30.txt | 1 | PASSED | 630 | 76 |  |
| HCA-2026-06-30.txt | 1 | PASSED | 565 | 78 |  |
| HUM-2026-06-30.txt | 1 | PASSED | 627 | 80 |  |
| IDXX-2026-06-30.txt | 1 | PASSED | 646 | 103 |  |
| INTU-2026-04-30.txt | 1 | PASSED | 808 | 108 |  |
| ISRG-2026-09-30.txt | 1 | PASSED | 641 | 80 |  |
| ITW-2026-06-30.txt | 1 | PASSED | 571 | 78 |  |
| KDP-2026-06-30.txt | 1 | PASSED | 847 | 138 |  |
| KHC-2026-06-27.txt | 1 | PASSED | 702 | 93 |  |
| KLAC-2026-03-31.txt | 1 | PASSED | 676 | 94 |  |
| LIN-2026-06-30.txt | 1 | PASSED | 642 | 84 |  |
| LRCX-2026-09-27.txt | 1 | PASSED | 685 | 87 |  |
| LULU-2026-08-02.txt | 1 | PASSED | 815 | 103 |  |
| MCHP-2026-06-30.txt | 1 | REJECTED | 597 | 80 | math |
| MCK-2026-06-30.txt | 1 | PASSED | 575 | 79 |  |
| MNST-2026-06-30.txt | 1 | PASSED | 807 | 106 |  |
| MPC-2026-06-30.txt | 1 | PASSED | 658 | 108 |  |
| MSI-2026-07-04.txt | 1 | PASSED | 585 | 85 |  |
| MU-2026-05-28.txt | 1 | PASSED | 564 | 72 |  |
| NOW-2026-06-30.txt | 1 | PASSED | 636 | 89 |  |
| NUE-2026-07-04.txt | 1 | PASSED | 647 | 83 |  |
| ODFL-2026-06-30.txt | 1 | PASSED | 738 | 114 |  |
| ORLY-2026-06-30.txt | 1 | PASSED | 806 | 123 |  |
| PANW-2026-04-30.txt | 1 | PASSED | 696 | 92 |  |
| PAYX-2026-08-31.txt | 1 | PASSED | 948 | 127 |  |
| PH-2026-03-31.txt | 1 | PASSED | 677 | 129 |  |
| PSX-2026-06-30.txt | 1 | REJECTED | 609 | 75 | math |
| REGN-2026-06-30.txt | 1 | PASSED | 644 | 78 |  |
| RMD-2026-03-31.txt | 1 | PASSED | 713 | 95 |  |
| ROK-2026-06-30.txt | 1 | PASSED | 655 | 91 |  |
| ROP-2026-06-30.txt | 1 | PASSED | 762 | 92 |  |
| SHW-2026-06-30.txt | 1 | PASSED | 736 | 90 |  |

## Every violation and error

- `ADP-2026-03-31.txt` [math] total_current_assets: sum of current_assets[*].amount is 7821.0 but total_current_assets is 54234.1 (allowed for rounding 1)
- `ADP-2026-03-31.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 5245.2 but total_current_liabilities is 52019.7 (allowed for rounding 1)
- `ADP-2026-03-31.txt` [provenance] total_current_assets: value 54234.1 is not a number its quote prints (it prints 43,252.2)
- `ADP-2026-03-31.txt` [provenance] total_assets: value 64484.2 is not a number its quote prints (it prints 53,369.3)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[0].amount: value 136.1 is not a number its quote prints (it prints 169.1)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[1].amount: value 3013.1 is not a number its quote prints (it prints 3,092.4)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[2].amount: value 777.9 is not a number its quote prints (it prints 973.1)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[3].amount: value 676.8 is not a number its quote prints (it prints 620.6)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[4].amount: value 269.1 is not a number its quote prints (it prints 262.8)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[5].amount: value 0.0 is not a number its quote prints (it prints 38.4)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[6].amount: value 0.0 is not a number its quote prints (it prints 4,769.5)
- `ADP-2026-03-31.txt` [provenance] current_liabilities[7].amount: value 372.2 is not a number its quote prints (it prints 9.1)
- `ADP-2026-03-31.txt` [provenance] total_current_liabilities: value 52019.7 is not a number its quote prints (it prints 41,278.3)
- `ADP-2026-03-31.txt` [provenance] total_liabilities_and_equity: value 64484.2 is not a number its quote prints (it prints 53,369.3)
- `EXC-2026-06-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 8507 but total_current_assets is 9797 (allowed for rounding 3.0)
- `EXC-2026-06-30.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 5524 but total_current_liabilities is 9008 (allowed for rounding 4.5)
- `EXC-2026-06-30.txt` [provenance] current_assets[4].amount: source_span.text "1,827" not found in the source document (whitespace and currency signs ignored)
- `MCHP-2026-06-30.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 16996.2 but total_current_liabilities is 1294.4 (allowed for rounding 1)
- `PSX-2026-06-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 36725 but total_current_assets is 24221 (allowed for rounding 2.5)
- `PSX-2026-06-30.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 24659 but total_current_liabilities is 18354 (allowed for rounding 3.0)
