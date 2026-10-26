# Eval run: gemini-3.5-flash (Gemini API, temperature 0, response_format: balance-sheet output schema)

- When: 2026-10-26T11:17:49.802169+05:30
- Documents: 60 from `/Users/raviprakameena/Desktop/outputgate/outputgate-eval/datasets/sec-balance-sheets/documents`
- Outcomes: PASSED: 58 · REJECTED: 2 · NO_TEXT: 0 · ERROR: 0
- Output tokens: 41118 · model time: 678 s

## Rejections by gate (documents where the gate failed)

| gate | documents |
|---|---|
| math | 2 |

## Every document

| file | pages | outcome | output tokens | seconds | failing gates |
|---|---|---|---|---|---|
| AAPL-2026-06-27.txt | 1 | PASSED | 568 | 9 |  |
| ABBV-2026-06-30.txt | 1 | PASSED | 802 | 10 |  |
| ABT-2026-06-30.txt | 1 | PASSED | 721 | 12 |  |
| ADBE-2026-08-28.txt | 1 | PASSED | 519 | 14 |  |
| AMD-2026-06-27.txt | 1 | PASSED | 609 | 7 |  |
| AMGN-2026-06-30.txt | 1 | PASSED | 803 | 9 |  |
| AMZN-2026-06-30.txt | 1 | PASSED | 449 | 14 |  |
| AVGO-2026-08-02.txt | 1 | PASSED | 458 | 8 |  |
| BBY-2026-08-01.txt | 1 | PASSED | 674 | 33 |  |
| BDX-2026-06-30.txt | 1 | PASSED | 492 | 8 |  |
| BMY-2026-06-30.txt | 1 | PASSED | 864 | 9 |  |
| CL-2026-06-30.txt | 1 | PASSED | 563 | 7 |  |
| CMG-2026-06-30.txt | 1 | PASSED | 1184 | 11 |  |
| COST-2026-05-10.txt | 1 | PASSED | 657 | 8 |  |
| CRM-2026-07-31.txt | 1 | PASSED | 492 | 7 |  |
| CSCO-2026-04-25.txt | 1 | PASSED | 1166 | 13 |  |
| DELL-2026-07-31.txt | 1 | PASSED | 977 | 12 |  |
| DG-2026-07-31.txt | 1 | PASSED | 686 | 11 |  |
| DHR-2026-09-25.txt | 1 | PASSED | 549 | 10 |  |
| DLTR-2026-08-01.txt | 1 | PASSED | 550 | 8 |  |
| DRI-2026-08-30.txt | 1 | PASSED | 579 | 10 |  |
| GILD-2026-06-30.txt | 1 | PASSED | 606 | 8 |  |
| GIS-2026-08-30.txt | 1 | PASSED | 969 | 8 |  |
| GOOGL-2026-06-30.txt | 1 | PASSED | 513 | 20 |  |
| HD-2026-08-02.txt | 1 | PASSED | 817 | 12 |  |
| HPQ-2026-07-31.txt | 1 | PASSED | 589 | 11 |  |
| HSY-2026-06-28.txt | 1 | PASSED | 678 | 11 |  |
| IBM-2026-09-30.txt | 1 | REJECTED | 812 | 15 | math |
| INTC-2026-06-27.txt | 1 | PASSED | 622 | 16 |  |
| JNJ-2026-09-27.txt | 1 | PASSED | 744 | 10 |  |
| KMB-2026-06-30.txt | 1 | PASSED | 636 | 6 |  |
| KO-2026-07-03.txt | 1 | PASSED | 1097 | 13 |  |
| LLY-2026-06-30.txt | 1 | PASSED | 702 | 8 |  |
| LOW-2026-07-31.txt | 1 | PASSED | 694 | 10 |  |
| MCD-2026-06-30.txt | 1 | PASSED | 554 | 8 |  |
| MDLZ-2026-06-30.txt | 1 | PASSED | 710 | 9 |  |
| MDT-2026-07-31.txt | 1 | PASSED | 528 | 9 |  |
| META-2026-09-30.txt | 1 | PASSED | 444 | 8 |  |
| MO-2026-06-30.txt | 1 | PASSED | 631 | 22 |  |
| MRK-2026-06-30.txt | 1 | PASSED | 729 | 6 |  |
| MSFT-2026-03-31.txt | 1 | PASSED | 698 | 11 |  |
| NKE-2026-08-31.txt | 1 | PASSED | 650 | 9 |  |
| NVDA-2026-07-26.txt | 1 | PASSED | 634 | 13 |  |
| ORCL-2026-08-31.txt | 1 | PASSED | 666 | 13 |  |
| PEP-2026-09-05.txt | 1 | PASSED | 548 | 7 |  |
| PFE-2026-06-28.txt | 1 | PASSED | 648 | 11 |  |
| PG-2026-09-30.txt | 1 | PASSED | 538 | 10 |  |
| PM-2026-09-30.txt | 1 | PASSED | 874 | 17 |  |
| QCOM-2026-06-28.txt | 1 | PASSED | 537 | 13 |  |
| ROST-2026-08-01.txt | 1 | PASSED | 593 | 14 |  |
| SBUX-2026-06-28.txt | 1 | PASSED | 757 | 7 |  |
| SYK-2026-06-30.txt | 1 | PASSED | 1069 | 9 |  |
| TGT-2026-08-01.txt | 1 | PASSED | 490 | 8 |  |
| TJX-2026-08-01.txt | 1 | PASSED | 523 | 8 |  |
| TMO-2026-06-27.txt | 1 | REJECTED | 1082 | 11 | math |
| TSLA-2026-09-30.txt | 1 | PASSED | 626 | 10 |  |
| TXN-2026-09-30.txt | 1 | PASSED | 999 | 11 |  |
| WMT-2026-07-31.txt | 1 | PASSED | 760 | 9 |  |
| YUM-2026-06-30.txt | 1 | PASSED | 445 | 10 |  |
| ZTS-2026-06-30.txt | 1 | PASSED | 544 | 10 |  |

## Every violation and error

- `IBM-2026-09-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 31190 but total_current_assets is 31192 (tolerance 1)
- `TMO-2026-06-27.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 15071 but total_current_liabilities is 15069 (tolerance 1)
