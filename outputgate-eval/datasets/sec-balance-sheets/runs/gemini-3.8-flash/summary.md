# Eval run: gemini-3.8-flash (Gemini API, temperature 0, response_format: balance-sheet output schema)

- When: 2026-10-26T11:19:28.468941+05:30
- Documents: 60 from `/Users/raviprakameena/Desktop/outputgate/outputgate-eval/datasets/sec-balance-sheets/documents`
- Outcomes: PASSED: 57 · REJECTED: 3 · NO_TEXT: 0 · ERROR: 0
- Output tokens: 39500 · model time: 1455 s

## Rejections by gate (documents where the gate failed)

| gate | documents |
|---|---|
| math | 3 |

## Every document

| file | pages | outcome | output tokens | seconds | failing gates |
|---|---|---|---|---|---|
| AAPL-2026-06-27.txt | 1 | PASSED | 712 | 155 |  |
| ABBV-2026-06-30.txt | 1 | PASSED | 529 | 578 |  |
| ABT-2026-06-30.txt | 1 | PASSED | 721 | 8 |  |
| ADBE-2026-08-28.txt | 1 | PASSED | 654 | 9 |  |
| AMD-2026-06-27.txt | 1 | PASSED | 609 | 8 |  |
| AMGN-2026-06-30.txt | 1 | PASSED | 530 | 7 |  |
| AMZN-2026-06-30.txt | 1 | PASSED | 554 | 7 |  |
| AVGO-2026-08-02.txt | 1 | PASSED | 573 | 9 |  |
| BBY-2026-08-01.txt | 1 | REJECTED | 591 | 150 | math |
| BDX-2026-06-30.txt | 1 | PASSED | 627 | 8 |  |
| BMY-2026-06-30.txt | 1 | PASSED | 566 | 8 |  |
| CL-2026-06-30.txt | 1 | PASSED | 563 | 9 |  |
| CMG-2026-06-30.txt | 1 | PASSED | 787 | 10 |  |
| COST-2026-05-10.txt | 1 | PASSED | 657 | 5 |  |
| CRM-2026-07-31.txt | 1 | PASSED | 617 | 8 |  |
| CSCO-2026-04-25.txt | 1 | PASSED | 768 | 10 |  |
| DELL-2026-07-31.txt | 1 | PASSED | 977 | 12 |  |
| DG-2026-07-31.txt | 1 | PASSED | 686 | 8 |  |
| DHR-2026-09-25.txt | 1 | PASSED | 549 | 10 |  |
| DLTR-2026-08-01.txt | 1 | PASSED | 445 | 10 |  |
| DRI-2026-08-30.txt | 1 | PASSED | 724 | 11 |  |
| GILD-2026-06-30.txt | 1 | PASSED | 481 | 9 |  |
| GIS-2026-08-30.txt | 1 | PASSED | 646 | 8 |  |
| GOOGL-2026-06-30.txt | 1 | PASSED | 639 | 11 |  |
| HD-2026-08-02.txt | 1 | PASSED | 783 | 11 |  |
| HPQ-2026-07-31.txt | 1 | PASSED | 568 | 8 |  |
| HSY-2026-06-28.txt | 1 | PASSED | 678 | 11 |  |
| IBM-2026-09-30.txt | 1 | REJECTED | 1014 | 12 | math |
| INTC-2026-06-27.txt | 1 | PASSED | 622 | 9 |  |
| JNJ-2026-09-27.txt | 1 | PASSED | 744 | 13 |  |
| KMB-2026-06-30.txt | 1 | PASSED | 636 | 15 |  |
| KO-2026-07-03.txt | 1 | PASSED | 724 | 13 |  |
| LLY-2026-06-30.txt | 1 | PASSED | 702 | 11 |  |
| LOW-2026-07-31.txt | 1 | PASSED | 694 | 7 |  |
| MCD-2026-06-30.txt | 1 | PASSED | 690 | 8 |  |
| MDLZ-2026-06-30.txt | 1 | PASSED | 710 | 7 |  |
| MDT-2026-07-31.txt | 1 | PASSED | 663 | 9 |  |
| META-2026-09-30.txt | 1 | PASSED | 549 | 9 |  |
| MO-2026-06-30.txt | 1 | PASSED | 497 | 9 |  |
| MRK-2026-06-30.txt | 1 | PASSED | 729 | 10 |  |
| MSFT-2026-03-31.txt | 1 | PASSED | 698 | 11 |  |
| NKE-2026-08-31.txt | 1 | PASSED | 650 | 13 |  |
| NVDA-2026-07-26.txt | 1 | PASSED | 634 | 9 |  |
| ORCL-2026-08-31.txt | 1 | PASSED | 666 | 9 |  |
| PEP-2026-09-05.txt | 1 | PASSED | 548 | 8 |  |
| PFE-2026-06-28.txt | 1 | PASSED | 803 | 10 |  |
| PG-2026-09-30.txt | 1 | PASSED | 538 | 9 |  |
| PM-2026-09-30.txt | 1 | PASSED | 874 | 13 |  |
| QCOM-2026-06-28.txt | 1 | PASSED | 682 | 9 |  |
| ROST-2026-08-01.txt | 1 | PASSED | 728 | 11 |  |
| SBUX-2026-06-28.txt | 1 | PASSED | 757 | 10 |  |
| SYK-2026-06-30.txt | 1 | PASSED | 696 | 10 |  |
| TGT-2026-08-01.txt | 1 | PASSED | 490 | 7 |  |
| TJX-2026-08-01.txt | 1 | PASSED | 658 | 9 |  |
| TMO-2026-06-27.txt | 1 | REJECTED | 709 | 8 | math |
| TSLA-2026-09-30.txt | 1 | PASSED | 500 | 10 |  |
| TXN-2026-09-30.txt | 1 | PASSED | 651 | 11 |  |
| WMT-2026-07-31.txt | 1 | PASSED | 760 | 12 |  |
| YUM-2026-06-30.txt | 1 | PASSED | 560 | 7 |  |
| ZTS-2026-06-30.txt | 1 | PASSED | 690 | 11 |  |

## Every violation and error

- `BBY-2026-08-01.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 7808 but total_current_liabilities is 8936 (tolerance 1)
- `IBM-2026-09-30.txt` [math] total_current_assets: sum of current_assets[*].amount is 31190 but total_current_assets is 31192 (tolerance 1)
- `TMO-2026-06-27.txt` [math] total_current_liabilities: sum of current_liabilities[*].amount is 15071 but total_current_liabilities is 15069 (tolerance 1)
