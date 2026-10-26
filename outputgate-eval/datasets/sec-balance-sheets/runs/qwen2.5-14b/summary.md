# Eval run: qwen2.5:14b (local Ollama, temperature 0, seed 42, num_ctx 16384, format: balance-sheet output schema)

- When: 2026-10-25T23:41:00.744153+05:30
- Documents: 60 from `/Users/raviprakameena/Desktop/outputgate/outputgate-eval/datasets/sec-balance-sheets/documents`
- Outcomes: PASSED: 52 · REJECTED: 8 · NO_TEXT: 0 · ERROR: 0
- Output tokens: 39263 · model time: 5823 s

## Rejections by gate (documents where the gate failed)

| gate | documents |
|---|---|
| math | 6 |
| provenance | 2 |

## Every document

| file | pages | outcome | output tokens | seconds | failing gates |
|---|---|---|---|---|---|
| AAPL-2026-06-27.txt | 1 | PASSED | 697 | 97 |  |
| ABBV-2026-06-30.txt | 1 | PASSED | 512 | 77 |  |
| ABT-2026-06-30.txt | 1 | PASSED | 708 | 95 |  |
| ADBE-2026-08-28.txt | 1 | PASSED | 650 | 100 |  |
| AMD-2026-06-27.txt | 1 | PASSED | 592 | 74 |  |
| AMGN-2026-06-30.txt | 1 | PASSED | 515 | 77 |  |
| AMZN-2026-06-30.txt | 1 | PASSED | 538 | 72 |  |
| AVGO-2026-08-02.txt | 1 | PASSED | 555 | 65 |  |
| BBY-2026-08-01.txt | 1 | REJECTED | 588 | 69 | math |
| BDX-2026-06-30.txt | 1 | PASSED | 611 | 97 |  |
| BMY-2026-06-30.txt | 1 | PASSED | 562 | 87 |  |
| CL-2026-06-30.txt | 1 | PASSED | 549 | 79 |  |
| CMG-2026-06-30.txt | 1 | PASSED | 770 | 146 |  |
| COST-2026-05-10.txt | 1 | PASSED | 642 | 110 |  |
| CRM-2026-07-31.txt | 1 | PASSED | 614 | 86 |  |
| CSCO-2026-04-25.txt | 1 | PASSED | 765 | 117 |  |
| DELL-2026-07-31.txt | 1 | PASSED | 649 | 99 |  |
| DG-2026-07-31.txt | 1 | PASSED | 682 | 105 |  |
| DHR-2026-09-25.txt | 1 | PASSED | 532 | 76 |  |
| DLTR-2026-08-01.txt | 1 | PASSED | 531 | 73 |  |
| DRI-2026-08-30.txt | 1 | PASSED | 734 | 109 |  |
| GILD-2026-06-30.txt | 1 | PASSED | 590 | 83 |  |
| GIS-2026-08-30.txt | 1 | PASSED | 626 | 80 |  |
| GOOGL-2026-06-30.txt | 1 | REJECTED | 727 | 114 | math |
| HD-2026-08-02.txt | 1 | PASSED | 768 | 100 |  |
| HPQ-2026-07-31.txt | 1 | PASSED | 551 | 75 |  |
| HSY-2026-06-28.txt | 1 | PASSED | 672 | 88 |  |
| IBM-2026-09-30.txt | 1 | REJECTED | 950 | 136 | math |
| INTC-2026-06-27.txt | 1 | PASSED | 604 | 91 |  |
| JNJ-2026-09-27.txt | 1 | PASSED | 742 | 109 |  |
| KMB-2026-06-30.txt | 1 | PASSED | 622 | 90 |  |
| KO-2026-07-03.txt | 1 | PASSED | 744 | 108 |  |
| LLY-2026-06-30.txt | 1 | PASSED | 690 | 119 |  |
| LOW-2026-07-31.txt | 1 | PASSED | 692 | 125 |  |
| MCD-2026-06-30.txt | 1 | PASSED | 645 | 96 |  |
| MDLZ-2026-06-30.txt | 1 | PASSED | 708 | 122 |  |
| MDT-2026-07-31.txt | 1 | PASSED | 647 | 92 |  |
| META-2026-09-30.txt | 1 | PASSED | 544 | 76 |  |
| MO-2026-06-30.txt | 1 | REJECTED | 548 | 74 | provenance |
| MRK-2026-06-30.txt | 1 | PASSED | 728 | 94 |  |
| MSFT-2026-03-31.txt | 1 | PASSED | 717 | 89 |  |
| NKE-2026-08-31.txt | 1 | REJECTED | 633 | 85 | provenance |
| NVDA-2026-07-26.txt | 1 | PASSED | 629 | 74 |  |
| ORCL-2026-08-31.txt | 1 | PASSED | 664 | 100 |  |
| PEP-2026-09-05.txt | 1 | PASSED | 543 | 73 |  |
| PFE-2026-06-28.txt | 1 | PASSED | 750 | 98 |  |
| PG-2026-09-30.txt | 1 | PASSED | 524 | 85 |  |
| PM-2026-09-30.txt | 1 | PASSED | 902 | 139 |  |
| QCOM-2026-06-28.txt | 1 | PASSED | 677 | 96 |  |
| ROST-2026-08-01.txt | 1 | PASSED | 724 | 115 |  |
| SBUX-2026-06-28.txt | 1 | PASSED | 741 | 114 |  |
| SYK-2026-06-30.txt | 1 | PASSED | 683 | 114 |  |
| TGT-2026-08-01.txt | 1 | PASSED | 473 | 86 |  |
| TJX-2026-08-01.txt | 1 | PASSED | 656 | 139 |  |
| TMO-2026-06-27.txt | 1 | REJECTED | 692 | 99 | math |
| TSLA-2026-09-30.txt | 1 | REJECTED | 711 | 109 | math |
| TXN-2026-09-30.txt | 1 | REJECTED | 750 | 107 | math |
| WMT-2026-07-31.txt | 1 | PASSED | 758 | 109 |  |
| YUM-2026-06-30.txt | 1 | PASSED | 557 | 81 |  |
| ZTS-2026-06-30.txt | 1 | PASSED | 685 | 98 |  |

## Every violation and error

- `BBY-2026-08-01.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 7808 but total_current_liabilities is 8936 (tolerance 1)
- `GOOGL-2026-06-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 585998 but total_current_assets is 343524 (tolerance 1)
- `IBM-2026-09-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 31190 but total_current_assets is 31192 (tolerance 1)
- `MO-2026-06-30.txt` [provenance] current_liabilities[2].amount: source_span.text "3,625" not found in the source document (whitespace ignored)
- `NKE-2026-08-31.txt` [provenance] total_liabilities_and_equity: source_span.text "$ 37,794" not found in the source document (whitespace ignored)
- `TMO-2026-06-27.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 15071 but total_current_liabilities is 15069 (tolerance 1)
- `TSLA-2026-09-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 78902 but total_current_assets is 70402 (tolerance 1)
- `TXN-2026-09-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 21552 but total_current_assets is 16880 (tolerance 1)
